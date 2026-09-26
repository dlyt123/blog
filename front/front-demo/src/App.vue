<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useSiteStore } from '@/store/site'
import SubscribeBox from '@/components/SubscribeBox.vue'
import { getUnreadMessageCount } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()
const isAdminPage = computed(() => route.path.startsWith('/admin'))
// 登录 / 注册页不展示订阅框，避免分散注意力
const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))

const menuOpen = ref(false)
// 移动端汉堡菜单（窄屏才用得到）
const mobileNavOpen = ref(false)

// 登录状态变化时刷新未读私信角标（登录后立刻生效，不用等下一次轮询）
watch(() => userStore.token, refreshUnread)

// ===== 深色模式 =====
const isDark = ref(localStorage.getItem('theme') === 'dark')
function applyTheme() {
  const dark = isDark.value
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
  // Element Plus 的暗色类
  document.documentElement.classList.toggle('dark', dark)
}
function toggleTheme() {
  isDark.value = !isDark.value
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
  applyTheme()
}

// ===== 回到顶部 =====
const showTop = ref(false)
function onScroll() {
  showTop.value = window.scrollY > 400
}
function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ===== 代码块一键复制（全局事件委托）=====
function onDocClick(e) {
  const btn = e.target && e.target.closest && e.target.closest('.code-copy-btn')
  if (!btn) return
  const block = btn.closest('.code-block')
  const code = block && block.querySelector('pre code')
  const text = code ? code.innerText : ''
  navigator.clipboard.writeText(text).then(() => {
    const old = btn.textContent
    btn.textContent = '已复制'
    setTimeout(() => { btn.textContent = old }, 1500)
  }).catch(() => {})
}

// ===== 未读私信角标 =====
const unreadCount = ref(0)
let unreadTimer = null

async function refreshUnread() {
  if (!userStore.token) {
    unreadCount.value = 0
    return
  }
  try {
    const data = await getUnreadMessageCount()
    unreadCount.value = data?.count || 0
  } catch (e) {
    // 静默失败，不打扰用户
  }
}

onMounted(async () => {
  applyTheme()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocClick)
  // 站点信息（logo / 页脚文案 / 浏览器标题）从「站点设置」读取
  siteStore.load().catch(() => {})
  // 进入前台时，如果有 token 但还没拉过用户信息，自动拉取
  if (userStore.token && !userStore.userInfo) {
    await userStore.fetchMe().catch(() => {})
  }
  refreshUnread()
  // 每 60 秒刷新一次未读数，效果接近「有消息就会亮起来」
  unreadTimer = setInterval(refreshUnread, 60_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocClick)
  if (unreadTimer) {
    clearInterval(unreadTimer)
  }
})

const displayName = computed(() => {
  const u = userStore.userInfo || {}
  return u.nickname || u.username || '我'
})

// 是否为管理员（普通用户 role 为 USER/空，管理员为 ADMIN）
const isAdmin = computed(() => (userStore.userInfo?.role || '').toUpperCase() === 'ADMIN')

function logout() {
  userStore.logout()
  menuOpen.value = false
  ElMessage.info('已退出登录')
  router.push('/login')
}

function goWrite() {
  router.push('/write')
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}
</script>

<template>
  <div v-if="isAdminPage">
    <router-view />
  </div>

  <div v-else class="anime-layout" @click="closeMenu">
    <!-- 顶部导航 -->
    <header class="navbar">
      <div class="navbar-inner">
        <router-link to="/" class="logo">
          <!-- 站点设置里配了 logo 就显示图片，否则用默认图标 -->
          <img v-if="siteStore.logo" :src="siteStore.logo" class="logo-img" alt="logo" />
          <span v-else class="logo-icon">🌸</span>
          <span class="logo-text">{{ siteStore.siteName }}</span>
        </router-link>

        <nav class="nav-links" @click.stop>
          <!-- nav-page-link：这些文字链接在窄屏下会被收进汉堡菜单 -->
          <router-link to="/" class="nav-page-link">首页</router-link>
          <router-link to="/categories" class="nav-page-link">分类</router-link>
          <router-link to="/tags" class="nav-page-link">标签</router-link>
          <router-link to="/archives" class="nav-page-link">归档</router-link>
          <router-link to="/series" class="nav-page-link">系列</router-link>
          <router-link to="/about" class="nav-page-link">关于</router-link>
          <router-link to="/links" class="nav-page-link">友链</router-link>
          <router-link to="/search" class="search-link" aria-label="搜索">🔍</router-link>
          <router-link
            v-if="userStore.token"
            to="/messages"
            class="msg-link"
            title="私信"
          >
            💬
            <span v-if="unreadCount > 0" class="msg-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>
          <button class="theme-btn" @click="toggleTheme" :title="isDark ? '切到浅色' : '切到深色'">
            {{ isDark ? '☀️' : '🌙' }}
          </button>

          <!-- 已登录：显示用户菜单 -->
          <div v-if="userStore.isLogin" class="user-menu" @click.stop>
            <!-- 复用全局的 .anime-btn--primary，导航栏只额外覆盖高度。
                 以前这里是一套独立的按钮样式，和其它页面的按钮各不相同。 -->
            <button class="anime-btn anime-btn--primary write-btn" @click="goWrite">✍️ 写文章</button>
            <div class="user-trigger" @click="toggleMenu">
              <div class="avatar-mini">
                <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" :alt="displayName" />
                <span v-else>{{ displayName[0] }}</span>
              </div>
              <span class="user-name">{{ displayName }}</span>
              <span class="caret">▾</span>
            </div>
            <div v-if="menuOpen" class="user-dropdown">
              <router-link to="/write" class="dropdown-item" @click="closeMenu">写文章</router-link>
              <router-link to="/admin/posts" class="dropdown-item" @click="closeMenu">
                {{ isAdmin ? '后台管理' : '我的文章' }}
              </router-link>
              <router-link
                v-if="userStore.userInfo?.id"
                :to="`/users/${userStore.userInfo.id}`"
                class="dropdown-item"
                @click="closeMenu"
              >我的主页</router-link>
              <router-link to="/favorites" class="dropdown-item" @click="closeMenu">我的收藏</router-link>
              <router-link to="/following" class="dropdown-item" @click="closeMenu">关注流</router-link>
              <router-link to="/messages" class="dropdown-item" @click="closeMenu">
                私信<span v-if="unreadCount > 0" class="dropdown-badge">{{ unreadCount }}</span>
              </router-link>
              <router-link to="/profile" class="dropdown-item" @click="closeMenu">个人信息</router-link>
              <button class="dropdown-item danger" @click="logout">退出登录</button>
            </div>
          </div>

          <!-- 未登录（访客）：显示登录 / 注册入口 -->
          <template v-else>
            <router-link to="/login" class="login-link">登录</router-link>
            <router-link to="/register" class="register-link">注册</router-link>
          </template>

          <!-- 移动端汉堡按钮：窄屏才显示，点开是完整的导航面板 -->
          <button
            class="menu-toggle"
            :title="mobileNavOpen ? '关闭菜单' : '打开菜单'"
            @click="mobileNavOpen = !mobileNavOpen"
          >
            {{ mobileNavOpen ? '✕' : '☰' }}
          </button>
        </nav>
      </div>

      <!-- 移动端导航面板（宽屏不显示） -->
      <nav v-if="mobileNavOpen" class="mobile-nav" @click.stop="mobileNavOpen = false">
        <router-link to="/" class="mobile-nav-item">🏠 首页</router-link>
        <router-link to="/categories" class="mobile-nav-item">📁 分类</router-link>
        <router-link to="/tags" class="mobile-nav-item">🏷️ 标签</router-link>
        <router-link to="/archives" class="mobile-nav-item">📅 归档</router-link>
        <router-link to="/series" class="mobile-nav-item">📚 系列</router-link>
        <router-link to="/about" class="mobile-nav-item">👤 关于</router-link>
        <router-link to="/links" class="mobile-nav-item">🔗 友链</router-link>

        <div class="mobile-nav-divider"></div>

        <template v-if="userStore.isLogin">
          <router-link to="/write" class="mobile-nav-item">✍️ 写文章</router-link>
          <router-link to="/messages" class="mobile-nav-item">
            💬 私信<span v-if="unreadCount > 0" class="dropdown-badge">{{ unreadCount }}</span>
          </router-link>
          <router-link to="/admin/posts" class="mobile-nav-item">
            {{ isAdmin ? '⚙️ 后台管理' : '📝 我的文章' }}
          </router-link>
          <router-link
            v-if="userStore.userInfo?.id"
            :to="`/users/${userStore.userInfo.id}`"
            class="mobile-nav-item"
          >🙋 我的主页</router-link>
          <router-link to="/favorites" class="mobile-nav-item">⭐ 我的收藏</router-link>
          <router-link to="/following" class="mobile-nav-item">👥 关注流</router-link>
          <router-link to="/profile" class="mobile-nav-item">🔧 个人信息</router-link>
          <button class="mobile-nav-item danger" @click="logout">🚪 退出登录</button>
        </template>
        <template v-else>
          <router-link to="/login" class="mobile-nav-item">🔑 登录</router-link>
          <router-link to="/register" class="mobile-nav-item">✨ 注册</router-link>
        </template>
      </nav>
    </header>

    <main class="anime-container">
      <router-view />
    </main>

    <footer class="footer">
      <div class="footer-inner">
        <SubscribeBox v-if="!isAuthPage" />
        <p class="footer-links">
          <router-link to="/policy?tab=agreement">用户协议</router-link>
          <span class="sep">·</span>
          <router-link to="/policy?tab=privacy">隐私政策</router-link>
          <span class="sep">·</span>
          <router-link to="/policy?tab=disclaimer">免责声明</router-link>
          <span class="sep">·</span>
          <router-link to="/links">友情链接</router-link>
        </p>
        <p>{{ siteStore.slogan }} · {{ siteStore.siteName }}</p>
        <p v-if="siteStore.icp || siteStore.police" class="icp">
          <a v-if="siteStore.icp" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ siteStore.icp }}</a>
          <span v-if="siteStore.icp && siteStore.police" class="icp-sep">·</span>
          <a v-if="siteStore.police" :href="siteStore.policeUrl" target="_blank" rel="noopener noreferrer">{{ siteStore.police }}</a>
        </p>
      </div>
    </footer>

    <!-- 回到顶部 -->
    <button v-if="showTop" class="back-top" @click="backToTop" title="回到顶部">↑</button>
  </div>
</template>

<style scoped>
.anime-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 导航栏：全站每页都在最上面，是"质感"最容易被感知的地方。
   毛玻璃 + 半透明底，让内容从下方滚过时有一层柔和的过渡。 */
.navbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  background: var(--surface-glass);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  backdrop-filter: blur(16px) saturate(1.6);
  border-bottom: 1px solid var(--border-soft);
}

.navbar-inner {
  /* 宽度跟内容区对齐：导航左边缘和文章卡片左边缘落在同一条竖线上。
     以前这里是 1100px、内容是 1080px，差 20px —— 单看没事，
     但页面上下一对比，就会觉得"没对齐"。 */
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 0 var(--space-5);
  height: var(--navbar-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 700;
  font-size: var(--text-xl);
  color: var(--brand-700);
  flex-shrink: 0;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.logo:hover {
  opacity: 0.82;
}

.logo-icon {
  font-size: 24px;
  line-height: 1;
}

.logo-img {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  object-fit: cover;
  box-shadow: var(--shadow-xs);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.nav-links a {
  color: var(--text-body);
  font-size: var(--text-md);
  transition: color var(--dur-fast) var(--ease-out);
}

.nav-links a:hover,
.nav-links a.router-link-active {
  color: var(--brand-700);
}

/* 导航文字链接的下划线：从左侧展开的 2px 渐变线。
   比单纯变色多一层反馈，也是导航最容易做出精致感的地方。
   只给 .nav-page-link 加，图标类链接（搜索 / 私信）不需要。 */
.nav-page-link {
  position: relative;
}

.nav-page-link::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -5px;
  height: 2px;
  border-radius: var(--radius-full);
  background: linear-gradient(90deg, var(--brand-500), var(--brand-400));
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform var(--dur-base) var(--ease-out);
}

.nav-page-link:hover::after,
.nav-page-link.router-link-active::after {
  transform: scaleX(1);
}

.search-link {
  font-size: var(--text-lg);
  line-height: 1;
}

.login-link {
  color: var(--brand-700) !important;
  font-weight: 600;
}

.register-link {
  color: var(--text-body);
  font-size: var(--text-base);
}

.register-link:hover {
  color: var(--brand-700);
}

/* ===== 移动端汉堡菜单 =====
   宽屏不显示；窄屏下把 7 个文字链接收进面板，导航栏只留图标，
   否则它们会被挤成「一列一个字」（这是移动端最扎眼的问题）。 */
.menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--radius-full);
  background: transparent;
  font-size: var(--text-xl);
  line-height: 1;
  cursor: pointer;
  color: var(--text-body);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.menu-toggle:hover {
  background: var(--surface-pink);
}

.mobile-nav {
  display: none;
}

@media (max-width: 768px) {
  .navbar-inner {
    padding: 0 var(--space-3);
    height: var(--navbar-h-mobile);
  }

  .logo {
    font-size: var(--text-lg);
    min-width: 0;
  }

  /* 站点名过长时省略，不要换行把导航栏撑成两行 */
  .logo-text {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 40vw;
  }

  .nav-page-link,
  .user-menu,
  .login-link,
  .register-link {
    display: none;
  }

  .nav-links {
    gap: var(--space-2);
  }

  .menu-toggle {
    display: flex;
  }

  .mobile-nav {
    display: flex;
    flex-direction: column;
    padding: var(--space-2) var(--space-3) var(--space-4);
    border-top: 1px solid var(--border-soft);
    background: var(--surface);
    box-shadow: var(--shadow-lg);
    max-height: calc(100vh - var(--navbar-h-mobile));
    overflow-y: auto;
  }

  .mobile-nav-item {
    display: block;
    width: 100%;
    text-align: left;
    padding: var(--space-3) var(--space-2);
    font-size: var(--text-md);
    color: var(--text-body);
    border: none;
    background: transparent;
    border-radius: var(--radius-md);
    cursor: pointer;
    box-sizing: border-box;
    transition: background-color var(--dur-fast) var(--ease-out),
                color var(--dur-fast) var(--ease-out);
  }

  .mobile-nav-item:hover,
  .mobile-nav-item.router-link-active {
    background: var(--surface-pink);
    color: var(--brand-700);
  }

  .mobile-nav-item.danger {
    color: var(--danger);
  }

  .mobile-nav-divider {
    height: 1px;
    background: var(--border-soft);
    margin: var(--space-2) 0;
  }
}

/* 移动菜单和汉堡按钮的配色现在全部走令牌
   （背景 var(--surface)、文字 var(--text-body)、悬停 var(--surface-pink)），
   暗色模式下变量自己会翻转，所以这里不再需要单独写一遍暗色覆盖。 */

/* 写文章按钮：外观来自全局的 .anime-btn--primary，
   这里只覆盖导航栏需要的高度 —— 不再重复实现一套按钮。 */
.write-btn {
  height: 34px;
  padding: 0 var(--space-4);
  font-size: var(--text-sm);
}

.user-menu {
  position: relative;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 4px;
  border: 1px solid transparent;
  border-radius: var(--radius-full);
  background: var(--surface-pink);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out);
}

.user-trigger:hover {
  border-color: var(--border-brand);
}

.avatar-mini {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--brand-200), var(--blue-300));
  /* 浅色渐变底配品牌深色字，白字在这里读不出来 */
  color: var(--brand-800);
  font-weight: 700;
  font-size: var(--text-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-mini img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-size: var(--text-sm);
  color: var(--text-strong);
  font-weight: 600;
}

.caret {
  font-size: 10px;
  color: var(--text-muted);
}

/* 下拉面板：浮层要"明显浮起来"，所以给更重的阴影 + 描边，
   再加一点入场动画，避免生硬地"啪"一下出现。 */
.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 152px;
  padding: 6px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: var(--z-dropdown);
  animation: dropdown-in var(--dur-base) var(--ease-out);
}

@keyframes dropdown-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 9px var(--space-3);
  text-align: left;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--text-base);
  color: var(--text-strong);
  cursor: pointer;
  text-decoration: none;
  transition: background-color var(--dur-fast) var(--ease-out),
              color var(--dur-fast) var(--ease-out);
}

.dropdown-item:hover {
  background: var(--surface-pink);
  color: var(--brand-700);
}

.dropdown-item.danger {
  color: var(--danger);
}

.dropdown-item.danger:hover {
  background: var(--danger-soft);
}

.anime-container {
  flex: 1;
}

/* 页脚：用一条两端渐隐的分割线代替生硬的整条 border-top，
   视觉上更轻，不会把页面"拦腰切断"。 */
.footer {
  position: relative;
  text-align: center;
  padding: var(--space-8) var(--space-5) var(--space-6);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, var(--container-max));
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--border-soft) 18%, var(--border-soft) 82%, transparent);
}

.footer-inner {
  max-width: 900px;
  margin: 0 auto;
}

.footer-links {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
}

.footer-links a {
  color: var(--text-muted);
}

.footer-links a:hover {
  color: var(--brand-700);
}

.footer-links .sep {
  margin: 0 var(--space-2);
  color: var(--text-faint);
}

.footer .icp {
  margin: 6px 0 0;
  font-size: var(--text-xs);
  color: var(--text-faint);
}

/* 备案号必须可点击（工信部 / 公安部的要求），但页脚不宜太抢眼 */
.footer .icp a {
  color: var(--text-faint);
  text-decoration: none;
  transition: color var(--dur-fast) var(--ease-out);
}

.footer .icp a:hover {
  color: var(--brand-700);
  text-decoration: underline;
}

.footer .icp-sep {
  margin: 0 6px;
}

/* ===== 深色模式切换按钮 ===== */
.theme-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--radius-full);
  background: transparent;
  font-size: var(--text-lg);
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out),
              transform var(--dur-base) var(--ease-spring);
}

/* 悬停时轻微旋一下：图标类按钮给一点个性，但幅度要克制 */
.theme-btn:hover {
  background: var(--surface-pink);
  transform: rotate(-18deg) scale(1.06);
}

/* ===== 私信入口 + 未读角标 ===== */
.msg-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-full);
  font-size: var(--text-lg);
  line-height: 1;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.msg-link:hover {
  background: var(--surface-pink);
}

.msg-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: var(--radius-full);
  background: var(--danger);
  color: var(--text-on-brand);
  font-size: 10px;
  line-height: 17px;
  text-align: center;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.dropdown-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: var(--radius-full);
  background: var(--danger);
  color: var(--text-on-brand);
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  font-variant-numeric: tabular-nums;
}

/* ===== 回到顶部 ===== */
.back-top {
  position: fixed;
  right: 28px;
  bottom: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--brand-500), var(--brand-400));
  color: var(--text-on-brand);
  font-size: var(--text-xl);
  cursor: pointer;
  /* 内高光 + 投影 + 品牌辉光：跟主按钮同一套阴影语言 */
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45),
              var(--shadow-lg),
              var(--shadow-brand);
  z-index: var(--z-sticky);
  transition: transform var(--dur-base) var(--ease-spring);
}

.back-top:hover {
  transform: translateY(-3px) scale(1.05);
}
</style>