<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { renderMarkdown } from '@/utils/markdown'
import {
  getPost, likePost, unlikePost, getLikeStatus,
  favoritePost, unfavoritePost, getFavoriteStatus, getRelatedPosts, submitReport
} from '@/api'
import { useUserStore } from '@/store/user'
import { useSiteStore } from '@/store/site'
import CommentSection from '@/components/CommentSection.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

const post = ref(null)
// 正文渲染：统一走 renderMarkdown（markdown-it + DOMPurify 净化，防存储型 XSS）
const html = ref('')
const liked = ref(false)      // 当前用户是否已点赞（从后端读取，刷新后仍保留）
const liking = ref(false)
const favorited = ref(false)  // 当前用户是否已收藏
const favoriting = ref(false)
const loading = ref(false)

// ===== 相关推荐 + 版权声明 =====
const related = ref([])

async function loadRelated(id) {
  try {
    related.value = (await getRelatedPosts(id, 5)) || []
  } catch (e) {
    related.value = []
  }
}

/** 复制原文链接 */
async function copyLink() {
  const url = window.location.origin + route.fullPath
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('原文链接已复制')
  } catch (e) {
    ElMessage.info('复制失败，请手动复制地址栏链接')
  }
}

// ===== 阅读时长估算（中文约 500 字/分钟）=====
const readingMinutes = computed(() => {
  const text = (post.value?.content || '').replace(/[#*`>\[\]!()~\-|>\s]/g, '')
  if (!text.length) return 0
  return Math.max(1, Math.round(text.length / 500))
})

// ===== 图片灯箱 =====
const lightbox = ref('')
function onContentClick(e) {
  const img = e.target && e.target.closest && e.target.closest('img')
  if (img && img.src) lightbox.value = img.src
}
function closeLightbox() {
  lightbox.value = ''
}

// ===== 结构化数据（JSON-LD，利于搜索引擎收录）=====
function injectJsonLd() {
  document.querySelectorAll('script[data-article-ld]').forEach((el) => el.remove())
  const p = post.value
  if (!p) return
  const data = {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: p.title,
    datePublished: p.publishTime || p.createTime,
    dateModified: p.updateTime || p.publishTime || p.createTime,
    author: { '@type': 'Person', name: p.authorName || '匿名' },
    description: p.summary || ''
  }
  const script = document.createElement('script')
  script.type = 'application/ld+json'
  script.setAttribute('data-article-ld', 'true')
  script.textContent = JSON.stringify(data)
  document.head.appendChild(script)
}

// ===== 内容举报 =====
async function reportPost() {  if (!userStore.isLogin) {
    ElMessage.warning('登录后才能举报')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请填写举报理由（选填）', '举报这篇文章', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：广告、抄袭、违法内容'
    })
    await submitReport({ targetType: 'post', targetId: Number(route.params.id), reason: value || '' })
    ElMessage.success('举报已提交，感谢你的反馈')
  } catch (e) {
    // 用户取消
  }
}

// ===== 文章目录（TOC）=====
const contentRef = ref(null)
const toc = ref([])           // [{ id, text, level }]
const activeId = ref('')
let observer = null

function buildToc() {
  nextTick(() => {
    const el = contentRef.value
    toc.value = []
    if (!el) return
    const headings = el.querySelectorAll('h1, h2, h3')
    if (!headings.length) return
    const items = []
    headings.forEach((h, i) => {
      const id = `heading-${i}`
      h.id = id
      items.push({ id, text: h.textContent.trim(), level: Number(h.tagName.charAt(1)) })
    })
    toc.value = items
    setupObserver()
  })
}

function setupObserver() {
  if (observer) observer.disconnect()
  const el = contentRef.value
  if (!el || typeof IntersectionObserver === 'undefined') return
  const heads = el.querySelectorAll('h1, h2, h3')
  observer = new IntersectionObserver(
    (entries) => {
      // 取当前视口里最靠上的标题作为高亮项
      const visible = entries.filter((e) => e.isIntersecting)
      if (visible.length) {
        activeId.value = visible[0].target.id
      }
    },
    { rootMargin: '-80px 0px -70% 0px', threshold: 0 }
  )
  heads.forEach((h) => observer.observe(h))
}

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

function scrollToHeading(id) {
  activeId.value = id
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}


// 用 watch 监听路由参数：点「上一篇 / 下一篇」时组件会被复用，
// onMounted 不会再次执行，必须靠 watch 触发重新加载。
watch(
  () => route.params.id,
  async (id) => {
    if (id) await load(id)
  },
  { immediate: true }
)

/**
 * 摘掉正文开头的重复标题。
 * 很多作者习惯在正文第一行再写一次「# 标题」，而页面本身已经渲染了文章标题，
 * 结果就是同一个标题上下连续出现两遍，目录里也会多一项。这里做兼容处理：
 * 若正文第一个块级元素是 H1 且文字与文章标题相同，就把它去掉。
 * （html 已经过 DOMPurify 净化，这里用 DOM 解析是安全的）
 */
function stripDuplicateTitle(html, title) {
  if (!title || !html) return html
  const norm = (s) => (s || '').replace(/\s+/g, '').toLowerCase()
  const box = document.createElement('div')
  box.innerHTML = html
  const first = box.firstElementChild
  if (first && first.tagName === 'H1' && norm(first.textContent) === norm(title)) {
    box.removeChild(first)
  }
  return box.innerHTML
}

async function load(id) {
  loading.value = true
  liked.value = false
  favorited.value = false
  try {
    const data = await getPost(id)
    post.value = data
    html.value = stripDuplicateTitle(renderMarkdown(data.content), data.title)
    buildToc()
    loadRelated(id)
    injectJsonLd()
    // 读取当前用户对该文章的点赞 / 收藏状态
    if (userStore.isLogin) {
      try {
        const status = await getLikeStatus(id)
        liked.value = !!status.liked
        if (typeof status.likes === 'number') post.value.likes = status.likes
      } catch (e) {
        liked.value = false
      }
      try {
        const fs = await getFavoriteStatus(id)
        favorited.value = !!fs.favorited
      } catch (e) {
        favorited.value = false
      }
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) {
    post.value = null
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

async function toggleLike() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后再点赞')
    router.push('/login')
    return
  }
  if (liking.value) return
  liking.value = true
  try {
    if (liked.value) {
      const res = await unlikePost(route.params.id)
      liked.value = false
      if (res && typeof res.likes === 'number') post.value.likes = res.likes
      ElMessage.info('已取消点赞')
    } else {
      const res = await likePost(route.params.id)
      liked.value = true
      if (res && typeof res.likes === 'number') post.value.likes = res.likes
      ElMessage.success('点赞成功～')
    }
  } finally {
    liking.value = false
  }
}

async function toggleFavorite() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后再收藏')
    router.push('/login')
    return
  }
  if (favoriting.value) return
  favoriting.value = true
  try {
    if (favorited.value) {
      await unfavoritePost(route.params.id)
      favorited.value = false
      ElMessage.info('已取消收藏')
    } else {
      await favoritePost(route.params.id)
      favorited.value = true
      ElMessage.success('收藏成功～')
    }
  } finally {
    favoriting.value = false
  }
}

function go(id) {
  if (id) router.push(`/posts/${id}`)
}

// 点标签 → 标签页按该标签筛选
function goTag(tag) {
  router.push({ path: '/tags', query: { name: tag } })
}

// 点作者 → 进 TA 的用户主页
function goAuthor() {
  if (post.value?.authorId) {
    router.push(`/users/${post.value.authorId}`)
  }
}
</script>

<template>
  <div v-if="post">
    <div class="detail-layout">
      <div class="detail-main">
        <article class="anime-card post-detail">
          <h1 class="post-title">{{ post.title }}</h1>

          <div class="post-meta">
            <!-- 作者（头像 + 昵称，来自数据库用户表）；点击进入 TA 的主页 -->
            <span class="author" @click="goAuthor">
              <span class="author-avatar">
                <img v-if="post.authorAvatar" :src="post.authorAvatar" :alt="post.authorName" />
                <span v-else class="author-fallback">{{ (post.authorName || '?')[0] }}</span>
              </span>
              <span class="author-name">{{ post.authorName || '匿名' }}</span>
            </span>
            <span v-if="post.categoryName">📁 {{ post.categoryName }}</span>
            <span v-if="post.seriesName" class="series-badge" @click="router.push('/series')">📚 {{ post.seriesName }}</span>
            <span>🕒 {{ formatTime(post.publishTime || post.createTime) }}</span>
            <span v-if="readingMinutes">⏱ 约 {{ readingMinutes }} 分钟读完</span>
            <span>👀 {{ post.views }} 阅读</span>
            <span>💗 {{ post.likes }} 赞</span>
          </div>

          <div v-if="post.tags && post.tags.length" class="tags">
            <span
              v-for="tag in post.tags"
              :key="tag"
              class="anime-tag clickable"
              @click="goTag(tag)"
            >{{ tag }}</span>
          </div>

          <div ref="contentRef" class="markdown-body" v-html="html" @click="onContentClick"></div>

          <div class="post-actions">
            <button
              class="like-btn"
              :class="{ liked: liked }"
              :disabled="liking"
              @click="toggleLike"
            >
              <span class="like-icon">{{ liked ? '💖' : '🤍' }}</span>
              <span>{{ liked ? '已点赞' : '点赞' }}</span>
            </button>
            <button
              class="like-btn fav-btn"
              :class="{ favorited: favorited }"
              :disabled="favoriting"
              @click="toggleFavorite"
            >
              <span class="like-icon">{{ favorited ? '⭐' : '☆' }}</span>
              <span>{{ favorited ? '已收藏' : '收藏' }}</span>
            </button>
            <button class="like-btn report-btn" @click="reportPost">
              <span class="like-icon">🚩</span>
              <span>举报</span>
            </button>
          </div>

          <!-- 版权声明 -->
          <div class="copyright">
            <div class="copyright-head">📄 版权声明</div>
            <p class="copyright-text">
              本文由 <span class="cp-author" @click="goAuthor">{{ post.authorName || '匿名' }}</span>
              于 {{ formatTime(post.publishTime || post.createTime) }} 发布在「{{ siteStore.siteName }}」。
              原文链接：<a class="cp-link" @click="copyLink">点此复制</a>
            </p>
            <p v-if="siteStore.copyright" class="copyright-license">{{ siteStore.copyright }}</p>
          </div>
        </article>

        <div class="prev-next">
          <div v-if="post.prev" class="nav-item anime-card" @click="go(post.prev.id)">
            <span class="label">上一篇</span>
            <span class="nav-title">{{ post.prev.title }}</span>
          </div>
          <div v-else class="nav-item disabled">上一篇没有了</div>
          <div v-if="post.next" class="nav-item anime-card" @click="go(post.next.id)">
            <span class="label">下一篇</span>
            <span class="nav-title">{{ post.next.title }}</span>
          </div>
          <div v-else class="nav-item disabled">下一篇没有了</div>
        </div>

        <!-- 相关推荐 -->
        <div v-if="related.length" class="related anime-card">
          <h3 class="related-title">📚 相关推荐</h3>
          <div class="related-list">
            <div v-for="r in related" :key="r.id" class="related-item" @click="go(r.id)">
              <span class="related-item-title">{{ r.title }}</span>
              <span class="related-item-meta">👀 {{ r.views }} · {{ formatTime(r.publishTime || r.createTime) }}</span>
            </div>
          </div>
        </div>

        <CommentSection :post-id="route.params.id" />
      </div>

      <!-- 文章目录：锚点跳转，滚动时自动高亮当前章节 -->
      <aside v-if="toc.length" class="toc-sidebar">
        <div class="toc-title">📑 目录</div>
        <ul class="toc-list">
          <li
            v-for="item in toc"
            :key="item.id"
            class="toc-item"
            :class="['level-' + item.level, { active: activeId === item.id }]"
            @click="scrollToHeading(item.id)"
          >{{ item.text }}</li>
        </ul>
      </aside>
    </div>

    <!-- 图片灯箱 -->
    <div v-if="lightbox" class="lightbox-overlay" @click="closeLightbox">
      <img :src="lightbox" class="lightbox-img" alt="图片预览" />
      <span class="lightbox-tip">点击任意处关闭</span>
    </div>
  </div>

  <div v-else-if="loading" class="loading-tip">加载中...</div>
</template>

<style scoped>
.post-detail {
  margin-bottom: 16px;
}

.post-title {
  margin: 0 0 14px;
  font-size: 28px;
  color: var(--text-strong);
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.author-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.author-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-fallback {
  color: #fff;
  font-weight: 700;
  font-size: 12px;
}

.author-name {
  color: #e04e82;
  font-weight: 600;
}

.tags {
  margin-bottom: 16px;
}

.anime-tag {
  display: inline-block;
  padding: 4px 10px;
  margin-right: 6px;
  background: var(--surface-pink);
  color: #e04e82;
  border-radius: 999px;
  font-size: 12px;
}

.anime-tag.clickable {
  cursor: pointer;
  transition: all 0.2s;
}

.anime-tag.clickable:hover {
  background: #ffd6e4;
}

.post-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px dashed var(--border-soft);
}

.fav-btn.favorited {
  background: linear-gradient(135deg, #ffe9b8, #ffd98e);
  border-color: #ffd98e;
  color: #fff;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 22px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  background: var(--surface);
  color: #6a6a7a;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.like-btn:hover:not(:disabled) {
  border-color: #ffb3cd;
  color: #e04e82;
  transform: translateY(-1px);
}

.like-btn.liked {
  background: linear-gradient(135deg, #ffd6e4, #ffb3cd);
  border-color: #ffb3cd;
  color: #fff;
}

.like-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.like-icon {
  font-size: 18px;
}

.prev-next {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.nav-item {
  flex: 1;
  cursor: pointer;
  text-align: center;
}

.nav-item .label {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 4px;
}

.nav-item .nav-title {
  color: #e04e82;
}

.nav-item.disabled {
  flex: 1;
  text-align: center;
  color: #d0c0d0;
  padding: 20px;
}

.loading-tip {
  text-align: center;
  color: var(--text-muted);
  padding: 60px;
}

/* ===== 文章目录 ===== */
.detail-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.detail-main {
  flex: 1;
  min-width: 0; /* 防止长代码块撑破布局 */
}

.toc-sidebar {
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 80px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 14px 12px;
}

.toc-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--border-soft);
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.toc-item {
  padding: 6px 10px;
  font-size: 13px;
  color: var(--text-body);
  cursor: pointer;
  border-radius: 6px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: all 0.15s;
}

.toc-item:hover {
  color: #e04e82;
  background: var(--surface-pink);
}

.toc-item.active {
  color: #e04e82;
  background: linear-gradient(90deg, #ffd6e4, transparent);
  font-weight: 600;
}

/* 标题层级缩进 */
.toc-item.level-2 { padding-left: 10px; font-weight: 600; }
.toc-item.level-3 { padding-left: 24px; }

@media (max-width: 900px) {
  .toc-sidebar {
    display: none; /* 窄屏隐藏目录，聚焦正文 */
  }
}

/* ===== 版权声明 ===== */
.copyright {
  margin-top: 24px;
  padding: 16px 18px;
  border: 1px dashed var(--border-soft);
  border-radius: 12px;
  background: #fffafc;
  font-size: 13px;
  color: #8a8a9a;
  line-height: 1.8;
}

.copyright-head {
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: 8px;
}

.copyright-text {
  margin: 0 0 6px;
}

.cp-author {
  color: #e04e82;
  cursor: pointer;
  font-weight: 500;
}

.cp-author:hover {
  text-decoration: underline;
}

.cp-link {
  color: #7b5ea7;
  cursor: pointer;
  text-decoration: underline;
}

.copyright-license {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
}

/* ===== 相关推荐 ===== */
.related {
  margin-bottom: 16px;
  padding: 20px;
}

.related-title {
  margin: 0 0 12px;
  font-size: 16px;
  color: var(--text-strong);
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.related-item:hover {
  background: var(--surface-pink);
}

.related-item-title {
  flex: 1;
  min-width: 0;
  color: #5a5a6a;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-item:hover .related-item-title {
  color: #e04e82;
}

.related-item-meta {
  flex-shrink: 0;
  color: var(--text-faint);
  font-size: 12px;
}

/* ===== 图片灯箱 ===== */
.markdown-body img {
  cursor: zoom-in;
}

.lightbox-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  cursor: zoom-out;
}

.lightbox-img {
  max-width: 92vw;
  max-height: 86vh;
  border-radius: 8px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.6);
}

.lightbox-tip {
  margin-top: 14px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
}
</style>
