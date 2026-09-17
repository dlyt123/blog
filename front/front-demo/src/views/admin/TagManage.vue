<script setup>
import { ref, onMounted } from 'vue'
import { getTags, adminCreateTag, adminUpdateTag, adminDeleteTag } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({ id: null, name: '', slug: '' })

onMounted(load)

async function load() {
  list.value = await getTags()
}

function openCreate() {
  form.value = { id: null, name: '', slug: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请填写标签名称')
    return
  }
  if (form.value.id) {
    await adminUpdateTag(form.value.id, form.value)
  } else {
    await adminCreateTag(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function del(row) {
  await ElMessageBox.confirm(`确定删除标签「${row.name}」吗？`, '提示', { type: 'warning' })
  await adminDeleteTag(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div>
    <div class="toolbar">
      <h3 class="title">🏷️ 标签管理</h3>
      <el-button type="primary" @click="openCreate">➕ 新增标签</el-button>
    </div>

    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" width="200" />
      <el-table-column prop="slug" label="别名" width="200" />
      <el-table-column prop="postCount" label="文章数" width="100" />
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑标签' : '新增标签'" width="420px">
      <el-form label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="form.slug" placeholder="URL 别名（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
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
