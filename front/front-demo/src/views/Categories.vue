<script setup>
import { ref, onMounted } from 'vue'
import { getCategories, getCategoryPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'

const categories = ref([])
const activeId = ref(null)
const posts = ref([])

onMounted(async () => {
  categories.value = await getCategories()
})

async function selectCategory(c) {
  activeId.value = c.id
  const data = await getCategoryPosts(c.id, { page: 1, pageSize: 20 })
  posts.value = data.list
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

    <div v-if="activeId" class="post-list">
      <h3 class="anime-title" style="margin-bottom: 14px">该分类下的文章</h3>
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
      <p v-if="!posts.length" class="empty">这个分类下还没有文章～</p>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 24px;
  color: var(--text-strong);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}

.category-card {
  text-align: center;
  cursor: pointer;
  padding: 24px 16px;
}

.category-card.active {
  border-color: #ff6b9d;
  background: linear-gradient(135deg, #fff0f5, #e8f4fb);
}

.category-name {
  display: block;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-strong);
}

.category-count {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 30px;
}
</style>
