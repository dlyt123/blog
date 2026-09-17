import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi, getMe } from '@/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null
  }),
  getters: {
    isLogin: (state) => !!state.token,
    // 是否为管理员（普通用户/博主 role 为 USER）
    isAdmin: (state) => (state.userInfo?.role || '').toUpperCase() === 'ADMIN'
  },
  actions: {
    async login(username, password, extra = {}) {
      const data = await loginApi({ username, password, ...extra })
      this.token = data.token
      localStorage.setItem('token', data.token)
      return data
    },
    async register(payload) {
      const data = await registerApi(payload)
      this.token = data.token
      localStorage.setItem('token', data.token)
      return data
    },
    async fetchMe(options = {}) {
      if (!this.token) return null
      try {
        // 启动时用静默模式：token 失效就悄悄退成访客，不弹「登录已过期」也不跳转
        this.userInfo = await getMe({ skipAuthToast: true, ...options })
      } catch (e) {
        // token 已失效：清干净，按匿名访客继续浏览公开页面
        this.userInfo = null
        this.token = ''
        localStorage.removeItem('token')
      }
      return this.userInfo
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
    }
  }
})