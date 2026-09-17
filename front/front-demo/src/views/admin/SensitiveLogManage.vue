<script setup>
import { ref, onMounted } from 'vue'
import { adminGetSensitiveLogs, adminClearSensitiveLogs } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await adminGetSensitiveLogs({ page: page.value, pageSize: pageSize.value })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空所有命中记录吗？', '清空记录', { type: 'warning' })
  } catch (e) {
    return
  }
  await adminClearSensitiveLogs()
  ElMessage.success('已清空')
  load()
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 16) : ''
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">🚫 敏感词命中记录</h3>
      <el-button v-if="total" type="danger" plain @click="clearAll">清空记录</el-button>
    </div>
    <p class="tip">这里记录的是「被拦截、没有发出去」的内容。命中哪个词只做服务端记录，不会展示给评论者。</p>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="word" label="命中词" width="140">
        <template #default="{ row }"><el-tag type="danger" effect="plain">{{ row.word }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="scene" label="场景" width="120" />
      <el-table-column prop="content" label="被拦截的内容" min-width="260" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <template #empty><p class="empty">还没有命中记录，说明敏感词没拦住过任何内容～</p></template>
    </el-table>

    <div v-if="total > pageSize" class="pager">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize"
        :current-page="page" @current-change="(p) => { page = p; load() }" />
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.title { margin: 0; color: var(--text-strong); }
.tip { margin: 0 0 14px; color: var(--text-muted); font-size: 12px; }
.empty { color: var(--text-muted); padding: 20px; }
.pager { display: flex; justify-content: center; margin-top: 16px; }
</style>
