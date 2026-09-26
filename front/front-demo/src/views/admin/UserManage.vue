<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminGetUsers, adminBanUser, adminMuteUser } from '@/api'

const loading = ref(false)
const users = ref([])

async function load() {
  loading.value = true
  try {
    users.value = (await adminGetUsers()) || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function stateText(u) {
  if (u.banned) return '已封号'
  if (u.muted) return '禁言中'
  return '正常'
}

function stateType(u) {
  if (u.banned) return 'danger'
  if (u.muted) return 'warning'
  return 'success'
}

async function toggleBan(u) {
  if (u.banned) {
    // 解封
    await adminBanUser(u.id, 0, null)
    ElMessage.success(`已解封 ${u.nickname || u.username}`)
    load()
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      `封号后该用户将无法登录、无法发表任何内容。\n可填写封禁原因（会提示给对方）：`,
      `封禁 ${u.nickname || u.username}`,
      { confirmButtonText: '封号', cancelButtonText: '取消', inputValue: '违反社区规范' }
    )
    await adminBanUser(u.id, 1, value || '违反社区规范')
    ElMessage.success('已封号')
    load()
  } catch (e) {
    // 用户取消
  }
}

async function muteUser(u) {
  if (u.muted) {
    await adminMuteUser(u.id, 0, null)
    ElMessage.success(`已解除 ${u.nickname || u.username} 的禁言`)
    load()
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '禁言期间该用户仍可浏览，但不能评论/发文章/发私信。输入禁言分钟数：',
      `禁言 ${u.nickname || u.username}`,
      { confirmButtonText: '禁言', cancelButtonText: '取消', inputValue: '60', inputPattern: /^\d+$/, inputErrorMessage: '请输入分钟数' }
    )
    const minutes = parseInt(value, 10) || 0
    await adminMuteUser(u.id, minutes, '违反社区规范')
    ElMessage.success(`已禁言 ${minutes} 分钟（到期自动解除）`)
    load()
  } catch (e) {
    // 用户取消
  }
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').substring(0, 16) : ''
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="page-title anime-title">👥 用户管理</h2>

    <el-table v-loading="loading" :data="users" class="anime-card" style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="用户名">
        <template #default="{ row }">
          <b>{{ row.username }}</b>
          <el-tag v-if="row.role === 'ADMIN'" size="small" style="margin-left: 6px">管理员</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="stateType(row)">{{ stateText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="banReason" label="封禁/禁言原因" show-overflow-tooltip />
      <el-table-column label="注册时间" width="150">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <template v-if="row.role === 'ADMIN'">
            <span style="color: var(--text-faint); font-size: 13px">—</span>
          </template>
          <template v-else>
            <el-button size="small" :type="row.banned ? 'success' : 'danger'" @click="toggleBan(row)">
              {{ row.banned ? '解封' : '封号' }}
            </el-button>
            <el-button size="small" :type="row.muted ? 'success' : 'warning'" @click="muteUser(row)">
              {{ row.muted ? '解除禁言' : '禁言' }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <p style="color: var(--text-faint); font-size: 13px; margin-top: 12px">
      封号后：无法登录、无法发表任何内容；禁言后：可浏览但不能写，到期自动解除。
    </p>
  </div>
</template>

<style scoped>
.page-title { margin: 0 0 16px; font-size: 24px; color: var(--text-strong); }
</style>
