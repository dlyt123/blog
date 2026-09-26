<script setup>
import { ref, onMounted } from 'vue'
import { adminCreateSeries, adminUpdateSeries, adminDeleteSeries, getSeries } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref(null)
const form = ref({ name: '', description: '', sort: 0 })

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = (await getSeries()) || []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = null
  form.value = { name: '', description: '', sort: 0 }
  dialog.value = true
}

function openEdit(item) {
  editing.value = item
  form.value = { name: item.name, description: item.description || '', sort: item.sort || 0 }
  dialog.value = true
}

async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入系列名')
    return
  }
  if (editing.value) {
    await adminUpdateSeries(editing.value.id, form.value)
    ElMessage.success('已更新')
  } else {
    await adminCreateSeries(form.value)
    ElMessage.success('已创建')
  }
  dialog.value = false
  load()
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`确定删除系列「${item.name}」吗？属于它的文章会变为「无系列」，不会被删除。`, '删除系列', { type: 'warning' })
  } catch (e) {
    return
  }
  await adminDeleteSeries(item.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">📚 文章系列 / 专栏</h3>
      <el-button type="primary" @click="openAdd">+ 新建系列</el-button>
    </div>
    <p class="tip">系列用于把连载文章串成合集（如「Spring Boot 入门系列」）。写文章时可给文章指定所属系列。</p>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="系列名" min-width="160" />
      <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip />
      <el-table-column prop="postCount" label="文章数" width="90" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><p class="anime-empty anime-empty--inline">📚 还没有系列，点右上角「新建系列」创建～</p></template>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑系列' : '新建系列'" width="480px">
      <el-form label-width="70px">
        <el-form-item label="系列名"><el-input v-model="form.name" placeholder="例如：Spring Boot 入门系列" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.title { margin: 0; color: var(--text-strong); }
.tip { margin: 0 0 14px; color: var(--text-muted); font-size: var(--text-xs); }
</style>
