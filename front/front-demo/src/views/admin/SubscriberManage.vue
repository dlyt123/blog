<script setup>
import { ref, onMounted } from 'vue'
import { adminGetSubscribes, adminDeleteSubscribe } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = (await adminGetSubscribes()) || []
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

/** 把所有邮箱导出成一行一个，方便粘进邮件群发工具的收件人列表 */
async function copyAll() {
  if (!list.value.length) {
    ElMessage.warning('还没有订阅者')
    return
  }
  const emails = list.value.map((s) => s.email).join('\n')
  try {
    await navigator.clipboard.writeText(emails)
    ElMessage.success(`已复制 ${list.value.length} 个邮箱`)
  } catch (e) {
    ElMessageBox.alert(emails, '请手动复制', { confirmButtonText: '知道了' })
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`确定移除「${item.email}」吗？`, '移除订阅', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await adminDeleteSubscribe(item.id)
  ElMessage.success('已移除')
  load()
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">📮 邮件订阅</h3>
      <el-button @click="copyAll">复制全部邮箱</el-button>
    </div>

    <p class="tip">
      访客在前台页脚填写邮箱即可订阅。这里可以查看和移除订阅者；
      复制邮箱后可直接粘贴到邮件群发工具的收件人列表。
    </p>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="email" label="邮箱" min-width="240" />
      <el-table-column label="订阅时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <p class="anime-empty anime-empty--inline">📮 还没有订阅者～</p>
      </template>
    </el-table>
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

.tip {
  margin: 0 0 18px;
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: 1.7;
}
</style>
