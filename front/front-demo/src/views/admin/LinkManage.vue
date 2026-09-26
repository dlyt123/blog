<script setup>
import { ref, onMounted } from 'vue'
import { getLinks, adminCreateLink, adminUpdateLink, adminDeleteLink, adminCheckLinks } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({ id: null, name: '', url: '', groupName: '默认分组', sort: 0 })
const checking = ref(false)
const checkResult = ref([])

onMounted(load)

async function load() {
  list.value = await getLinks()
}

/** 友链存活检测 */
async function checkLinks() {
  checking.value = true
  checkResult.value = []
  try {
    const res = await adminCheckLinks()
    checkResult.value = res
    const ok = res.filter((r) => r.ok).length
    ElMessage.success(`检测完成：${ok}/${res.length} 个可访问`)
  } finally {
    checking.value = false
  }
}

function openCreate() {
  form.value = { id: null, name: '', url: '', groupName: '默认分组', sort: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name || !form.value.url) {
    ElMessage.warning('请填写名称和链接')
    return
  }
  if (form.value.id) {
    await adminUpdateLink(form.value.id, form.value)
  } else {
    await adminCreateLink(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function del(row) {
  await ElMessageBox.confirm(`确定删除友链「${row.name}」吗？`, '提示', { type: 'warning' })
  await adminDeleteLink(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div>
    <div class="toolbar">
      <h3 class="title">🔗 友链管理</h3>
      <div>
        <el-button :loading="checking" @click="checkLinks">🩺 检测存活</el-button>
        <el-button type="primary" @click="openCreate">➕ 新增友链</el-button>
      </div>
    </div>

    <div v-if="checkResult.length" class="check-result">
      <div v-for="r in checkResult" :key="r.id" class="check-item">
        <span class="dot" :class="{ ok: r.ok, bad: !r.ok }"></span>
        <span class="check-name">{{ r.name }}</span>
        <span class="check-url">{{ r.url }}</span>
        <span v-if="r.ok" class="check-status ok-text">正常 ({{ r.status }})</span>
        <span v-else class="check-status bad-text">失败 {{ r.status ? '(' + r.status + ')' : '' }}</span>
      </div>
    </div>

    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="url" label="链接" min-width="220" />
      <el-table-column prop="groupName" label="分组" width="120" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑友链' : '新增友链'" width="440px">
      <el-form label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="form.url" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.groupName" />
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

.check-result {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--surface);
  border-radius: 10px;
  border: 1px solid var(--border-soft);
}

.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

/* 友链存活检测的结果标记：走语义色而不是各写一个绿/红 */
.dot.ok { background: var(--success); }
.dot.bad { background: var(--danger); }

.check-name { font-weight: 600; color: var(--text-strong); }
.check-url { color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 360px; }
.ok-text { color: var(--success); }
.bad-text { color: var(--danger); }
</style>
