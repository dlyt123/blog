<script setup>
import { ref, onMounted } from 'vue'
import { getMyFavorites } from '@/api'
import PostCard from '@/components/PostCard.vue'
import SkeletonPostList from '@/components/SkeletonPostList.vue'

const posts = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await getMyFavorites({ page: page.value, pageSize: pageSize.value })
    posts.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  page.value = p
  load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">⭐ 我的收藏</h2>
    <p class="subtitle">这里是你收藏过的文章。取消收藏后会自动从这里消失。</p>

    <div :aria-busy="loading" v-loading="loading && posts.length > 0">
      <SkeletonPostList v-if="loading && !posts.length" :count="3" />
      <template v-else>
        <PostCard v-for="post in posts" :key="post.id" :post="post" />
        <div v-if="!posts.length" class="anime-empty">
          <p class="empty-icon">⭐</p>
          <p class="empty-text">还没有收藏任何文章</p>
          <p class="empty-hint">在文章详情页点「收藏」，之后就能在这里找到它</p>
        </div>
      </template>
    </div>

    <div v-if="total > pageSize" class="pagination">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-2xl);
  color: var(--text-strong);
}

.subtitle {
  margin: 0 0 var(--space-5);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

/* 空状态的容器与间距来自全局 .anime-empty，这里只描述内部三行 */
.empty-icon {
  font-size: 42px;
  line-height: 1;
}

.empty-text {
  margin: 0;
  color: var(--text-body);
  font-size: var(--text-md);
  font-weight: 500;
}

.empty-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--space-5);
}
</style>
