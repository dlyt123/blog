<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPosts, getTags, getAbout, getPopularPosts, getRecentComments } from '@/api'
import { useSiteStore } from '@/store/site'
import PostCard from '@/components/PostCard.vue'
import SkeletonPostList from '@/components/SkeletonPostList.vue'
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

        <!-- 首屏用骨架屏；翻页时列表已有内容，才交给 v-loading 的遮罩 -->
        <div :aria-busy="loading" v-loading="loading && posts.length > 0">
          <SkeletonPostList v-if="loading && !posts.length" :count="3" />
          <template v-else>
            <PostCard v-for="post in posts" :key="post.id" :post="post" />
            <p v-if="!posts.length" class="anime-empty">📝 还没有文章呢～</p>
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
            <li
              v-for="(p, i) in popular"
              :key="p.id"
              tabindex="0"
              @click="goPost(p.id)"
              @keydown.enter="goPost(p.id)"
            >
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
            <!-- 用 button：标签是筛选控件，键盘 Tab/Enter 应当可达 -->
            <button
              v-for="t in tags"
              :key="t.id"
              type="button"
              class="cloud-tag"
              :style="tagStyle(t)"
              @click="goTag(t.name)"
            >{{ t.name }}<em v-if="t.postCount"> {{ t.postCount }}</em></button>
          </div>
          <p v-else class="side-empty">暂无标签</p>
        </div>

        <!-- 最新评论 -->
        <div class="side-card">
          <h3 class="side-title">💬 最新评论</h3>
          <ul v-if="recentComments.length" class="comment-list">
            <li
              v-for="c in recentComments"
              :key="c.id"
              tabindex="0"
              @click="goPost(c.postId)"
              @keydown.enter="goPost(c.postId)"
            >
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
  border-radius: var(--radius-xl);
  padding: var(--space-12) var(--space-8);
  margin-bottom: var(--space-6);
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
  font-size: var(--text-4xl);
  /* 700 级才是白底上 AA 达标的品牌文字色（这里底是浅粉渐变，同理） */
  color: var(--brand-700);
  font-weight: 800;
  letter-spacing: 0.01em;
  line-height: 1.25;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.6);
}

.hero-subtitle {
  margin: 0;
  color: var(--text-body);
  font-size: var(--text-md);
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
  pointer-events: none; /* 纯装饰，别挡住文字或截走点击 */
}

/* ===== 双栏布局 ===== */
.home-layout {
  display: flex;
  gap: var(--space-5);
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
  gap: var(--space-4);
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.list-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-strong);
}

.sort-tabs {
  display: flex;
  gap: 6px;
}

.sort-btn {
  padding: 5px 14px;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-full);
  background: var(--surface);
  color: var(--text-body);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out),
              border-color var(--dur-fast) var(--ease-out),
              background-color var(--dur-fast) var(--ease-out),
              box-shadow var(--dur-base) var(--ease-out);
}

.sort-btn:hover {
  color: var(--brand-700);
  border-color: var(--border-brand);
}

/* 选中态：跟主按钮用同一套"渐变 + 内高光 + 辉光"的语言 */
.sort-btn.on {
  background: linear-gradient(135deg, var(--brand-500), var(--brand-400));
  border-color: transparent;
  color: var(--text-on-brand);
  font-weight: 600;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.4),
              0 4px 12px -6px rgba(255, 107, 157, 0.8);
}

/* ===== 侧边栏卡片 ===== */
.side-card {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
}

.side-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-strong);
  padding-bottom: var(--space-2);
  border-bottom: 1px dashed var(--border-soft);
}

.about-text {
  margin: 0 0 var(--space-2);
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.side-more {
  color: var(--brand-700);
  font-size: var(--text-xs);
}

.side-empty {
  margin: 0;
  color: var(--text-faint);
  font-size: var(--text-xs);
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
  gap: var(--space-2);
  padding: 6px 0;
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out);
}

.hot-list li:hover .hot-title {
  color: var(--brand-700);
}

.hot-rank {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.hot-rank.top {
  background: linear-gradient(135deg, var(--brand-500), var(--brand-400));
  color: var(--text-on-brand);
  box-shadow: 0 2px 6px -2px rgba(255, 107, 157, 0.9);
}

.hot-title {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  color: var(--text-body);
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
  color: var(--brand-700);
  border-radius: var(--radius-full);
  /* 作为 <button> 渲染：清掉默认边框与字体，避免尺寸和别的页面不一致 */
  border: none;
  font-family: inherit;
  cursor: pointer;
  line-height: 1.6;
  transition: background-color var(--dur-fast) var(--ease-out),
              transform var(--dur-fast) var(--ease-out);
}

.cloud-tag:hover {
  transform: translateY(-1px);
  background: var(--brand-200);
}

.cloud-tag em {
  font-style: normal;
  color: var(--text-muted);
  font-size: 11px;
}

.comment-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.comment-list li {
  padding: 7px 0;
  border-bottom: 1px dashed var(--border-softer);
  cursor: pointer;
  font-size: var(--text-xs);
  line-height: 1.6;
}

.comment-list li:last-child {
  border-bottom: none;
}

.comment-list li:hover .comment-text {
  color: var(--brand-700);
}

.comment-user {
  color: var(--brand-700);
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
  margin-top: var(--space-5);
}

@media (max-width: 900px) {
  .home-side {
    display: none;
  }
}
</style>
