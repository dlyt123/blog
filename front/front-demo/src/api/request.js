import axios from 'axios'
import { ElMessage } from 'element-plus'

// Axios 统一封装
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/**
 * 把后端偏技术的提示，翻译成对用户更友好的文案。
 * 未列出的会原样展示；以某个前缀开头的走 prefix 规则。
 */
const FRIENDLY_MESSAGES = {
  // 登录 / 鉴权
  '未登录或 Token 缺失': '请先登录后再操作',
  'Token 无效或已过期': '登录已过期，请重新登录',
  未登录: '请先登录后再操作',
  用户不存在请重新登录: '账号状态异常，请重新登录',
  用户名或密码错误: '用户名或密码不正确，请检查后重试',
  用户名和密码不能为空: '请输入用户名和密码',
  用户名已被占用: '这个用户名已被注册，换一个试试',

  // 权限
  仅管理员可执行此操作: '该操作仅管理员可用',
  仅管理员可访问此功能: '该功能仅管理员可用',
  无权限操作他人文章: '只能操作自己发布的文章哦',

  // 内容
  文章不存在: '这篇文章不存在或已被删除',

  // 互动
  请先登录后再点赞: '登录后才能点赞哦',
  请先登录后再收藏: '登录后才能收藏哦',
  请先登录后再评论: '登录后才能发表评论哦',

  // 其它
  'ids 不能为空': '请先选择要操作的内容',
  未知的批量操作: '暂不支持该操作',
  邮箱格式不正确: '请输入正确的邮箱地址'
}

/** 前缀匹配规则（后端把细节拼在冒号后面的情况） */
const FRIENDLY_PREFIXES = [
  { prefix: '服务器内部错误', text: '服务器开小差了，请稍后重试' },
  { prefix: '上传失败', text: '图片上传失败，请稍后重试' }
]

function friendly(message, fallback = '操作失败，请稍后重试') {
  if (!message) return fallback
  if (FRIENDLY_MESSAGES[message]) return FRIENDLY_MESSAGES[message]
  const hit = FRIENDLY_PREFIXES.find((r) => message.startsWith(r.prefix))
  return hit ? hit.text : message
}

/** 统一弹提示：相同文案自动合并，避免短时间内刷屏 */
function toast(message, type = 'error') {
  ElMessage({
    message,
    type,
    duration: 2600,
    grouping: true,
    showClose: false
  })
}

// 请求拦截器：注入 Token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 处理登录态失效：
 * - 静默探测（config.skipAuthToast，例如启动时校验 token）→ 只清 token，不提示不跳转，
 *   让用户以匿名访客身份继续浏览公开页面
 * - 之前已登录（本地有 token）→ 提示"登录已过期"，这是用户需要知道的信息
 * - 本来就没登录 → 静默跳登录页，不打扰用户
 */
function handleUnauthorized(config) {
  const hadToken = !!localStorage.getItem('token')
  localStorage.removeItem('token')

  if (config && config.skipAuthToast) {
    return
  }
  if (hadToken) {
    toast('登录已过期，请重新登录', 'warning')
  }
  if (location.pathname !== '/login') {
    location.href = '/login'
  }
}

// 响应拦截器：统一处理 code
http.interceptors.response.use(
  (res) => {
    const data = res.data
    // 业务码 401：登录态失效
    if (data.code === 401) {
      handleUnauthorized(res.config)
      return Promise.reject(data)
    }
    if (data.code !== 200) {
      toast(friendly(data.message))
      return Promise.reject(data)
    }
    return data.data
  },
  (error) => {
    // HTTP 401 同样按登录态失效处理
    if (error.response && error.response.status === 401) {
      handleUnauthorized(error.config)
      return Promise.reject(error)
    }

    let msg
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时了，请稍后重试'
    } else if (error.response) {
      msg = friendly(error.response.data && error.response.data.message)
    } else {
      msg = '网络异常，请检查网络后重试'
    }
    toast(msg)
    return Promise.reject(error)
  }
)

export default http
