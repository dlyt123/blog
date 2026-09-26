<script setup>
import { ref, onMounted } from 'vue'
import { getCategories, getCategoryPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'
import SkeletonPostList from '@/components/SkeletonPostList.vue'

const categories = ref([])
const activeId = ref(null)
const posts = ref([])
const loading = ref(false)

onMounted(async () => {
  categories.value = await getCategories()
})

async function selectCategory(c) {
  activeId.value = c.id
  loading.value = true
  try {
    const data = await getCategoryPosts(c.id, { page: 1, pageSize: 20 })
    posts.value = data.list
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">📁 文章分类</h2>

    <div class="category-grid">
      <div
        v-for="c in categories"
        :key="c.id"
        class="category-card anime-card"
        :class="{ active: activeId === c.id }"
        @click="selectCategory(c)"
      >
        <span class="category-name">{{ c.name }}</span>
        <span class="category-count">{{ c.postCount }} 篇</span>
      </div>
    </div>

    <div v-if="activeId" class="post-list" :aria-busy="loading">
      <h3 class="anime-title" style="margin-bottom: 14px">该分类下的文章</h3>
      <SkeletonPostList v-if="loading && !posts.length" :count="3" />
      <template v-else>
        <PostCard v-for="p in posts" :key="p.id" :post="p" />
        <p v-if="!posts.length" class="anime-empty">📭 这个分类下还没有文章～</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 var(--space-5);
  font-size: var(--text-2xl);
  color: var(--text-strong);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.category-card {
  text-align: center;
  cursor: pointer;
  padding: var(--space-6) var(--space-4);
}

/* 选中态：粉色→蓝色的柔和渐变。
   两个色都走令牌，暗色模式下会自动翻成深粉 → 深蓝。 */
.category-card.active {
  border-color: var(--brand-400);
  background: linear-gradient(135deg, var(--surface-pink), var(--blue-100));
}

.category-name {
  display: block;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-strong);
}

.category-count {
  display: block;
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
}
</style>
