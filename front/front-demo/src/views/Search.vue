<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { searchPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'
import SkeletonPostList from '@/components/SkeletonPostList.vue'

const route = useRoute()
const keyword = ref(route.query.keyword || '')
const posts = ref([])
const searched = ref(false)
const loading = ref(false)

onMounted(() => {
  if (keyword.value) {
    doSearch()
  }
})

async function doSearch() {
  if (!keyword.value.trim()) return
  loading.value = true
  try {
    const data = await searchPosts({ keyword: keyword.value, page: 1, pageSize: 20 })
    posts.value = data.list
    searched.value = true
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">🔍 搜索</h2>

    <div class="search-bar anime-card">
      <el-input
        v-model="keyword"
        placeholder="输入关键词搜索文章..."
        size="large"
        @keyup.enter="doSearch"
      >
        <template #append>
          <el-button @click="doSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <div v-if="searched || loading" :aria-busy="loading">
      <h3 class="anime-title" style="margin-bottom: 14px">搜索结果</h3>
      <SkeletonPostList v-if="loading && !posts.length" :count="2" />
      <template v-else>
        <PostCard v-for="p in posts" :key="p.id" :post="p" />
        <p v-if="!posts.length" class="anime-empty">🔍 没有找到相关文章～</p>
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

.search-bar {
  margin-bottom: var(--space-6);
}
</style>
