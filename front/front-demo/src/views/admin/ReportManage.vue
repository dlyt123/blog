<script setup>
import { ref, onMounted } from 'vue'
import { adminGetReports, adminHandleReport } from '@/api'
import { ElMessage } from 'element-plus'

const list = ref([])
const loading = ref(false)
const status = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await adminGetReports(status.value === null ? {} : { status: status.value })
  } finally {
    loading.value = false
  }
}

function switchStatus(s) {
  status.value = s
  load()
}

async function handle(item) {
  await adminHandleReport(item.id)
  ElMessage.success('已标记为处理')
  load()
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 16) : ''
}

function targetLink(item) {
  return item.targetType === 'post' ? `/posts/${item.targetId}` : `/posts/${item.targetId}`
}
</script>

<template>
  <div>
    <h3 class="title">🚩 内容举报</h3>

    <div class="tabs">
      <button class="tab" :class="{ on: status === null }" @click="switchStatus(null)">全部</button>
      <button class="tab" :class="{ on: status === 0 }" @click="switchStatus(0)">待处理</button>
      <button class="tab" :class="{ on: status === 1 }" @click="switchStatus(1)">已处理</button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="被举报内容" min-width="220">
        <template #default="{ row }">
          <router-link :to="targetLink(row)" target="_blank">{{ row.targetTitle || (row.targetType + ' #' + row.targetId) }}</router-link>
        </template>
      </el-table-column>
      <el-table-column prop="targetType" label="类型" width="90">
        <template #default="{ row }">{{ row.targetType === 'post' ? '文章' : '评论' }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="举报理由" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '（未填写）' }}</template>
      </el-table-column>
      <el-table-column prop="reporterName" label="举报人" width="120">
        <template #default="{ row }">{{ row.reporterName || '匿名' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
            {{ row.status === 1 ? '已处理' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" size="small" type="primary" plain @click="handle(row)">处理</el-button>
        </template>
      </el-table-column>
      <template #empty><p class="empty">没有举报记录</p></template>
    </el-table>
  </div>
</template>

<style scoped>
.title { margin: 0 0 16px; color: var(--text-strong); }
.tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.tab { padding: 6px 16px; border: 1px solid var(--border-soft); border-radius: 999px; background: var(--surface); color: var(--text-body); font-size: 12px; cursor: pointer; }
.tab.on { background: linear-gradient(135deg, #ff6b9d, #ff8fb5); border-color: transparent; color: #fff; font-weight: 600; }
.empty { color: var(--text-muted); padding: 20px; }
</style>
