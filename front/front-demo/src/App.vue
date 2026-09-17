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
          <router-link to="/search" class="search-link">🔍</router-link>
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
            <button class="write-btn" @click="goWrite">✍️ 写文章</button>
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
        <p v-if="siteStore.icp" class="icp">{{ siteStore.icp }}</p>
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

.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 107, 157, 0.2);
}

.navbar-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 20px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 20px;
  color: #e04e82;
}

.logo-icon {
  font-size: 24px;
}

.logo-img {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  object-fit: cover;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 18px;
}

.nav-links a {
  color: #6a6a7a;
  font-size: 15px;
  transition: color 0.2s;
}

.nav-links a:hover,
.nav-links a.router-link-active {
  color: #e04e82;
}

.search-link {
  font-size: 16px;
}

.login-link {
  color: #e04e82 !important;
  font-weight: 600;
}

.register-link {
  color: var(--text-body);
  font-size: 14px;
}

.register-link:hover {
  color: #e04e82;
}

/* ===== 移动端汉堡菜单 =====
   宽屏不显示；窄屏下把 7 个文字链接收进面板，导航栏只留图标，
   否则它们会被挤成「一列一个字」（这是移动端最扎眼的问题）。 */
.menu-toggle {
  display: none;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  padding: 4px 6px;
  cursor: pointer;
  color: #6a6a7a;
}

.mobile-nav {
  display: none;
}

@media (max-width: 768px) {
  .navbar-inner {
    padding: 0 12px;
    height: 54px;
  }

  .logo {
    font-size: 17px;
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
    gap: 10px;
  }

  .menu-toggle {
    display: block;
  }

  .mobile-nav {
    display: flex;
    flex-direction: column;
    padding: 8px 12px 14px;
    border-top: 1px solid rgba(255, 107, 157, 0.15);
    background: rgba(255, 255, 255, 0.97);
    max-height: calc(100vh - 54px);
    overflow-y: auto;
  }

  .mobile-nav-item {
    display: block;
    width: 100%;
    text-align: left;
    padding: 12px 8px;
    font-size: 15px;
    color: #5a5a6a;
    border: none;
    background: transparent;
    border-radius: 10px;
    cursor: pointer;
    box-sizing: border-box;
  }

  .mobile-nav-item:hover,
  .mobile-nav-item.router-link-active {
    background: var(--surface-pink);
    color: #e04e82;
  }

  .mobile-nav-item.danger {
    color: #e24b4a;
  }

  .mobile-nav-divider {
    height: 1px;
    background: rgba(255, 107, 157, 0.15);
    margin: 8px 0;
  }
}

/* 暗色模式下的移动菜单 */
[data-theme='dark'] .mobile-nav {
  background: rgba(32, 32, 44, 0.98);
  border-top-color: rgba(255, 255, 255, 0.08);
}

[data-theme='dark'] .mobile-nav-item {
  color: #c8c8d8;
}

[data-theme='dark'] .mobile-nav-item:hover,
[data-theme='dark'] .mobile-nav-item.router-link-active {
  background: #2a2a38;
  color: #ffb3cd;
}

[data-theme='dark'] .menu-toggle {
  color: #c8c8d8;
}

.write-btn {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.write-btn:hover {
  opacity: 0.92;
}

.user-menu {
  position: relative;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  background: rgba(255, 214, 228, 0.4);
  cursor: pointer;
  transition: background 0.2s;
}

.user-trigger:hover {
  background: rgba(255, 214, 228, 0.7);
}

.avatar-mini {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  color: #fff;
  font-weight: 700;
  font-size: 13px;
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
  font-size: 13px;
  color: var(--text-strong);
  font-weight: 600;
}

.caret {
  font-size: 10px;
  color: var(--text-muted);
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 140px;
  background: var(--surface);
  border-radius: 10px;
  box-shadow: 0 6px 20px rgba(224, 78, 130, 0.15);
  overflow: hidden;
  padding: 6px 0;
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 10px 16px;
  text-align: left;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-strong);
  cursor: pointer;
  text-decoration: none;
}

.dropdown-item:hover {
  background: var(--surface-pink);
  color: #e04e82;
}

.dropdown-item.danger {
  color: #d05070;
}

.anime-container {
  flex: 1;
}

.footer {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
  font-size: 13px;
}

.footer-inner {
  max-width: 900px;
  margin: 0 auto;
}

.footer-links {
  margin: 0 0 8px;
  font-size: 12px;
}

.footer-links a {
  color: var(--text-muted);
}

.footer-links a:hover {
  color: #e04e82;
}

.footer-links .sep {
  margin: 0 8px;
  color: #d8ccd8;
}

.footer .icp {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-faint);
}

/* ===== 深色模式切换按钮 ===== */
.theme-btn {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
  transition: transform 0.2s;
}

.theme-btn:hover {
  transform: scale(1.15);
}

/* ===== 私信入口 + 未读角标 ===== */
.msg-link {
  position: relative;
  font-size: 18px;
  line-height: 1;
  padding: 4px;
}

.msg-badge {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #ff4d6d;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  font-weight: 600;
}

.dropdown-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ff4d6d;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}

/* ===== 回到顶部 ===== */
.back-top {
  position: fixed;
  right: 28px;
  bottom: 40px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(255, 107, 157, 0.4);
  z-index: 90;
  transition: transform 0.2s;
}

.back-top:hover {
  transform: translateY(-3px);
}
</style>