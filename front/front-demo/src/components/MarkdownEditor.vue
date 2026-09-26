<script setup>
/**
 * 自研 Markdown 编辑器（零第三方依赖）
 * - 工具栏：加粗 / 斜体 / 删除线 / 下划线 / 标题 / 引用 / 列表 / 代码 / 链接 / 图片 / 表格 / 分割线
 * - 左侧编辑、右侧实时预览（与文章详情页共用同一套 markdown-it 配置，所见即所得）
 * - 支持点击按钮插入图片、直接粘贴截图、把图片拖进编辑区
 * - 借用浏览器原生 execCommand('insertText')，所以撤销/重做（Ctrl+Z / Ctrl+Y）可用
 */
import { ref, computed, nextTick, watch } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '在这里写正文…' },
  /** 图片上传函数：(file) => Promise<{ url }> */
  uploadImage: { type: Function, default: null },
  minHeight: { type: String, default: '560px' }
})
const emit = defineEmits(['update:modelValue'])

// 预览与文章详情页用的是同一个渲染函数（含 DOMPurify 净化），所见即所得
const preview = computed(() => renderMarkdown(props.modelValue))

const textareaRef = ref(null)
const fileInputRef = ref(null)
const uploading = ref(false)
const mode = ref('split') // split | edit | preview

const text = computed(() => props.modelValue || '')
const charCount = computed(() => text.value.replace(/\s/g, '').length)
const lineCount = computed(() => (text.value ? text.value.split('\n').length : 0))

/* ============ 基础工具 ============ */

/** 用原生 insertText 替换选区：可参与浏览器撤销栈 */
function replaceRange(start, end, insertText) {
  const ta = textareaRef.value
  if (!ta) return
  ta.focus()
  ta.setSelectionRange(start, end)
  const ok = document.execCommand && document.execCommand('insertText', false, insertText)
  if (!ok) {
    // 兜底：直接改值，并把光标放到插入内容之后
    const val = props.modelValue || ''
    emit('update:modelValue', val.slice(0, start) + insertText + val.slice(end))
    nextTick(() => {
      const t = textareaRef.value
      if (!t) return
      const caret = start + insertText.length
      t.focus()
      t.setSelectionRange(caret, caret)
    })
  }
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
  checkCursorInTable()
}

/* ============ 缩进：Tab / Shift+Tab 与回车自动缩进 ============ */

const INDENT = '  ' // 一个缩进级别 = 2 个空格（软 Tab，显示更稳定）

function currentLineStart(value, pos) {
  return value.lastIndexOf('\n', pos - 1) + 1
}

/** 光标之前是否处于「未闭合」的代码块（``` 或 ~~~）内 */
function insideCodeFence(value, pos) {
  const fences = value.slice(0, pos).match(/^[ \t]*(?:```|~~~)/gm)
  return !!fences && fences.length % 2 === 1
}

/**
 * Tab / Shift+Tab
 * - 无选中或单行：在光标处插入 / 删除一个缩进级别
 * - 跨多行选中：整块缩进 / 反缩进
 */
function handleIndent(shift) {
  const ta = textareaRef.value
  if (!ta) return
  const val = props.modelValue || ''
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const lineStart = currentLineStart(val, start)
  // 选区结束所在行的行尾（单行选区就是本行行尾）
  let lineEnd = val.indexOf('\n', end)
  if (lineEnd === -1) lineEnd = val.length

  const block = val.slice(lineStart, lineEnd)
  const multiLine = block.includes('\n')

  // 单行 + 不按 Shift：直接插一个缩进（最常用）
  if (!multiLine && !shift) {
    replaceRange(start, end, INDENT)
    return
  }

  // 选区恰好以换行结尾时，不要给最后那个空行也加缩进
  const trailingNl = block.endsWith('\n')
  const core = trailingNl ? block.slice(0, -1) : block

  const out = core
    .split('\n')
    .map((line) => {
      if (shift) {
        if (line.startsWith('\t')) return line.slice(1)
        const m = line.match(/^ {1,2}/)
        return m ? line.slice(m[0].length) : line
      }
      return INDENT + line
    })
    .join('\n') + (trailingNl ? '\n' : '')

  replaceRange(lineStart, lineEnd, out)
  nextTick(() => {
    const t = textareaRef.value
    if (!t) return
    t.focus()
    t.setSelectionRange(lineStart, lineStart + out.length)
  })
}

/**
 * 回车自动缩进：按当前光标所在行的上下文决定新行的缩进
 * - 代码块内：沿用缩进；行尾是 { ( [ : 时再多缩进一级
 * - 列表：自动续上无序标记「- 」「* 」「+ 」；有序列表自动递增；空列表项回车则退出列表
 * - 引用：自动续上 >
 * - 普通行：沿用当前行缩进
 */
function handleEnter(e) {
  const ta = textareaRef.value
  if (!ta) return
  const val = props.modelValue || ''
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const lineStart = currentLineStart(val, start)
  const line = val.slice(lineStart, start)
  const lead = (line.match(/^[ \t]*/) || [''])[0]

  // 1) 代码块内
  if (insideCodeFence(val, start)) {
    let indent = lead
    if (/[{([:]\s*$/.test(line)) indent += INDENT
    if (indent) {
      e.preventDefault()
      replaceRange(start, end, '\n' + indent)
    }
    return
  }

  // 2) 列表
  const listMatch = line.match(/^([ \t]*)([-*+]|\d+\.)[ \t]+/)
  if (listMatch) {
    const listLead = listMatch[1]
    const marker = listMatch[2]
    e.preventDefault()
    if (line.trim() === marker) {
      // 空列表项回车 → 结束列表：把标记删掉，只留缩进
      replaceRange(lineStart + listLead.length, start, '\n' + listLead)
      return
    }
    const next = /^\d+\.$/.test(marker) ? `${parseInt(marker, 10) + 1}.` : marker
    replaceRange(start, end, `\n${listLead}${next} `)
    return
  }

  // 3) 引用
  const quoteMatch = line.match(/^([ \t]*)(>+[ \t]+)/)
  if (quoteMatch) {
    e.preventDefault()
    const qLead = quoteMatch[1]
    if (/^[ \t]*>[ \t]*$/.test(line)) {
      replaceRange(lineStart + qLead.length, start, '\n' + qLead)
      return
    }
    replaceRange(start, end, `\n${qLead}${quoteMatch[2]}`)
    return
  }

  // 4) 普通行：沿用当前行缩进
  if (lead) {
    e.preventDefault()
    replaceRange(start, end, '\n' + lead)
  }
}

function onKeydown(e) {
  if (e.key === 'Tab') {
    // 默认行为是切换焦点，必须拦掉才能用于缩进
    e.preventDefault()
    handleIndent(e.shiftKey)
    return
  }
  if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey) {
    handleEnter(e)
  }
}

/** 行内包裹，如 **加粗** */
function wrapInline(before, after, placeholder) {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const val = props.modelValue || ''
  const sel = val.slice(start, end)
  const inner = sel || placeholder
  replaceRange(start, end, before + inner + after)
  nextTick(() => {
    const t = textareaRef.value
    if (!t) return
    t.focus()
    t.setSelectionRange(start + before.length, start + before.length + inner.length)
  })
}

/** 给选中的每一行加前缀（用于标题 / 引用 / 列表） */
function prefixLines(prefixFn, placeholder) {
  const ta = textareaRef.value
  if (!ta) return
  const val = props.modelValue || ''
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const lineStart = val.lastIndexOf('\n', start - 1) + 1
  let lineEnd = val.indexOf('\n', end)
  if (lineEnd === -1) lineEnd = val.length
  const block = val.slice(lineStart, lineEnd)
  if (!block.trim()) {
    replaceRange(lineStart, lineEnd, prefixFn(0) + (placeholder || ''))
    return
  }
  const out = block.split('\n').map((l, i) => prefixFn(i) + l).join('\n')
  replaceRange(lineStart, lineEnd, out)
  nextTick(() => {
    const t = textareaRef.value
    if (!t) return
    t.focus()
    t.setSelectionRange(lineStart, lineStart + out.length)
  })
}

/* ============ 工具栏动作 ============ */

const actions = {
  bold: () => wrapInline('**', '**', '加粗文字'),
  italic: () => wrapInline('*', '*', '斜体文字'),
  strike: () => wrapInline('~~', '~~', '删除文字'),
  underline: () => wrapInline('<u>', '</u>', '下划线文字'),
  h1: () => prefixLines(() => '# ', '标题'),
  h2: () => prefixLines(() => '## ', '标题'),
  h3: () => prefixLines(() => '### ', '标题'),
  quote: () => prefixLines(() => '> ', '引用内容'),
  ul: () => prefixLines(() => '- ', '列表项'),
  ol: () => prefixLines((i) => `${i + 1}. `, '列表项'),
  code: () => wrapInline('`', '`', 'code'),
  codeBlock: () => {
    const ta = textareaRef.value
    if (!ta) return
    const val = props.modelValue || ''
    const start = ta.selectionStart
    const end = ta.selectionEnd
    const sel = val.slice(start, end) || '// 在这里输入代码'
    const needNl = start > 0 && val[start - 1] !== '\n'
    replaceRange(start, end, `${needNl ? '\n' : ''}\`\`\`\n${sel}\n\`\`\`\n`)
  },
  link: () => wrapInline('[', '](https://)', '链接文字'),
  image: () => fileInputRef.value && fileInputRef.value.click(),
  table: () => toggleTablePicker(),
  hr: () => {
    const ta = textareaRef.value
    if (!ta) return
    const val = props.modelValue || ''
    const start = ta.selectionStart
    const needNl = start > 0 && val[start - 1] !== '\n'
    replaceRange(start, ta.selectionEnd, `${needNl ? '\n' : ''}---\n`)
  }
}

const toolbarGroups = [
  [
    { key: 'bold', label: 'B', title: '加粗', cls: 'fw' },
    { key: 'italic', label: 'I', title: '斜体', cls: 'it' },
    { key: 'strike', label: 'S', title: '删除线', cls: 'st' },
    { key: 'underline', label: 'U', title: '下划线', cls: 'ul' }
  ],
  [
    { key: 'h1', label: 'H1', title: '一级标题' },
    { key: 'h2', label: 'H2', title: '二级标题' },
    { key: 'h3', label: 'H3', title: '三级标题' }
  ],
  [
    { key: 'quote', label: '❝', title: '引用' },
    { key: 'ul', label: '•', title: '无序列表' },
    { key: 'ol', label: '1.', title: '有序列表' }
  ],
  [
    { key: 'code', label: '</>', title: '行内代码' },
    { key: 'codeBlock', label: '{ }', title: '代码块' }
  ],
  [
    { key: 'link', label: '🔗', title: '插入链接' },
    { key: 'image', label: '🖼', title: '插入图片' },
    { key: 'table', label: '▦', title: '插入表格' },
    { key: 'hr', label: '—', title: '分割线' }
  ]
]

function run(key) {
  const fn = actions[key]
  if (fn) fn()
}

/* ============ 表格：自选「行 × 列」 ============ */

const MAX_TABLE_ROWS = 8
const MAX_TABLE_COLS = 8
const showTablePicker = ref(false)
const hoverRow = ref(2)
const hoverCol = ref(2)

function toggleTablePicker() {
  showTablePicker.value = !showTablePicker.value
  if (showTablePicker.value) {
    hoverRow.value = 2
    hoverCol.value = 2
  }
}

function setHover(r, c) {
  hoverRow.value = r
  hoverCol.value = c
}

function closeTablePicker() {
  showTablePicker.value = false
}

/** 按选定的行数 / 列数插入 Markdown 表格（含表头） */
function insertTable(rows, cols) {
  showTablePicker.value = false
  const ta = textareaRef.value
  if (!ta) return
  const val = props.modelValue || ''
  const start = ta.selectionStart
  const needNl = start > 0 && val[start - 1] !== '\n'

  const cols_ = Math.max(1, cols)
  const header = '| ' + Array.from({ length: cols_ }, (_, i) => `表头${i + 1}`).join(' | ') + ' |'
  const sep = '| ' + Array.from({ length: cols_ }, () => '---').join(' | ') + ' |'
  const body = Array.from({ length: Math.max(0, rows - 1) }, () =>
    '| ' + Array.from({ length: cols_ }, () => '内容').join(' | ') + ' |'
  )
  const tpl = [header, sep, ...body].join('\n') + '\n'
  replaceRange(start, ta.selectionEnd, `${needNl ? '\n' : ''}${tpl}`)

  // 插入后自动选中第一个「表头1」，直接打字即可覆盖
  nextTick(() => {
    const t = textareaRef.value
    if (!t) return
    const firstCell = start + (needNl ? 1 : 0) + 2 // 跳过 "| "
    t.focus()
    t.setSelectionRange(firstCell, firstCell + 3)
    checkCursorInTable()
  })
}

/* ============ 光标位于表格内时的「表格工具条」 ============ */

const inTable = ref(false)
const tableRows = ref(0)
const tableCols = ref(0)

/** 把一行 Markdown 表格拆成单元格数组 */
function parseRow(line) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((s) => s.trim())
}

function buildRow(cells) {
  return '| ' + cells.join(' | ') + ' |'
}

/** 找出光标所在的整块表格（连续的以 | 开头的行） */
function findTableBlock() {
  const ta = textareaRef.value
  if (!ta) return null
  const val = props.modelValue || ''
  const pos = ta.selectionStart

  let lineStart = val.lastIndexOf('\n', pos - 1) + 1
  let lineEnd = val.indexOf('\n', pos)
  if (lineEnd === -1) lineEnd = val.length
  const currentLine = val.slice(lineStart, lineEnd)
  if (!/^\s*\|/.test(currentLine)) return null

  // 向上扩展
  let start = lineStart
  while (start > 0) {
    const prevEnd = start - 1
    const prevStart = val.lastIndexOf('\n', prevEnd - 1) + 1
    if (/^\s*\|/.test(val.slice(prevStart, prevEnd))) start = prevStart
    else break
  }
  // 向下扩展
  let end = lineEnd
  while (end < val.length) {
    const nextStart = end + 1
    let nextEnd = val.indexOf('\n', nextStart)
    if (nextEnd === -1) nextEnd = val.length
    if (/^\s*\|/.test(val.slice(nextStart, nextEnd))) end = nextEnd
    else break
  }

  const lines = val.slice(start, end).split('\n')
  const cursorRow = val.slice(start, lineStart).split('\n').length - 1 // 0-based
  return { start, end, lines, cursorRow }
}

/** 光标移动 / 输入后重新判断是否在表格内 */
function checkCursorInTable() {
  const block = findTableBlock()
  if (!block) {
    inTable.value = false
    return
  }
  inTable.value = true
  tableRows.value = block.lines.length
  tableCols.value = block.lines.length ? parseRow(block.lines[0]).length : 0
}

function applyTable(lines) {
  const block = findTableBlock()
  if (!block) return
  const text = lines.join('\n')
  replaceRange(block.start, block.end, text)
  nextTick(checkCursorInTable)
}

/** 加一列（第 0 行补表头、第 1 行补 ---、其余补空单元格） */
function tableAddCol() {
  const block = findTableBlock()
  if (!block) return
  const newIndex = parseRow(block.lines[0]).length + 1
  const lines = block.lines.map((line, i) => {
    const cells = parseRow(line)
    cells.push(i === 0 ? `表头${newIndex}` : i === 1 ? '---' : '')
    return buildRow(cells)
  })
  applyTable(lines)
}

/** 删最后一列（至少保留 1 列） */
function tableDelCol() {
  const block = findTableBlock()
  if (!block) return
  if (parseRow(block.lines[0]).length <= 1) {
    ElMessage.warning('至少保留一列')
    return
  }
  const lines = block.lines.map((line) => {
    const cells = parseRow(line)
    cells.pop()
    return buildRow(cells)
  })
  applyTable(lines)
}

/** 在当前行下方加一行 */
function tableAddRow() {
  const block = findTableBlock()
  if (!block) return
  const cols = parseRow(block.lines[0]).length
  const emptyRow = buildRow(Array.from({ length: cols }, () => ''))
  const lines = [...block.lines]
  const insertAt = Math.max(2, block.cursorRow + 1) // 不能插到表头/分隔行之前
  lines.splice(insertAt, 0, emptyRow)
  applyTable(lines)
}

/** 删除光标所在行（表头与分隔行不可删） */
function tableDelRow() {
  const block = findTableBlock()
  if (!block) return
  if (block.cursorRow < 2) {
    ElMessage.warning('表头行和分隔行不能删除')
    return
  }
  if (block.lines.length <= 3) {
    ElMessage.warning('至少保留一行数据')
    return
  }
  const lines = [...block.lines]
  lines.splice(block.cursorRow, 1)
  applyTable(lines)
}

/** 删除整张表格 */
function tableRemove() {
  const block = findTableBlock()
  if (!block) return
  let end = block.end
  if (end < (props.modelValue || '').length) end += 1 // 连同换行一起删
  replaceRange(block.start, end, '')
  inTable.value = false
}

/* ============ 图片上传（按钮 / 粘贴 / 拖拽） ============ */

const ALLOW_MAX = 5 * 1024 * 1024

function accepted(file) {
  if (!file) return false
  if (!file.type.startsWith('image/')) {
    ElMessage.warning(`「${file.name || '文件'}」不是图片`)
    return false
  }
  if (file.size > ALLOW_MAX) {
    ElMessage.warning(`「${file.name || '图片'}」超过 5MB`)
    return false
  }
  return true
}

async function insertImages(files) {
  if (!props.uploadImage) {
    ElMessage.warning('未配置图片上传')
    return
  }
  uploading.value = true
  try {
    for (const file of files) {
      if (!accepted(file)) continue
      const res = await props.uploadImage(file)
      if (res && res.url) {
        const ta = textareaRef.value
        const val = props.modelValue || ''
        const start = ta ? ta.selectionStart : val.length
        const needNl = start > 0 && val[start - 1] !== '\n'
        replaceRange(start, ta ? ta.selectionEnd : start, `${needNl ? '\n' : ''}![](${res.url})\n`)
      }
    }
  } catch (e) {
    // 上传失败时拦截器已提示
  } finally {
    uploading.value = false
  }
}

function pickImage() {
  fileInputRef.value && fileInputRef.value.click()
}

function onFileChange(e) {
  const files = Array.from(e.target.files || [])
  insertImages(files)
  e.target.value = ''
}

function onPaste(e) {
  const items = e.clipboardData && e.clipboardData.items
  if (!items) return
  const imgs = []
  for (const it of items) {
    if (it.type && it.type.startsWith('image/')) {
      const f = it.getAsFile()
      if (f) imgs.push(f)
    }
  }
  if (imgs.length) {
    e.preventDefault()
    insertImages(imgs)
  }
}

function onDrop(e) {
  const files = Array.from((e.dataTransfer && e.dataTransfer.files) || [])
  const imgs = files.filter((f) => f.type && f.type.startsWith('image/'))
  if (imgs.length) {
    e.preventDefault()
    insertImages(imgs)
  }
}

/* ============ 编辑区 / 预览区 滚动同步 ============ */

const previewRef = ref(null)
let syncing = false

function onScroll() {
  if (syncing || mode.value !== 'split') return
  const ta = textareaRef.value
  const pv = previewRef.value
  if (!ta || !pv) return
  const ratio = ta.scrollTop / Math.max(1, ta.scrollHeight - ta.clientHeight)
  syncing = true
  pv.scrollTop = ratio * Math.max(0, pv.scrollHeight - pv.clientHeight)
  requestAnimationFrame(() => { syncing = false })
}

function onPreviewScroll() {
  if (syncing || mode.value !== 'split') return
  const ta = textareaRef.value
  const pv = previewRef.value
  if (!ta || !pv) return
  const ratio = pv.scrollTop / Math.max(1, pv.scrollHeight - pv.clientHeight)
  syncing = true
  ta.scrollTop = ratio * Math.max(0, ta.scrollHeight - ta.clientHeight)
  requestAnimationFrame(() => { syncing = false })
}

/* ============ 对外方法 ============ */

function focus() {
  textareaRef.value && textareaRef.value.focus()
}

watch(
  () => props.modelValue,
  () => {
    // 父组件切换文章时，把编辑区滚回顶部
    nextTick(() => {
      if (textareaRef.value) textareaRef.value.scrollTop = 0
    })
  }
)

defineExpose({ focus })
</script>

<template>
  <div class="md-editor">
    <!-- 工具栏 -->
    <div class="md-toolbar">
      <template v-for="(group, gi) in toolbarGroups" :key="gi">
        <span v-if="gi > 0" class="md-divider" />
        <template v-for="btn in group" :key="btn.key">
          <!-- 表格按钮：点开「行 × 列」选择面板，可自由指定表格大小 -->
          <div v-if="btn.key === 'table'" class="md-tool-wrap">
            <button
              type="button"
              class="md-tool"
              :class="{ on: showTablePicker }"
              :title="btn.title"
              @mousedown.prevent
              @click="run('table')"
            >{{ btn.label }}</button>

            <div v-if="showTablePicker" class="md-table-picker" @mousedown.prevent>
              <div class="md-picker-grid">
                <div v-for="r in MAX_TABLE_ROWS" :key="r" class="md-picker-row">
                  <span
                    v-for="c in MAX_TABLE_COLS"
                    :key="c"
                    class="md-picker-cell"
                    :class="{ on: r <= hoverRow && c <= hoverCol }"
                    @mouseenter="setHover(r, c)"
                    @click="insertTable(r, c)"
                  />
                </div>
              </div>
              <div class="md-picker-tip">{{ hoverRow }} 行 × {{ hoverCol }} 列</div>
              <div class="md-picker-hint">第一行为表头，点击即可插入</div>
            </div>
          </div>

          <button
            v-else
            type="button"
            class="md-tool"
            :class="btn.cls"
            :title="btn.title"
            :disabled="uploading && btn.key === 'image'"
            @mousedown.prevent
            @click="run(btn.key)"
          >{{ btn.key === 'image' && uploading ? '…' : btn.label }}</button>
        </template>
      </template>

      <span class="md-spacer" />

      <button
        v-for="m in [['edit', '编辑'], ['split', '分屏'], ['preview', '预览']]"
        :key="m[0]"
        type="button"
        class="md-tool mode"
        :class="{ on: mode === m[0] }"
        @mousedown.prevent
        @click="mode = m[0]"
      >
        {{ m[1] }}
      </button>
    </div>

    <!-- 光标在表格内时出现：加/删 行、加/删 列 -->
    <div v-if="inTable && mode !== 'preview'" class="md-table-toolbar">
      <span class="md-tt-label">表格 {{ tableRows }} 行 × {{ tableCols }} 列</span>
      <button type="button" class="md-tt-btn" @mousedown.prevent @click="tableAddRow">＋ 行</button>
      <button type="button" class="md-tt-btn" @mousedown.prevent @click="tableDelRow">－ 行</button>
      <button type="button" class="md-tt-btn" @mousedown.prevent @click="tableAddCol">＋ 列</button>
      <button type="button" class="md-tt-btn" @mousedown.prevent @click="tableDelCol">－ 列</button>
      <span class="md-tt-spacer" />
      <button type="button" class="md-tt-btn danger" @mousedown.prevent @click="tableRemove">删除表格</button>
    </div>

    <!-- 编辑 + 预览 -->
    <div class="md-body" :class="`mode-${mode}`" :style="{ minHeight }" @click="closeTablePicker">
      <textarea
        v-show="mode !== 'preview'"
        ref="textareaRef"
        class="md-input"
        :value="modelValue"
        :placeholder="placeholder"
        spellcheck="false"
        @input="onInput"
        @scroll="onScroll"
        @paste="onPaste"
        @drop="onDrop"
        @keydown="onKeydown"
        @click="checkCursorInTable"
        @keyup="checkCursorInTable"
        @select="checkCursorInTable"
        @dragover.prevent
      />
      <div
        v-show="mode !== 'edit'"
        ref="previewRef"
        class="md-preview markdown-body"
        @scroll="onPreviewScroll"
      >
        <div v-if="!modelValue" class="md-preview-empty">预览区（左侧输入内容后会实时显示）</div>
        <div v-else v-html="preview" />
      </div>
    </div>

    <!-- 状态栏 -->
    <div class="md-footer">
      <span>{{ charCount }} 字</span>
      <span>{{ lineCount }} 行</span>
      <span class="md-tip">Tab 缩进 · Shift+Tab 反缩进 · 回车自动续排 · 粘贴 / 拖拽可插图 · Ctrl+Z 撤销</span>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      accept="image/*"
      multiple
      class="md-hidden-file"
      @change="onFileChange"
    />
  </div>
</template>

<style scoped>
.md-editor {
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface);
}

/* 工具栏 */
.md-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--border-soft);
  flex-wrap: wrap;
}

.md-tool {
  min-width: 30px;
  height: 28px;
  padding: 0 7px;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-body);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out),
              color var(--dur-fast) var(--ease-out);
}

.md-tool:hover:not(:disabled) {
  background: var(--surface-pink);
  color: var(--brand-700);
}

.md-tool:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.md-tool.fw { font-weight: 800; }
.md-tool.it { font-style: italic; font-family: Georgia, serif; }
.md-tool.st { text-decoration: line-through; }
.md-tool.ul { text-decoration: underline; }

.md-divider {
  width: 1px;
  height: 16px;
  background: var(--border-soft);
  margin: 0 5px;
}

.md-spacer {
  flex: 1;
}

.md-tool.mode {
  font-size: var(--text-xs);
  padding: 0 10px;
}

/* 当前生效的格式 / 视图模式。
   --brand-100 在暗色下会翻转成深玫红，浅色和暗色都能和工具栏底色区分开。 */
.md-tool.mode.on {
  background: var(--brand-100);
  color: var(--brand-700);
  font-weight: 600;
}

.md-tool.on {
  background: var(--brand-100);
  color: var(--brand-700);
}

/* 表格「行 × 列」选择面板 */
.md-tool-wrap {
  position: relative;
  display: inline-flex;
}

.md-table-picker {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: var(--z-dropdown);
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 10px;
  user-select: none;
}

.md-picker-grid {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.md-picker-row {
  display: flex;
  gap: 2px;
}

.md-picker-cell {
  width: 16px;
  height: 16px;
  border: 1px solid var(--border-soft);
  border-radius: 2px;
  background: var(--surface-soft);
  cursor: pointer;
}

/* 选中格用实心品牌色：这个 16px 的小方块必须一眼可辨，
   用深色边框在暗色模式下会糊成一片。 */
.md-picker-cell.on {
  background: var(--brand-500);
  border-color: var(--brand-400);
}

.md-picker-tip {
  margin-top: var(--space-2);
  text-align: center;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--brand-700);
}

.md-picker-hint {
  margin-top: 2px;
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
}

/* 光标在表格内时的工具条 */
.md-table-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--border-soft);
}

.md-tt-label {
  font-size: var(--text-xs);
  color: var(--brand-700);
  font-weight: 600;
  margin-right: 4px;
}

.md-tt-spacer {
  flex: 1;
}

.md-tt-btn {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--border-brand);
  border-radius: var(--radius-xs);
  background: var(--surface);
  color: var(--brand-700);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out),
              border-color var(--dur-fast) var(--ease-out);
}

.md-tt-btn:hover {
  background: var(--surface-pink);
}

.md-tt-btn.danger {
  border-color: rgba(212, 71, 92, 0.3);
  color: var(--danger);
}

.md-tt-btn.danger:hover {
  background: var(--danger-soft);
}

/* 主体 */
.md-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.md-body.mode-edit,
.md-body.mode-preview {
  grid-template-columns: 1fr;
}

.md-input {
  width: 100%;
  min-height: inherit;
  padding: var(--space-4);
  border: none;
  outline: none;
  resize: none;
  font-family: var(--font-mono);
  font-size: var(--text-base);
  line-height: 1.7;
  color: var(--text-strong);
  background: var(--surface);
  box-sizing: border-box;
}

.md-body.mode-split .md-input {
  border-right: 1px solid var(--border-soft);
}

.md-preview {
  min-height: inherit;
  padding: var(--space-4);
  /* 纵向跟随编辑区；横向用于宽表格滚动 */
  overflow: auto;
  background: var(--surface);
  box-sizing: border-box;
}

.md-preview-empty {
  color: var(--text-faint);
  font-size: var(--text-sm);
}

/* 状态栏 */
.md-footer {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  padding: 6px var(--space-3);
  background: var(--surface-soft);
  border-top: 1px solid var(--border-soft);
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.md-tip {
  margin-left: auto;
}

.md-hidden-file {
  display: none;
}

/* 预览区内容样式（复用全局 .markdown-body，这里只对齐代码块与表格）。
   代码块底色是全站统一的深色面，两种主题下都保持高对比 —— 和正文页一致。 */
.md-preview :deep(pre) {
  background: #2d2a3a;
  color: #e8e8f0;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  overflow-x: auto;
  box-shadow: var(--shadow-md);
}

.md-preview :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.9em;
}

.md-preview :deep(pre code) {
  background: transparent;
  padding: 0;
}

.md-preview :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

/* 列数多时给单元格保底宽度，超出部分由预览区横向滚动 */
.md-preview :deep(th),
.md-preview :deep(td) {
  border: 1px solid var(--border-soft);
  padding: var(--space-2) var(--space-3);
  min-width: 80px;
}

.md-preview :deep(th) {
  background: var(--brand-100);
}

.md-preview :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-sm);
}

@media (max-width: 900px) {
  .md-body.mode-split {
    grid-template-columns: 1fr;
  }
  .md-body.mode-split .md-input {
    border-right: none;
    border-bottom: 1px solid var(--border-soft);
  }
}
</style>
