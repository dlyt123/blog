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
        <article class="anime-card anime-card--flat post-detail">
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
          <div
            v-if="post.prev"
            class="nav-item anime-card"
            tabindex="0"
            @click="go(post.prev.id)"
            @keydown.enter="go(post.prev.id)"
          >
            <span class="label">上一篇</span>
            <span class="nav-title">{{ post.prev.title }}</span>
          </div>
          <div v-else class="nav-item disabled">上一篇没有了</div>
          <div
            v-if="post.next"
            class="nav-item anime-card"
            tabindex="0"
            @click="go(post.next.id)"
            @keydown.enter="go(post.next.id)"
          >
            <span class="label">下一篇</span>
            <span class="nav-title">{{ post.next.title }}</span>
          </div>
          <div v-else class="nav-item disabled">下一篇没有了</div>
        </div>

        <!-- 相关推荐 -->
        <div v-if="related.length" class="related anime-card anime-card--flat">
          <h3 class="related-title">📚 相关推荐</h3>
          <div class="related-list">
            <div
              v-for="r in related"
              :key="r.id"
              class="related-item"
              tabindex="0"
              @click="go(r.id)"
              @keydown.enter="go(r.id)"
            >
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
            tabindex="0"
            @click="scrollToHeading(item.id)"
            @keydown.enter="scrollToHeading(item.id)"
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

  <!-- 首屏加载：画一张"文章的形状"，比一行"加载中..."更有信息量，
       而且高度和真实正文接近，读完不会跳版 -->
  <div v-else-if="loading" class="anime-card anime-card--flat detail-skeleton" aria-hidden="true">
    <div class="anime-skeleton sk-line sk-h1"></div>
    <div class="anime-skeleton sk-line sk-meta"></div>
    <div v-for="i in 5" :key="i" class="anime-skeleton sk-line sk-p"></div>
    <div class="anime-skeleton sk-line sk-p sk-p--short"></div>
  </div>
</template>

<style scoped>
.post-detail {
  margin-bottom: var(--space-4);
  /* 正文卡的留白比列表卡更宽松。
     卡片统一 20px 内边距对列表够用，但长文会读得很挤 ——
     左右各 32px 让单行字数落进 30~40 字的舒适区间。 */
  padding: var(--space-8) var(--space-8) var(--space-6);
}

/* 文章大标题：和正文 h1 同字号，字重更高，字距略收紧。
   中文大字号不需要 negative tracking 太多，-0.01em 刚好把"松"的感觉收掉。 */
.post-title {
  margin: 0 0 var(--space-4);
  font-size: var(--text-3xl);
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: -0.01em;
  color: var(--text-strong);
}

@media (max-width: 768px) {
  .post-detail {
    padding: var(--space-5) var(--space-4) var(--space-4);
  }
  .post-title {
    font-size: var(--text-2xl);
  }
}

.post-meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: 1.6;
  margin-bottom: var(--space-3);
  flex-wrap: wrap;
}

.author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.author:hover .author-name {
  text-decoration: underline;
}

.author-avatar {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-full);
  overflow: hidden;
  background: linear-gradient(135deg, var(--brand-200), var(--blue-300));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px var(--surface);
}

.author-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 无头像时的首字母：原来用的是白字，压在浅粉浅蓝的渐变上几乎看不见。
   改成品牌深色，浅色/暗色两种模式都能读。 */
.author-fallback {
  color: var(--brand-800);
  font-weight: 700;
  font-size: 12px;
}

.author-name {
  color: var(--brand-700);
  font-weight: 600;
}

/* 系列徽标：紫色系，和分类 / 标签区分开 */
.series-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 8px;
  border-radius: var(--radius-full);
  background: var(--purple-100);
  color: var(--purple-700);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.series-badge:hover {
  background: var(--purple-300);
}

.tags {
  margin-bottom: var(--space-4);
}

/* 详情页标签用"浅底 + 品牌字"，和列表页的实心彩胶囊刻意不同：
   列表要快速扫读所以靠颜色区分，详情页只需要安静地说明归属。
   这里提高一级特异性（.tags .anime-tag）压过全局的 nth-child 彩色底，
   否则 scoped 规则和全局规则同为 (0,2,0)，谁生效取决于打包顺序 —— 太脆。 */
.tags .anime-tag {
  display: inline-block;
  padding: 3px 12px;
  margin: 0 6px 6px 0;
  background: var(--surface-pink);
  color: var(--brand-700);
  border: 1px solid var(--border-softer);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  box-shadow: none;
}

.tags .anime-tag.clickable {
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out),
              border-color var(--dur-fast) var(--ease-out);
}

.tags .anime-tag.clickable:hover {
  background: var(--brand-100);
  border-color: var(--border-brand);
  transform: none;
  filter: none;
}

.post-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px dashed var(--border-soft);
}

/* 点赞 / 收藏 / 举报这三个是"胶囊操作条"：
   底座沿用全局按钮的 46px 高度与 pill 圆角，但状态色自己管
   （全局 .anime-btn--secondary 的 :hover 特异性是 (0,4,0)，
   本地 .liked 只有 (0,3,0) 会被盖掉，反而在悬停时"变回未选中"，很怪）。 */
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 var(--space-5);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-full);
  background: var(--surface);
  color: var(--text-body);
  font-size: var(--text-base);
  font-weight: 500;
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-out),
              border-color var(--dur-fast) var(--ease-out),
              background-color var(--dur-fast) var(--ease-out),
              color var(--dur-fast) var(--ease-out),
              box-shadow var(--dur-base) var(--ease-out);
}

.like-btn:hover:not(:disabled) {
  border-color: var(--border-brand);
  color: var(--brand-700);
  background: var(--surface-pink);
  transform: translateY(-1px);
}

.like-btn:active:not(:disabled) {
  transform: translateY(1px) scale(0.985);
}

.like-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.like-icon {
  font-size: 18px;
  line-height: 1;
}

/* 已点赞：实心品牌渐变。原来用的是 #ffd6e4→#ffb3cd 配白字，
   浅粉底上的白字对比度只有 1.6:1，基本等于看不见。 */
.like-btn.liked {
  background: linear-gradient(135deg, var(--brand-500) 0%, var(--brand-400) 100%);
  border-color: transparent;
  color: var(--text-on-brand);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42),
              var(--shadow-brand);
}

.like-btn.liked:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--brand-600) 0%, var(--brand-500) 100%);
  color: var(--text-on-brand);
}

/* 已收藏：琥珀色。原实现同样是浅黄底 + 白字（#ffe9b8/#ffd98e），
   这里换成柔和的琥珀底 + 深琥珀字，浅色和暗色都成立。 */
.fav-btn.favorited {
  background: var(--amber-100);
  border-color: var(--amber-300);
  color: var(--amber-700);
}

.fav-btn.favorited:hover:not(:disabled) {
  background: var(--amber-100);
  border-color: var(--amber-500);
  color: var(--amber-700);
}

/* 举报是低频且带负面意味的操作，视觉上要压到最轻 */
.report-btn {
  color: var(--text-muted);
}

.report-btn:hover:not(:disabled) {
  color: var(--danger);
  border-color: rgba(212, 71, 92, 0.32);
  background: var(--danger-soft);
}

.prev-next {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.nav-item {
  flex: 1;
  min-width: 0; /* 允许标题省略号截断，否则长标题会把卡片撑开 */
  cursor: pointer;
  text-align: center;
}

.nav-item .label {
  display: block;
  color: var(--text-muted);
  font-size: var(--text-xs);
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}

.nav-item .nav-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--brand-700);
  font-size: var(--text-base);
  font-weight: 500;
  line-height: 1.5;
  transition: color var(--dur-fast) var(--ease-out);
}

/* 占位格：用虚线框而不是一行灰字，和隔壁真卡片等高，
   否则"上一篇没有了"会让整个操作条高度塌一半。 */
.nav-item.disabled {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 78px;
  padding: var(--space-5);
  border: 1px dashed var(--border-soft);
  border-radius: var(--radius-xl);
  color: var(--text-faint);
  font-size: var(--text-sm);
  cursor: default;
}

.loading-tip {
  text-align: center;
  color: var(--text-muted);
  padding: var(--space-12) var(--space-5);
  font-size: var(--text-base);
}

/* ===== 文章骨架屏（首次加载时替代"加载中..."） ===== */
.detail-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-skeleton .sk-line {
  height: 14px;
  border-radius: var(--radius-xs);
}

.detail-skeleton .sk-h1 {
  height: 32px;
  width: 68%;
  margin-bottom: var(--space-2);
}

.detail-skeleton .sk-meta {
  height: 14px;
  width: 42%;
  margin-bottom: var(--space-5);
}

.detail-skeleton .sk-p {
  width: 100%;
}

.detail-skeleton .sk-p--short {
  width: 56%;
}

/* ===== 文章目录 ===== */
.detail-layout {
  display: flex;
  gap: var(--space-5);
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
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-3);
  box-shadow: var(--shadow-xs);
  scrollbar-width: thin;
}

.toc-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px dashed var(--border-soft);
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.toc-item {
  padding: 6px 10px;
  font-size: var(--text-sm);
  color: var(--text-body);
  cursor: pointer;
  border-radius: var(--radius-xs);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background-color var(--dur-fast) var(--ease-out),
              color var(--dur-fast) var(--ease-out);
}

.toc-item:hover {
  color: var(--brand-700);
  background: var(--surface-pink);
}

/* 当前章节：左侧一道实心短杠 + 向右淡出的粉色底。
   --brand-100 在暗色下会翻转成深玫红，所以这个渐变两种模式都成立。 */
.toc-item.active {
  color: var(--brand-700);
  background: linear-gradient(90deg, var(--brand-100), transparent);
  box-shadow: inset 2px 0 0 var(--brand-500);
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
  margin-top: var(--space-6);
  padding: var(--space-4) var(--space-4);
  border: 1px dashed var(--border-soft);
  border-radius: var(--radius-lg);
  background: var(--surface-soft);
  font-size: var(--text-sm);
  color: var(--text-muted);
  line-height: 1.8;
}

.copyright-head {
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: var(--space-2);
}

.copyright-text {
  margin: 0 0 6px;
}

.cp-author {
  color: var(--brand-700);
  cursor: pointer;
  font-weight: 500;
}

.cp-author:hover {
  text-decoration: underline;
}

.cp-link {
  color: var(--purple-700);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.cp-link:hover {
  color: var(--purple-500);
}

.copyright-license {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

/* ===== 相关推荐 ===== */
.related {
  margin-bottom: var(--space-4);
}

.related-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-strong);
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.related-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.related-item:hover {
  background: var(--surface-pink);
}

.related-item-title {
  flex: 1;
  min-width: 0;
  color: var(--text-body);
  font-size: var(--text-base);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease-out);
}

.related-item:hover .related-item-title {
  color: var(--brand-700);
}

.related-item-meta {
  flex-shrink: 0;
  color: var(--text-faint);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
}

/* ===== 图片灯箱 ===== */
.markdown-body img {
  cursor: zoom-in;
}

.lightbox-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  background: rgba(0, 0, 0, 0.86);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  cursor: zoom-out;
  /* 入场淡入，避免点图瞬间"啪"地糊一屏 */
  animation: lightbox-in var(--dur-base) var(--ease-out);
}

@keyframes lightbox-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}

.lightbox-img {
  max-width: 92vw;
  max-height: 86vh;
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.6);
}

.lightbox-tip {
  margin-top: var(--space-4);
  color: rgba(255, 255, 255, 0.62);
  font-size: var(--text-sm);
}
</style>
