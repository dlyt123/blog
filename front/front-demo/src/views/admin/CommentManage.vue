<script setup>
import { ref, onMounted } from 'vue'
import { adminGetComments, adminAuditComment, adminDeleteComment } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const status = ref(null)

onMounted(load)

async function load() {
  list.value = await adminGetComments({ status: status.value })
}

async function audit(row, s) {
  await adminAuditComment(row.id, s)
  ElMessage.success(s === 1 ? '已通过' : '已驳回')
  load()
}

async function del(row) {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await adminDeleteComment(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div>
    <div class="toolbar">
      <h3 class="title">💬 评论管理</h3>
      <el-select v-model="status" placeholder="状态筛选" style="width: 150px" clearable @change="load">
        <el-option label="待审核" :value="0" />
        <el-option label="已通过" :value="1" />
        <el-option label="已驳回" :value="2" />
      </el-select>
    </div>

    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="content" label="内容" min-width="250" />
      <el-table-column prop="postId" label="文章ID" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">
            {{ row.status === 1 ? '已通过' : row.status === 2 ? '已驳回' : '待审核' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 1" size="small" type="success" @click="audit(row, 1)">通过</el-button>
          <el-button v-if="row.status !== 2" size="small" type="warning" @click="audit(row, 2)">驳回</el-button>
          <el-button size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.title {
  margin: 0;
  color: var(--text-strong);
}
</style>
