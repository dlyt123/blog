import { createApp } from 'vue'
import { createPinia } from 'pinia'

// ==================== Element Plus 按需引入 ====================
// 以前是 import ElementPlus from 'element-plus' + app.use(ElementPlus) + 全量 CSS，
// 结果把整个组件库（约 1MB JS + 376KB CSS）都打进包里，可项目只用到 15 个组件。
// 现在改为按需：模板里的 el-* 由 vite.config.js 的 ElementPlusResolver 自动引入。
//
// 下面这几个是「以函数方式调用 / 指令形式使用」的，模板里不出现，
// 所以必须手动引它们的样式，否则弹提示、确认框、加载动画会没有样式。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-loading.css'
// 暗色模式变量（配合 App.vue 里给 <html> 加的 .dark 类）
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'
import './styles/anime.css'

const app = createApp(App)

// 说明：曾经在这里把 @element-plus/icons-vue 的 ~300 个图标全部注册成全局组件，
// 但全项目一个都没用到（界面图标全用 emoji），白白多打包 200KB。已移除。
// 将来真要用某个图标，按需 import 单个即可，不要恢复全量注册。

app.use(createPinia())
app.use(router)
app.mount('#app')
