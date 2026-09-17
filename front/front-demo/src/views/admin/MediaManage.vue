<script setup>
import { ref, onMounted, computed } from 'vue'
import { adminGetMedia, adminDeleteMedia, uploadMedia } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const loading = ref(false)
const uploading = ref(false)
const fileInput = ref(null)
const keyword = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = (await adminGetMedia()) || []
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((m) => (m.filename || '').toLowerCase().includes(k))
})

/** 图片大小格式化 */
function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

/** 复制图片完整地址，方便粘到文章里 */
async function copyUrl(item) {
  const full = location.origin + item.url
  try {
    await navigator.clipboard.writeText(full)
    ElMessage.success('图片地址已复制')
  } catch (e) {
    // 部分浏览器 / 非 HTTPS 环境不支持剪贴板 API，降级为手动复制提示
    ElMessageBox.alert(full, '请手动复制图片地址', { confirmButtonText: '知道了' })
  }
}

/** 复制 Markdown 图片语法 */
async function copyMarkdown(item) {
  const md = `![${item.filename || '图片'}](${item.url})`
  try {
    await navigator.clipboard.writeText(md)
    ElMessage.success('已复制 Markdown 图片语法')
  } catch (e) {
    ElMessageBox.alert(md, '请手动复制', { confirmButtonText: '知道了' })
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(
      '删除后，引用这张图片的文章里会显示空白，确定删除吗？',
      '删除图片',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  await adminDeleteMedia(item.id)
  ElMessage.success('已删除')
  load()
}

function pickFile() {
  fileInput.value && fileInput.value.click()
}

async function onFileChange(e) {
  const files = Array.from(e.target.files || [])
  if (!files.length) return
  uploading.value = true
  try {
    for (const file of files) {
      if (!file.type.startsWith('image/')) {
        ElMessage.warning(`「${file.name}」不是图片，已跳过`)
        continue
      }
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning(`「${file.name}」超过 5MB，已跳过`)
        continue
      }
      await uploadMedia(file)
    }
    ElMessage.success('上传完成')
    load()
  } catch (err) {
    // 拦截器已提示
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">🖼️ 媒体库</h3>
      <div class="tools">
        <el-input
          v-model="keyword"
          placeholder="按文件名搜索"
          clearable
          style="width: 200px"
        />
        <el-button :loading="uploading" @click="pickFile">上传图片</el-button>
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          multiple
          class="hidden-file"
          @change="onFileChange"
        />
      </div>
    </div>

    <p class="tip">
      共 {{ list.length }} 张图片。点「复制地址」得到完整 URL，点「复制 MD」得到 Markdown 图片语法，可直接粘进文章。
    </p>

    <div v-loading="loading" class="grid">
      <div v-for="m in filtered" :key="m.id" class="item">
        <div class="thumb">
          <img :src="m.url" :alt="m.filename" loading="lazy" />
        </div>
        <div class="meta">
          <span class="name" :title="m.filename">{{ m.filename }}</span>
          <span class="sub">{{ formatSize(m.size) }} · {{ formatTime(m.createTime) }}</span>
        </div>
        <div class="actions">
          <el-button size="small" @click="copyUrl(m)">复制地址</el-button>
          <el-button size="small" @click="copyMarkdown(m)">复制 MD</el-button>
          <el-button size="small" type="danger" plain @click="remove(m)">删除</el-button>
        </div>
      </div>
    </div>

    <p v-if="!loading && !filtered.length" class="empty">
      {{ keyword ? '没有匹配的图片' : '还没有上传过图片～' }}
    </p>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  color: var(--text-strong);
}

.tools {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tip {
  margin: 0 0 18px;
  color: var(--text-muted);
  font-size: 12px;
}

.hidden-file {
  display: none;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.item {
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
  display: flex;
  flex-direction: column;
}

.thumb {
  height: 130px;
  background: #faf6f9;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.meta {
  padding: 8px 10px 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.name {
  font-size: 12px;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  font-size: 11px;
  color: var(--text-muted);
}

.actions {
  padding: 8px 10px 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px;
}
</style>
