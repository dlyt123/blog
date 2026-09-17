<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { searchPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const keyword = ref(route.query.keyword || '')
const posts = ref([])
const searched = ref(false)

onMounted(() => {
  if (keyword.value) {
    doSearch()
  }
})

async function doSearch() {
  if (!keyword.value.trim()) return
  const data = await searchPosts({ keyword: keyword.value, page: 1, pageSize: 20 })
  posts.value = data.list
  searched.value = true
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

    <div v-if="searched">
      <h3 class="anime-title" style="margin-bottom: 14px">搜索结果</h3>
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
      <p v-if="!posts.length" class="empty">没有找到相关文章～</p>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 24px;
  color: var(--text-strong);
}

.search-bar {
  margin-bottom: 24px;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px;
}
</style>
