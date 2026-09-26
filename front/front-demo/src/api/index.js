import http from './request'

// ===== 认证 =====
export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)
// config 支持 { skipAuthToast: true }：启动时静默校验 token，失效就当作访客，不弹提示
export const getMe = (config) => http.get('/auth/me', config)
export const updateProfile = (data) => http.put('/auth/me', data)

// 上传图片（头像等），返回 { url }
export const uploadMedia = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return http.post('/admin/media', fd)
}

// ===== 文章 =====
export const getPosts = (params) => http.get('/posts', { params })
export const getPost = (id) => http.get(`/posts/${id}`)
export const getArchives = () => http.get('/archives')
export const searchPosts = (params) => http.get('/search', { params })
export const getAbout = () => http.get('/about')

// ===== 分类 / 标签 =====
export const getCategories = () => http.get('/categories')
export const getCategoryPosts = (id, params) => http.get(`/categories/${id}/posts`, { params })
export const getTags = () => http.get('/tags')

// ===== 评论 =====
export const getComments = (postId, params) => http.get(`/posts/${postId}/comments`, { params })
export const addComment = (postId, data) => http.post(`/posts/${postId}/comments`, data)

// ===== 互动 =====
export const likePost = (id) => http.post(`/posts/${id}/like`)
export const unlikePost = (id) => http.delete(`/posts/${id}/like`)
export const getLikeStatus = (id) => http.get(`/posts/${id}/like/status`)
export const favoritePost = (id) => http.post(`/posts/${id}/favorite`)
export const unfavoritePost = (id) => http.delete(`/posts/${id}/favorite`)
export const getFavoriteStatus = (id) => http.get(`/posts/${id}/favorite/status`)

// ===== 友链 / 设置 / 订阅 =====
export const getLinks = () => http.get('/links')
// 公开的站点基础信息（站点名 / Logo 等），登录页也要用，无需登录
export const getSiteInfo = () => http.get('/site-info')
export const getSettings = () => http.get('/settings')
export const subscribe = (data) => http.post('/subscribe', data)

// ===== 后台 =====
export const adminGetPosts = (params) => http.get('/admin/posts', { params })
export const adminCreatePost = (data) => http.post('/admin/posts', data)
export const adminUpdatePost = (id, data) => http.put(`/admin/posts/${id}`, data)
export const adminDeletePost = (id) => http.delete(`/admin/posts/${id}`)
export const adminPublishPost = (id, status) => http.post(`/admin/posts/${id}/publish`, { status })
export const adminPinPost = (id, pinned) => http.post(`/admin/posts/${id}/pin`, { pinned })
export const adminRecommendPost = (id, recommended) => http.post(`/admin/posts/${id}/recommend`, { recommended })
export const adminBatchPosts = (data) => http.post('/admin/posts/batch', data)
// 回收站
export const adminGetTrash = (params) => http.get('/admin/posts/trash', { params })
export const adminRestorePost = (id) => http.post(`/admin/posts/${id}/restore`)
export const adminPurgePost = (id) => http.delete(`/admin/posts/${id}/purge`)

export const adminCreateCategory = (data) => http.post('/admin/categories', data)
export const adminUpdateCategory = (id, data) => http.put(`/admin/categories/${id}`, data)
export const adminDeleteCategory = (id) => http.delete(`/admin/categories/${id}`)

export const adminCreateTag = (data) => http.post('/admin/tags', data)
export const adminUpdateTag = (id, data) => http.put(`/admin/tags/${id}`, data)
export const adminDeleteTag = (id) => http.delete(`/admin/tags/${id}`)

export const adminGetComments = (params) => http.get('/admin/comments', { params })
export const adminAuditComment = (id, status) => http.post(`/admin/comments/${id}/audit`, { status })
export const adminDeleteComment = (id) => http.delete(`/admin/comments/${id}`)

export const adminCreateLink = (data) => http.post('/admin/links', data)
export const adminUpdateLink = (id, data) => http.put(`/admin/links/${id}`, data)
export const adminDeleteLink = (id) => http.delete(`/admin/links/${id}`)

export const adminGetStats = () => http.get('/admin/stats/overview')
export const adminGetPopular = () => http.get('/admin/stats/popular')

// 媒体库
export const adminGetMedia = () => http.get('/admin/media')
export const adminDeleteMedia = (id) => http.delete(`/admin/media/${id}`)

// 邮件订阅
export const adminGetSubscribes = () => http.get('/admin/subscribes')
export const adminDeleteSubscribe = (id) => http.delete(`/admin/subscribes/${id}`)

// 访客留痕：前台上报（无需登录），后台查看（仅管理员）
export const reportVisit = (data) => http.post('/visit', data)
export const adminGetVisits = (params) => http.get('/admin/visits', { params })
export const adminGetVisitStats = () => http.get('/admin/visits/stats')
export const adminGetVisitTrend = (days = 7) => http.get('/admin/visits/trend', { params: { days } })
export const adminClearVisits = () => http.delete('/admin/visits')

// 敏感词管理（仅管理员）
export const adminGetSensitiveWords = (params) => http.get('/admin/sensitive-words', { params })
export const adminAddSensitiveWords = (data) => http.post('/admin/sensitive-words', data)
export const adminDeleteSensitiveWord = (id) => http.delete(`/admin/sensitive-words/${id}`)

// 公开的侧边栏数据（首页用）
export const getPopularPosts = (limit = 5) => http.get('/popular', { params: { limit } })
export const getRecentComments = (limit = 5) => http.get('/comments/recent', { params: { limit } })
export const getRelatedPosts = (id, limit = 5) => http.get(`/posts/${id}/related`, { params: { limit } })

// 用户主页 / 关注 / 我的收藏
export const getUserProfile = (id) => http.get(`/users/${id}`)
export const getUserPosts = (id, params) => http.get(`/users/${id}/posts`, { params })
export const followUser = (id) => http.post(`/users/${id}/follow`)
export const unfollowUser = (id) => http.delete(`/users/${id}/follow`)
export const getMyFavorites = (params) => http.get('/users/me/favorites', { params })
export const getFollowingPosts = (params) => http.get('/users/me/following/posts', { params })

// 敏感词命中记录（仅管理员）
export const adminGetSensitiveLogs = (params) => http.get('/admin/sensitive-logs', { params })
export const adminClearSensitiveLogs = () => http.delete('/admin/sensitive-logs')

// 内容举报
export const submitReport = (data) => http.post('/reports', data)
export const adminGetReports = (params) => http.get('/admin/reports', { params })
export const adminHandleReport = (id) => http.put(`/admin/reports/${id}/handle`)

// 文章系列 / 专栏
export const getSeries = () => http.get('/series')
export const getSeriesPosts = (id, params) => http.get(`/series/${id}/posts`, { params })
export const adminCreateSeries = (data) => http.post('/admin/series', data)
export const adminUpdateSeries = (id, data) => http.put(`/admin/series/${id}`, data)
export const adminDeleteSeries = (id) => http.delete(`/admin/series/${id}`)

// 友链存活检测（仅管理员）
export const adminCheckLinks = () => http.post('/admin/links/check')

// 来源分析（仅管理员）
// ⚠️ 参数必须是 { limit } 这样的对象。
// 之前写成 (params) => http.get(..., { params })，而调用处传的是数字 8，
// axios 拿到「数字当 params」会直接抛错、请求根本发不出去，
// 错误里没有 response，最终被前端提示成「网络异常，请检查网络后重试」。
export const adminGetReferrers = (limit = 10) => http.get('/admin/visits/referrers', { params: { limit } })

// 数据库备份（仅管理员）
export const adminBackupNow = () => http.post('/admin/backup')
export const adminListBackups = () => http.get('/admin/backup')

// 邮件配置自检（仅管理员）
export const adminMailStatus = () => http.get('/admin/mail/status')
export const adminSendTestMail = (to) => http.post('/admin/mail/test', { to })

// 操作日志 / 审计（仅管理员）
export const adminGetOperationLogs = (params) => http.get('/admin/operation-logs', { params })
export const adminClearOperationLogs = () => http.delete('/admin/operation-logs')

// 图形验证码（登录用）
export const getCaptcha = () => http.get('/captcha')

// 找回密码
export const forgotPassword = (email) => http.post('/auth/forgot-password', { email })
export const resetPassword = (data) => http.post('/auth/reset-password', data)

// 邮件退订
export const checkUnsubscribe = (token) => http.get('/subscribe/unsubscribe/check', { params: { token } })
export const unsubscribe = (token) => http.post('/subscribe/unsubscribe', { token })

// 注销账号（需密码二次确认）
export const deleteAccount = (password) => http.delete('/auth/me', { data: { password } })

// 私信
export const sendMessage = (data) => http.post('/messages', data)
export const getConversations = () => http.get('/messages/conversations')
export const getChat = (userId, params) => http.get(`/messages/with/${userId}`, { params })
export const markChatRead = (userId) => http.put(`/messages/with/${userId}/read`)
export const getUnreadMessageCount = () => http.get('/messages/unread-count')

// 数据导出（返回 zip 二进制，用 fetch 直接下载，不走 axios 的 JSON 拦截器）
export async function adminExport() {
  const token = localStorage.getItem('token')
  const resp = await fetch('/api/admin/export', {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!resp.ok) throw new Error('导出失败')
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'blog-posts.zip'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export const adminUpdateSettings = (data) => http.put('/admin/settings', data)

// ===== 用户管理（封号 / 禁言）=====
export const adminGetUsers = () => http.get('/admin/users')
export const adminBanUser = (id, banned, reason) => http.put(`/admin/users/${id}/ban`, { banned, reason })
export const adminMuteUser = (id, minutes, reason) => http.put(`/admin/users/${id}/mute`, { minutes, reason })

// ===== 文章审核（先审后发）=====
export const adminAuditPost = (id, pass, remark) => http.post(`/admin/posts/${id}/audit`, { pass, remark })
