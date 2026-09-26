<script setup>
import { ref, onMounted } from 'vue'
import { adminGetSensitiveWords, adminAddSensitiveWords, adminDeleteSensitiveWord } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const cacheSize = ref(0)
const keyword = ref('')
const loading = ref(false)

// 新增
const dialogVisible = ref(false)
const input = ref('')
const adding = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await adminGetSensitiveWords({ keyword: keyword.value || undefined })
    list.value = data.list || []
    total.value = data.total || 0
    cacheSize.value = data.cacheSize || 0
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

function openAdd() {
  input.value = ''
  dialogVisible.value = true
}

async function submitAdd() {
  if (!input.value.trim()) {
    ElMessage.warning('请输入要添加的敏感词')
    return
  }
  adding.value = true
  try {
    const res = await adminAddSensitiveWords({ words: input.value })
    ElMessage.success(`提交 ${res.submitted} 个，新增 ${res.added} 个，词库共 ${res.total} 个`)
    dialogVisible.value = false
    load()
  } finally {
    adding.value = false
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`确定删除敏感词「${item.word}」吗？`, '删除敏感词', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await adminDeleteSensitiveWord(item.id)
  ElMessage.success('已删除')
  load()
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">🚫 敏感词管理</h3>
      <div class="tools">
        <el-input
          v-model="keyword"
          placeholder="搜索敏感词"
          clearable
          style="width: 180px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button @click="search">查询</el-button>
        <el-button type="primary" @click="openAdd">+ 添加敏感词</el-button>
      </div>
    </div>

    <p class="tip">
      评论提交时会比对这里维护的词库，命中就直接拦截、不予发布（评论本身是发即显示，不走先审后发）。
      支持一次粘贴多个词，用换行、逗号、分号或空格分隔。修改后立即生效，无需重启服务。
    </p>

    <div class="stat-row">
      <span class="pill">当前显示 <b>{{ total }}</b> 个</span>
      <span class="pill">内存词库 <b>{{ cacheSize }}</b> 个</span>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="word" label="敏感词" min-width="180">
        <template #default="{ row }">
          <el-tag type="danger" effect="plain">{{ row.word }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="添加时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <p class="anime-empty anime-empty--inline">🚫 词库是空的，点击右上角「添加敏感词」开始维护～</p>
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" title="添加敏感词" width="520px">
      <p class="dialog-tip">可以一次粘贴多个，用换行、逗号、分号或空格分隔。</p>
      <el-input
        v-model="input"
        type="textarea"
        :rows="8"
        placeholder="例如：&#10;办证&#10;代开发票&#10;刷单"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.title {
  margin: 0;
  color: var(--text-strong);
}

.tools {
  display: flex;
  gap: 8px;
}

.tip {
  margin: 0 0 14px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.stat-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.pill {
  padding: 5px var(--space-3);
  background: var(--surface-pink);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  color: var(--text-body);
}

.pill b {
  color: var(--brand-700);
}

.dialog-tip {
  margin: 0 0 10px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
