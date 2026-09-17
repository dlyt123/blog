<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const ready = ref(false)

onMounted(async () => {
  // 刷新页面后确保拿到用户角色，避免菜单闪现错误
  try {
    if (userStore.token && !userStore.userInfo) {
      await userStore.fetchMe()
    }
  } catch (e) {
    // 忽略，仍渲染
  } finally {
    ready.value = true
  }
})

const isAdmin = computed(() => (userStore.userInfo?.role || '').toUpperCase() === 'ADMIN')

// 管理员：全部管理功能；博主：只有「我的文章」+「个人信息」
const adminMenus = [
  { path: '/admin/posts', label: '文章管理', icon: '📝' },
  { path: '/admin/trash', label: '回收站', icon: '🗑️' },
  { path: '/admin/categories', label: '分类管理', icon: '📁' },
  { path: '/admin/tags', label: '标签管理', icon: '🏷️' },
  { path: '/admin/comments', label: '评论管理', icon: '💬' },
  { path: '/admin/sensitive-words', label: '敏感词管理', icon: '🚫' },
  { path: '/admin/sensitive-logs', label: '敏感词命中', icon: '📋' },
  { path: '/admin/reports', label: '内容举报', icon: '🚩' },
  { path: '/admin/series', label: '系列管理', icon: '📚' },
  { path: '/admin/links', label: '友链管理', icon: '🔗' },
  { path: '/admin/media', label: '媒体库', icon: '🖼️' },
  { path: '/admin/subscribes', label: '邮件订阅', icon: '📮' },
  { path: '/admin/visits', label: '访客记录', icon: '👣' },
  { path: '/admin/settings', label: '站点设置', icon: '⚙️' },
  { path: '/admin/stats', label: '数据统计', icon: '📊' },
  { path: '/admin/operation-logs', label: '操作日志', icon: '📋' },
  { path: '/profile', label: '个人信息', icon: '👤' }
]

const bloggerMenus = [
  { path: '/admin/posts', label: '我的文章', icon: '📝' },
  { path: '/profile', label: '个人信息', icon: '👤' }
]

const menus = computed(() => (isAdmin.value ? adminMenus : bloggerMenus))

function logout() {
  userStore.logout()
  router.push('/login')
}

function goHome() {
  router.push('/')
}

function goWrite() {
  router.push('/write')
}
</script>

<template>
  <div v-if="ready" class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-title">🌸 {{ isAdmin ? '后台管理' : '个人中心' }}</div>
      <nav class="menu">
        <router-link
          v-for="m in menus"
          :key="m.path"
          :to="m.path"
          class="menu-item"
          active-class="active"
        >
          <span>{{ m.icon }}</span>{{ m.label }}
        </router-link>
      </nav>
      <div class="sidebar-bottom">
        <button class="btn write" @click="goWrite">✍️ 写文章</button>
        <button class="btn" @click="goHome">返回前台</button>
        <button class="btn logout" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--surface-soft);
}

.sidebar {
  width: 200px;
  background: var(--surface);
  border-right: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.sidebar-title {
  padding: 24px 20px;
  font-weight: 700;
  font-size: 18px;
  color: #e04e82;
  border-bottom: 1px solid var(--border-soft);
}

.menu {
  flex: 1;
  padding: 10px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  color: #6a6a7a;
  transition: all 0.2s;
}

.menu-item:hover {
  background: var(--surface-pink);
  color: #e04e82;
}

.menu-item.active {
  background: linear-gradient(90deg, #ffd6e4, transparent);
  color: #e04e82;
  font-weight: 600;
}

.sidebar-bottom {
  padding: 16px;
  border-top: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn {
  padding: 8px;
  border: 1px solid #ffb3cd;
  border-radius: 8px;
  background: var(--surface);
  color: #e04e82;
  cursor: pointer;
  font-size: 13px;
}

.btn.write {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  border: none;
}

.btn.logout {
  background: var(--surface-soft);
  border-color: var(--border-soft);
  color: #888;
}

.content {
  flex: 1;
  padding: 24px;
  overflow: auto;
}

/* ================= 手机端 =================
   原来的 200px 竖向侧边栏在手机上要占掉一半屏宽，
   内容区只剩 200px 左右，表格被压成一条条看不清。
   改为：侧边栏变成顶部横条，16 个菜单项横向滑动；
   表格给一个最小宽度，让内容区横向滚动而不是把列挤扁。 */
@media (max-width: 768px) {
  .admin-layout {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: auto;
    position: static;
    border-right: none;
    border-bottom: 1px solid var(--border-soft);
  }

  .sidebar-title {
    padding: 12px 16px;
    font-size: 16px;
  }

  .menu {
    display: flex;
    flex: none;
    gap: 6px;
    padding: 8px 10px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  /* 隐藏横滑条，视觉上更干净 */
  .menu::-webkit-scrollbar {
    display: none;
  }

  .menu-item {
    flex-shrink: 0;
    padding: 7px 12px;
    border-radius: 999px;
    background: var(--surface-pink);
    font-size: 13px;
    white-space: nowrap;
    gap: 4px;
  }

  .menu-item.active {
    background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
    color: #fff;
  }

  .sidebar-bottom {
    flex-direction: row;
    padding: 10px 12px;
    gap: 8px;
  }

  .sidebar-bottom .btn {
    flex: 1;
    padding: 9px 6px;
  }

  .content {
    padding: 14px 12px;
  }

  /* 表格别被挤扁：给最小宽度，超出部分在内容区横向滚动 */
  .content :deep(.el-table) {
    min-width: 620px;
  }
}
</style>