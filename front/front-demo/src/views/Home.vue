<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPosts, getTags, getAbout, getPopularPosts, getRecentComments } from '@/api'
import { useSiteStore } from '@/store/site'
import PostCard from '@/components/PostCard.vue'
// hero 背景图改由 <style> 里的 background-image 引用（这样暗色模式能覆盖），
// 所以这里不再需要 import heroImg。
import { useRouter } from 'vue-router'

const router = useRouter()
const siteStore = useSiteStore()
const posts = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

// 排序：latest = 按发布时间，views = 按阅读量（后端 orderBy 参数）
const sort = ref('latest')

// ===== 侧边栏数据 =====
const about = ref('')
const popular = ref([])
const tags = ref([])
const recentComments = ref([])
const sidebarLoading = ref(false)

// 每页文章数读取「站点设置 → 每页文章数」，未配置时兜底 10
const pageSize = computed(() => {
  const n = Number(siteStore.info.pageSize)
  return Number.isFinite(n) && n > 0 ? n : 10
})

onMounted(async () => {
  // 先等站点设置加载完，保证分页大小用的是配置值
  await siteStore.load().catch(() => {})
  load()
  loadSidebar()
})

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (sort.value === 'views') params.orderBy = 'views'
    const data = await getPosts(params)
    posts.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 侧边栏四个卡片并发拉取，任一失败不影响其它 */
async function loadSidebar() {
  sidebarLoading.value = true
  const safe = (p, fallback) => p.catch(() => fallback)
  try {
    const [a, p, t, c] = await Promise.all([
      safe(getAbout(), { content: '' }),
      safe(getPopularPosts(5), []),
      safe(getTags(), []),
      safe(getRecentComments(5), [])
    ])
    about.value = a?.content || ''
    popular.value = p || []
    tags.value = (t || []).slice(0, 18)
    recentComments.value = c || []
  } finally {
    sidebarLoading.value = false
  }
}

function changeSort(s) {
  if (sort.value === s) return
  sort.value = s
  page.value = 1
  load()
}

function changePage(p) {
  page.value = p
  load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goPost(id) {
  router.push(`/posts/${id}`)
}

function goTag(name) {
  router.push({ path: '/tags', query: { name } })
}

/** 标签云：文章越多字号越大 */
function tagStyle(tag) {
  const n = Number(tag.postCount) || 0
  const size = n >= 5 ? 15 : n >= 3 ? 14 : n >= 1 ? 13 : 12
  return { fontSize: size + 'px' }
}

function formatDate(t) {
  return t ? String(t).replace('T', ' ').substring(5, 16) : ''
}

function plain(text) {
  if (!text) return ''
  const s = String(text).replace(/[#*`>\-\[\]]/g, '').trim()
  return s.length > 40 ? s.substring(0, 40) + '…' : s
}
</script>

<template>
  <div>
    <!-- Hero 横幅 -->
    <div class="hero">
      <div class="hero-content">
        <h1 class="hero-title">{{ siteStore.siteName }}</h1>
        <p class="hero-subtitle">{{ siteStore.description || siteStore.slogan }}</p>
      </div>
      <div class="hero-decor">
        <span>🌸</span><span>⭐</span><span>🎀</span><span>✨</span><span>🌙</span>
      </div>
    </div>

    <div class="home-layout">
      <!-- 主区：文章列表 -->
      <div class="home-main">
        <div class="list-head">
          <h2 class="list-title">最新文章</h2>
          <div class="sort-tabs">
            <button class="sort-btn" :class="{ on: sort === 'latest' }" @click="changeSort('latest')">最新</button>
            <button class="sort-btn" :class="{ on: sort === 'views' }" @click="changeSort('views')">最热</button>
          </div>
        </div>

        <div v-loading="loading">
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
          <p v-if="!loading && !posts.length" class="empty">还没有文章呢～</p>
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

      <!-- 侧边栏 -->
      <aside class="home-side" v-loading="sidebarLoading">
        <!-- 个人简介 -->
        <div class="side-card">
          <h3 class="side-title">👋 关于我</h3>
          <p class="about-text">{{ plain(about) || '这位博主还没有填写简介～' }}</p>
          <router-link to="/about" class="side-more">查看详情 →</router-link>
        </div>

        <!-- 热门文章 -->
        <div class="side-card">
          <h3 class="side-title">🔥 热门文章</h3>
          <ol v-if="popular.length" class="hot-list">
            <li v-for="(p, i) in popular" :key="p.id" @click="goPost(p.id)">
              <span class="hot-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="hot-title">{{ p.title }}</span>
              <span class="hot-views">{{ p.views }}</span>
            </li>
          </ol>
          <p v-else class="side-empty">暂无数据</p>
        </div>

        <!-- 标签云 -->
        <div class="side-card">
          <h3 class="side-title">🏷️ 标签云</h3>
          <div v-if="tags.length" class="tag-cloud">
            <span
              v-for="t in tags"
              :key="t.id"
              class="cloud-tag"
              :style="tagStyle(t)"
              @click="goTag(t.name)"
            >{{ t.name }}<em v-if="t.postCount"> {{ t.postCount }}</em></span>
          </div>
          <p v-else class="side-empty">暂无标签</p>
        </div>

        <!-- 最新评论 -->
        <div class="side-card">
          <h3 class="side-title">💬 最新评论</h3>
          <ul v-if="recentComments.length" class="comment-list">
            <li v-for="c in recentComments" :key="c.id" @click="goPost(c.postId)">
              <span class="comment-user">{{ c.nickname || '匿名' }}</span>
              <span class="comment-text">{{ plain(c.content) }}</span>
              <span class="comment-post">《{{ c.postTitle }}》</span>
            </li>
          </ul>
          <p v-else class="side-empty">还没有评论</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  border-radius: 18px;
  padding: 52px 32px;
  margin-bottom: 24px;
  background-size: cover;
  background-position: center;
  overflow: hidden;
  /* 左侧压一层白遮罩：文字都在左边，这样对比度才够；
     右侧只留淡遮罩，樱花图看得见。暗色模式的覆盖在 anime.css 里。 */
  background-image:
    linear-gradient(100deg, rgba(255, 255, 255, 0.94) 0%, rgba(255, 255, 255, 0.78) 40%, rgba(255, 255, 255, 0.16) 100%),
    url('../assets/anime-hero.webp');
}

.hero-content {
  position: relative;
  z-index: 2;
}

.hero-title {
  margin: 0 0 10px;
  font-size: 32px;
  /* 原来用 #e04e82 配浅粉底，对比度偏低；改用更深的玫红 */
  color: #c2266b;
  font-weight: 800;
  letter-spacing: 0.5px;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.6);
}

.hero-subtitle {
  margin: 0;
  color: #55556a;
  font-size: 15px;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.5);
}

.hero-decor {
  position: absolute;
  right: 30px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  gap: 14px;
  font-size: 28px;
  opacity: 0.5;
}

/* ===== 双栏布局 ===== */
.home-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.home-main {
  flex: 1;
  min-width: 0;
}

.home-side {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.list-title {
  margin: 0;
  font-size: 18px;
  color: var(--text-strong);
}

.sort-tabs {
  display: flex;
  gap: 6px;
}

.sort-btn {
  padding: 5px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-body);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.sort-btn:hover {
  color: #e04e82;
  border-color: #ffb3cd;
}

.sort-btn.on {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  border-color: transparent;
  color: #fff;
  font-weight: 600;
}

/* ===== 侧边栏卡片 ===== */
.side-card {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  padding: 16px;
}

.side-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong);
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--border-soft);
}

.about-text {
  margin: 0 0 8px;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.7;
}

.side-more {
  color: #e04e82;
  font-size: 12px;
}

.side-empty {
  margin: 0;
  color: var(--text-faint);
  font-size: 12px;
  text-align: center;
  padding: 10px 0;
}

.hot-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.hot-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  cursor: pointer;
  transition: color 0.2s;
}

.hot-list li:hover .hot-title {
  color: #e04e82;
}

.hot-rank {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: 5px;
  background: #f0e8ee;
  color: var(--text-muted);
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.hot-rank.top {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
}

.hot-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #5a5a6a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-views {
  font-size: 11px;
  color: var(--text-faint);
  flex-shrink: 0;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cloud-tag {
  padding: 3px 10px;
  background: var(--surface-pink);
  color: #e04e82;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
  line-height: 1.6;
}

.cloud-tag:hover {
  background: #ffd6e4;
}

.cloud-tag em {
  font-style: normal;
  color: #c98aa8;
  font-size: 11px;
}

.comment-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.comment-list li {
  padding: 7px 0;
  border-bottom: 1px dashed #f7f0f4;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.6;
}

.comment-list li:last-child {
  border-bottom: none;
}

.comment-list li:hover .comment-text {
  color: #e04e82;
}

.comment-user {
  color: #e04e82;
  font-weight: 600;
  margin-right: 4px;
}

.comment-text {
  color: var(--text-body);
}

.comment-post {
  display: block;
  color: var(--text-faint);
  margin-top: 2px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px;
}

@media (max-width: 900px) {
  .home-side {
    display: none;
  }
}
</style>
