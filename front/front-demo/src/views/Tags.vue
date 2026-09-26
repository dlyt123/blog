<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTags, getPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'
import SkeletonPostList from '@/components/SkeletonPostList.vue'

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
      <!-- 用 button 而不是 span：这几个标签是筛选控件，
           button 自带键盘可达（Tab / Enter / Space）和正确的语义 -->
      <button
        v-for="tag in tags"
        :key="tag.id"
        type="button"
        class="anime-tag tag-item"
        :class="{ active: activeTag === tag.name }"
        @click="selectTag(tag)"
      >{{ tag.name }} ({{ tag.postCount }})</button>
      <p v-if="!tags.length" class="anime-empty">🏷️ 还没有标签～</p>
    </div>

    <div v-if="activeTag" :aria-busy="loading" v-loading="loading && posts.length > 0" class="post-list">
      <h3 class="anime-title" style="margin-bottom: 14px">
        标签「{{ activeTag }}」下的文章（{{ posts.length }} 篇）
      </h3>
      <SkeletonPostList v-if="loading && !posts.length" :count="2" />
      <template v-else>
        <PostCard v-for="p in posts" :key="p.id" :post="p" />
        <p v-if="!posts.length" class="anime-empty">📭 这个标签下还没有文章～</p>
      </template>
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
  font-size: var(--text-md);
  padding: 6px var(--space-4);
  margin: 0 10px 10px 0;
  cursor: pointer;
  /* 作为 <button> 渲染时要去掉浏览器默认的边框和字体 */
  border: none;
  font-family: inherit;
  line-height: 1.4;
  transition: transform var(--dur-fast) var(--ease-out),
              box-shadow var(--dur-fast) var(--ease-out),
              filter var(--dur-fast) var(--ease-out);
}

.tag-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  filter: brightness(1.04);
}

.tag-item.active {
  background: linear-gradient(135deg, var(--brand-500), var(--brand-400));
  color: var(--text-on-brand);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42),
              var(--shadow-brand);
}
</style>
