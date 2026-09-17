<script setup>
import { ref, onMounted } from 'vue'
import { adminGetOperationLogs, adminClearOperationLogs } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await adminGetOperationLogs({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空所有操作日志吗？清空后无法恢复。', '清空日志', { type: 'warning' })
  } catch (e) {
    return
  }
  await adminClearOperationLogs()
  ElMessage.success('已清空')
  load()
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 19) : ''
}

function actionType(action) {
  if (action === '新增') return 'success'
  if (action === '删除') return 'danger'
  return 'warning'
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">📋 操作日志</h3>
      <div class="tools">
        <el-input
          v-model="keyword"
          placeholder="搜用户名 / 动作 / 对象"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button @click="search">搜索</el-button>
        <el-button v-if="total" type="danger" plain @click="clearAll">清空</el-button>
      </div>
    </div>
    <p class="tip">记录后台所有「改动数据」的操作，用于事后追溯（比如某篇文章是谁删的）。</p>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="操作人" width="130">
        <template #default="{ row }">{{ row.username || '（匿名）' }}</template>
      </el-table-column>
      <el-table-column label="动作" width="90">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" effect="plain">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="target" label="操作对象" width="140" />
      <el-table-column prop="method" label="方法" width="80" />
      <el-table-column prop="path" label="接口路径" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <span :class="row.success === 1 ? 'ok' : 'bad'">{{ row.success === 1 ? '成功' : '失败' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <template #empty><p class="empty">还没有操作记录</p></template>
    </el-table>

    <div v-if="total > pageSize" class="pager">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize"
        :current-page="page" @current-change="(p) => { page = p; load() }" />
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; gap: 12px; flex-wrap: wrap; }
.title { margin: 0; color: var(--text-strong); }
.tools { display: flex; gap: 8px; }
.tip { margin: 0 0 14px; color: var(--text-muted); font-size: 12px; }
.empty { color: var(--text-muted); padding: 20px; }
.ok { color: #3cba7a; }
.bad { color: #e24b4a; }
.pager { display: flex; justify-content: center; margin-top: 16px; }
</style>
