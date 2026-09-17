import { createRouter, createWebHistory } from 'vue-router'
import { reportVisit } from '@/api'

/**
 * 路由分为两类（公开博客模型）：
 *   - 公开页面：任何人都能访问 —— 首页、文章详情、分类、标签、归档、搜索、关于、友链
 *   - 需要登录：标了 meta.requiresAuth 的页面 —— 发布文章、个人资料、后台管理
 */
const routes = [
  // ===== 公开页面 =====
  { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
  { path: '/posts/:id', name: 'post', component: () => import('@/views/PostDetail.vue') },
  { path: '/categories', name: 'categories', component: () => import('@/views/Categories.vue') },
  { path: '/tags', name: 'tags', component: () => import('@/views/Tags.vue') },
  { path: '/archives', name: 'archives', component: () => import('@/views/Archives.vue') },
  { path: '/search', name: 'search', component: () => import('@/views/Search.vue') },
  { path: '/about', name: 'about', component: () => import('@/views/About.vue') },
  { path: '/links', name: 'links', component: () => import('@/views/Links.vue') },
  { path: '/policy', name: 'policy', component: () => import('@/views/Policy.vue') },

  // 兜底：任何未匹配的路径都进 404 页（必须放最后）
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') },

  // ===== 登录 / 注册 =====
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('@/views/Register.vue') },

  // ===== 需要登录 =====
  {
    path: '/profile',
    name: 'profile',
    meta: { requiresAuth: true },
    component: () => import('@/views/Profile.vue')
  },
  {
    path: '/favorites',
    name: 'favorites',
    meta: { requiresAuth: true },
    component: () => import('@/views/Favorites.vue')
  },
  {
    path: '/following',
    name: 'following',
    meta: { requiresAuth: true },
    component: () => import('@/views/Following.vue')
  },
  {
    path: '/series',
    name: 'series',
    component: () => import('@/views/Series.vue')
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/ForgotPassword.vue')
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/views/ResetPassword.vue')
  },
  {
    path: '/unsubscribe',
    name: 'unsubscribe',
    component: () => import('@/views/Unsubscribe.vue')
  },
  {
    path: '/messages',
    name: 'messages',
    meta: { requiresAuth: true },
    component: () => import('@/views/Messages.vue')
  },
  // 用户主页（公开）：点别人的头像进这里，可查看 TA 的文章并关注
  {
    path: '/users/:id',
    name: 'user-profile',
    component: () => import('@/views/UserProfile.vue')
  },
  {
    path: '/write',
    name: 'write',
    meta: { requiresAuth: true },
    component: () => import('@/views/Write.vue')
  },
  {
    path: '/admin',
    meta: { requiresAuth: true },
    component: () => import('@/views/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/posts' },
      { path: 'posts', name: 'admin-posts', component: () => import('@/views/admin/PostManage.vue') },
      { path: 'trash', name: 'admin-trash', component: () => import('@/views/admin/RecycleBin.vue') },
      // 旧的编辑页已由前台 /write 取代，这里保留重定向以兼容老链接
      {
        path: 'posts/edit/:id?',
        redirect: (to) => ({ path: '/write', query: to.params.id ? { id: to.params.id } : {} })
      },
      { path: 'categories', name: 'admin-categories', component: () => import('@/views/admin/CategoryManage.vue') },
      { path: 'tags', name: 'admin-tags', component: () => import('@/views/admin/TagManage.vue') },
      { path: 'comments', name: 'admin-comments', component: () => import('@/views/admin/CommentManage.vue') },
      { path: 'sensitive-words', name: 'admin-sensitive-words', component: () => import('@/views/admin/SensitiveWordManage.vue') },
      { path: 'sensitive-logs', name: 'admin-sensitive-logs', component: () => import('@/views/admin/SensitiveLogManage.vue') },
      { path: 'reports', name: 'admin-reports', component: () => import('@/views/admin/ReportManage.vue') },
      { path: 'series', name: 'admin-series', component: () => import('@/views/admin/SeriesManage.vue') },
      { path: 'operation-logs', name: 'admin-operation-logs', component: () => import('@/views/admin/OperationLogManage.vue') },
      { path: 'links', name: 'admin-links', component: () => import('@/views/admin/LinkManage.vue') },
      { path: 'media', name: 'admin-media', component: () => import('@/views/admin/MediaManage.vue') },
      { path: 'subscribes', name: 'admin-subscribes', component: () => import('@/views/admin/SubscriberManage.vue') },
      { path: 'visits', name: 'admin-visits', component: () => import('@/views/admin/VisitLog.vue') },
      { path: 'settings', name: 'admin-settings', component: () => import('@/views/admin/SettingManage.vue') },
      { path: 'stats', name: 'admin-stats', component: () => import('@/views/admin/Stats.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const isAuthPage = to.path === '/login' || to.path === '/register'

  // 已登录还去登录 / 注册页 → 直接回首页
  if (isAuthPage && token) {
    return next('/')
  }

  // 只有标了 requiresAuth 的页面才强制登录，其余页面匿名可访问
  if (to.matched.some((record) => record.meta.requiresAuth) && !token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  next()
})

/**
 * 访客留痕：每次进入页面都上报一次（游客也会上报）。
 * 后台管理页不记录，避免管理员自己浏览后台的数据混进访客统计。
 * 上报失败静默忽略，绝不影响正常浏览。
 */
router.afterEach((to) => {
  if (to.path.startsWith('/admin')) return
  reportVisit({
    path: to.fullPath,
    referer: document.referrer || ''
  }).catch(() => {})
})

export default router
