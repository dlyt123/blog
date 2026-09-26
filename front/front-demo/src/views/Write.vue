<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import { getPost, adminCreatePost, adminUpdatePost, getCategories, getTags, getSeries, uploadMedia } from '@/api'
import { renderMarkdown } from '@/utils/markdown'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

// ===== 草稿自动保存（仅新建时，编辑已有文章不覆盖草稿）=====
const DRAFT_KEY = 'blog-draft'
let draftTimer = null

function restoreDraft() {
  if (isEdit.value) return
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const d = JSON.parse(raw)
    if (d && (d.title || d.content)) {
      form.value = { ...form.value, ...d }
      ElMessage.info('已恢复上次未发布的草稿')
    }
  } catch (e) {}
}

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
}

const form = ref({
  title: '',
  summary: '',
  cover: '',
  categoryId: null,
  seriesId: null,
  tagIds: [],
  status: 1,          // 默认发布
  content: '',
  scheduledAt: null   // 定时发布（可选）
})
const categories = ref([])
const tags = ref([])
const seriesList = ref([])
const saving = ref(false)
const uploadingCover = ref(false)
const coverInput = ref(null)
const editorRef = ref(null)

// ===== 预览：不保存，直接看渲染效果 =====
const previewVisible = ref(false)
const previewHtml = computed(() => renderMarkdown(form.value.content || ''))

const editId = computed(() => route.query.id || null)
const isEdit = computed(() => !!editId.value)

// ⚠️ 必须放在 form / isEdit 声明「之后」：
//    这里的第一句 watch(form, ...) 会立即读取变量 form，
//    如果放在 form 声明之前，整个组件会在 setup 阶段抛
//    ReferenceError: Cannot access 'form' before initialization，
//    表现就是「写文章页整个白屏」（已踩过）。
watch(form, () => {
  if (isEdit.value) return
  clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    try {
      if (form.value.title.trim() || form.value.content.trim()) {
        localStorage.setItem(DRAFT_KEY, JSON.stringify(form.value))
      }
    } catch (e) {}
  }, 800)
}, { deep: true })

onMounted(async () => {
  categories.value = await getCategories()
  tags.value = await getTags()
  seriesList.value = (await getSeries().catch(() => [])) || []
  if (isEdit.value) {
    const post = await getPost(editId.value)
    form.value = {
      title: post.title,
      summary: post.summary || '',
      cover: post.cover || '',
      categoryId: post.categoryId,
      seriesId: post.seriesId,
      tagIds: post.tagIds || [],
      status: post.status,
      content: post.content || '',
      scheduledAt: post.scheduledAt || null
    }
    nextTick(() => editorRef.value && editorRef.value.focus())
  } else {
    restoreDraft()
  }
})

/** 供编辑器调用：上传一张图片，返回 { url } */
function uploadImage(file) {
  return uploadMedia(file)
}

/** 封面：选择本地图片上传（不用手输地址） */
function pickCover() {
  coverInput.value && coverInput.value.click()
}

async function onCoverChange(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件（jpg / png / gif / webp）')
    e.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    e.target.value = ''
    return
  }
  uploadingCover.value = true
  try {
    const res = await uploadMedia(file)
    form.value.cover = res.url
    ElMessage.success('封面已上传')
  } catch (err) {
    // 拦截器已提示
  } finally {
    uploadingCover.value = false
    e.target.value = ''
  }
}

function clearCover() {
  form.value.cover = ''
}

/** 未填摘要时，从正文自动截取一段作为摘要 */
function autoSummary() {
  if (form.value.summary && form.value.summary.trim()) return form.value.summary.trim()
  const plain = (form.value.content || '')
    .replace(/```[\s\S]*?```/g, ' ')          // 代码块
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')    // 图片
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')  // 链接保留文字
    .replace(/[#>*`~\-]+/g, ' ')              // 标记符号
    .replace(/\s+/g, ' ')
    .trim()
  return plain.slice(0, 120)
}

async function save(publish) {
  if (!form.value.title || !form.value.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!form.value.content || !form.value.content.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      title: form.value.title.trim(),
      summary: autoSummary(),
      // 定时发布：保存为草稿，到点由后端自动发布；否则按「发布/存草稿」处理
      status: form.value.scheduledAt ? 0 : (publish ? 1 : 0)
    }
    if (isEdit.value) {
      await adminUpdatePost(editId.value, payload)
      ElMessage.success(form.value.scheduledAt ? '已设为定时发布' : '文章已更新')
    } else {
      await adminCreatePost(payload)
      ElMessage.success(form.value.scheduledAt ? '已设为定时发布，到点自动上线' : (publish ? '文章已发布' : '草稿已保存'))
      clearDraft()
    }
    router.push('/admin/posts')
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.back()
}
</script>

<template>
  <div class="write-wrap">
    <div class="anime-card write-card">
      <h2 class="write-title">{{ isEdit ? '编辑文章' : '写文章' }}</h2>

      <div class="field">
        <label>标题 <span class="req">*</span></label>
        <input v-model="form.title" class="anime-input" placeholder="给文章起个标题" />
      </div>

      <div class="field-row">
        <div class="field">
          <label>分类</label>
          <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </div>
        <div class="field">
          <label>所属系列（可选）</label>
          <el-select v-model="form.seriesId" placeholder="选择系列 / 专栏" clearable style="width: 100%">
            <el-option v-for="s in seriesList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </div>
      </div>

      <div class="field-row">
        <div class="field">
          <label>标签</label>
          <el-select v-model="form.tagIds" multiple placeholder="选择标签（可多选）" style="width: 100%">
            <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </div>
      </div>

      <!-- 封面：选择本地图片上传 -->
      <div class="field">
        <label>封面图</label>
        <div class="cover-row">
          <div class="cover-preview">
            <img v-if="form.cover" :src="form.cover" alt="封面预览" />
            <span v-else class="cover-placeholder">暂无封面</span>
          </div>
          <div class="cover-actions">
            <input
              ref="coverInput"
              type="file"
              accept="image/*"
              class="hidden-file"
              @change="onCoverChange"
            />
            <button class="anime-btn anime-btn--secondary" :disabled="uploadingCover" @click="pickCover">
              {{ uploadingCover ? '上传中...' : (form.cover ? '重新选择' : '选择封面图片') }}
            </button>
            <button v-if="form.cover" class="anime-btn anime-btn--ghost" @click="clearCover">移除封面</button>
            <p class="cover-hint">支持 jpg / png / gif / webp，单张不超过 5MB</p>
          </div>
        </div>
      </div>

      <div class="field">
        <label>摘要</label>
        <textarea
          v-model="form.summary"
          class="anime-input textarea"
          rows="2"
          placeholder="一句话概括文章内容（留空则自动从正文提取）"
        />
      </div>

      <div class="field">
        <label>正文 <span class="hint">工具栏可插入图片 / 表格等，也可直接粘贴或拖拽图片</span></label>
        <div class="editor-wrap">
          <MarkdownEditor
            ref="editorRef"
            v-model="form.content"
            :upload-image="uploadImage"
            placeholder="在这里写正文，支持 Markdown 语法…"
            min-height="560px"
          />
        </div>
      </div>

      <div class="actions">
        <div class="schedule-box">
          <el-date-picker
            v-model="form.scheduledAt"
            type="datetime"
            placeholder="定时发布（可选，到点自动上线）"
            :disabled-date="(d) => d < Date.now() - 86400000"
            clearable
          />
        </div>
        <button class="anime-btn anime-btn--primary" :disabled="saving" @click="save(true)">
          {{ saving ? '提交中...' : (form.scheduledAt ? '设定时发布' : (isEdit ? '更新并发布' : '发布文章')) }}
        </button>
        <button class="anime-btn anime-btn--secondary" :disabled="saving" @click="save(false)">存为草稿</button>
        <button class="anime-btn anime-btn--ghost" type="button" @click="previewVisible = true">👁 预览</button>
        <button class="anime-btn anime-btn--ghost" :disabled="saving" @click="cancel">取消</button>
      </div>
    </div>

    <!-- 预览弹窗：和前台文章页一样的渲染效果 -->
    <el-dialog v-model="previewVisible" title="文章预览（不会保存）" width="820px" top="5vh">
      <div class="preview-wrap">
        <h1 class="preview-title">{{ form.title || '（未填标题）' }}</h1>
        <div class="markdown-body" v-html="previewHtml"></div>
        <p v-if="!form.content || !form.content.trim()" class="preview-empty">正文还是空的</p>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.write-wrap {
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-5);
}

.write-card {
  padding: var(--space-8);
}

.write-title {
  color: var(--brand-700);
  margin: 0 0 var(--space-5);
  font-size: var(--text-2xl);
  font-weight: 700;
  line-height: 1.35;
}

.field {
  margin-bottom: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-body);
}

/* "必填"是状态信息，不是品牌装饰，用语义危险色 */
.field .req {
  color: var(--danger);
}

.field .hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
  font-weight: 400;
  margin-left: 6px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

/* 输入框外观统一由全局 .anime-input 提供。
   这里以前又整块重写了一遍（圆角/边框/聚焦都一样，只有 padding 不同），
   因为 scoped 特异性 +1 会盖住全局规则，于是本页的输入框和别处不一样。已删除。 */

/* 封面 */
.cover-row {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}

.cover-preview {
  width: 200px;
  height: 120px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border-soft);
  background: var(--surface-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  color: var(--text-faint);
  font-size: var(--text-sm);
}

.cover-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: flex-start;
}

.cover-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

/* ===== 手机端：封面预览与按钮改成上下排 =====
   横排时左边固定 200px 的预览图把右侧挤到只剩几十像素，
   「选择封面图片」按钮的文字会被压成「一列一个字」。 */
@media (max-width: 640px) {
  .cover-row {
    flex-direction: column;
    gap: var(--space-3);
  }

  .cover-preview {
    width: 100%;
    height: 160px;
  }

  .cover-actions {
    width: 100%;
    align-items: stretch;
  }

  .cover-actions .anime-btn {
    width: 100%;
  }
}

.hidden-file {
  display: none;
}

/* 编辑器容器 */
.editor-wrap {
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-5);
  align-items: center;
  flex-wrap: wrap;
}

.schedule-box {
  flex: 1;
  min-width: 200px;
}

/* ===== 预览弹窗 ===== */
.preview-wrap {
  max-height: 68vh;
  overflow-y: auto;
  padding: 4px 8px 8px;
}

.preview-title {
  margin: 0 0 var(--space-4);
  font-size: var(--text-2xl);
  color: var(--text-strong);
  text-align: center;
  line-height: 1.5;
}

.preview-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-sm);
  padding: var(--space-6) 0;
}

/* 按钮外观统一由全局的 .anime-btn 提供（src/styles/anime.css），本页不再自己实现。 */

@media (max-width: 720px) {
  .field-row {
    grid-template-columns: 1fr;
  }
}
</style>