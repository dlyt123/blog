import { defineStore } from 'pinia'
import { getSiteInfo } from '@/api'

/**
 * 站点信息 store：把「站点设置」里配置的内容真正用起来，
 * 避免 logo / 页脚 / 首页大标题写死。
 *
 * 用的是公开接口 /api/site-info（只含展示字段），
 * 这样即使在登录页、未登录状态下也能取到站点名和 Logo，且不会触发 401 提示。
 */
export const useSiteStore = defineStore('site', {
  state: () => ({
    info: {},
    loaded: false
  }),
  getters: {
    siteName: (s) => s.info.siteName || '我的博客',
    slogan: (s) => s.info.slogan || '记录代码与热爱',
    description: (s) => s.info.description || '',
    keywords: (s) => s.info.keywords || '',
    icp: (s) => s.info.icp || '',
    // 公安联网备案号（如「渝公网安备50000000000000号」）
    police: (s) => s.info.police || '',
    // 公安备案查询链接要用的纯数字编号（公安部平台只认编号，不认整串文字）
    policeCode: (s) => (s.info.police || '').replace(/\D/g, ''),
    // 公安部备案查询地址
    policeUrl: (s) => {
      const code = (s.info.police || '').replace(/\D/g, '')
      return code
        ? `https://beian.mps.gov.cn/#/query/webSearch?code=${code}`
        : 'https://beian.mps.gov.cn/'
    },
    logo: (s) => s.info.logo || '',
    // 文章页底部的版权声明文案（在「站点设置」里可改）
    copyright: (s) => s.info.copyright || ''
  },
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return this.info
      try {
        this.info = (await getSiteInfo()) || {}
      } catch (e) {
        this.info = {}
      }
      this.loaded = true
      if (this.siteName) {
        document.title = this.siteName
      }
      return this.info
    }
  }
})
