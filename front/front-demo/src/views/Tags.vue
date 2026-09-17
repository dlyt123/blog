<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTags, getPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const tags = ref([])
const posts = ref([])
const activeTag = ref('')
const loading = ref(false)

onMounted(async () => {
  tags.value = await getTags()
  // 支持从文章卡片跳转过来：/tags?name=xxx 自动选中该标签
  const name = route.query.name
  if (name) {
    const hit = tags.value.find((t) => t.name === name)
    if (hit) await selectTag(hit)
  }
})

/** 按标签 ID 精确筛选（原来用 keyword 搜索会漏掉标题/摘要里没有标签名的文章） */
async function selectTag(tag) {
  activeTag.value = tag.name
  loading.value = true
  try {
    const data = await getPosts({ tagId: tag.id, page: 1, pageSize: 50 })
    posts.value = data.list || []
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">🏷️ 标签云</h2>

    <div class="tag-cloud anime-card">
      <span
        v-for="tag in tags"
        :key="tag.id"
        class="anime-tag tag-item"
        :class="{ active: activeTag === tag.name }"
        @click="selectTag(tag)"
      >{{ tag.name }} ({{ tag.postCount }})</span>
      <p v-if="!tags.length" class="empty">还没有标签～</p>
    </div>

    <div v-if="activeTag" v-loading="loading" class="post-list">
      <h3 class="anime-title" style="margin-bottom: 14px">
        标签「{{ activeTag }}」下的文章（{{ posts.length }} 篇）
      </h3>
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
      <p v-if="!loading && !posts.length" class="empty">这个标签下还没有文章～</p>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 24px;
  color: var(--text-strong);
}

.tag-cloud {
  margin-bottom: 24px;
  padding: 24px;
}

.tag-item {
  font-size: 15px;
  padding: 6px 16px;
  margin: 0 10px 10px 0;
  cursor: pointer;
  transition: all 0.2s;
}

.tag-item:hover {
  transform: translateY(-2px);
}

.tag-item.active {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
}

.empty {
  text-align: center;
  color: var(--text-muted);
}
</style>
