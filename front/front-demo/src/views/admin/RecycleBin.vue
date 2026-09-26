<script setup>
import { ref, onMounted } from 'vue'
import { adminGetTrash, adminRestorePost, adminPurgePost } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await adminGetTrash({
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

function changePage(p) {
  page.value = p
  load()
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

async function restore(item) {
  await adminRestorePost(item.id)
  ElMessage.success('已恢复到文章列表')
  load()
}

async function purge(item) {
  try {
    await ElMessageBox.confirm(
      `「${item.title}」将永久删除，评论、点赞、收藏等关联数据一并清除，且无法恢复。确定吗？`,
      '彻底删除',
      { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await adminPurgePost(item.id)
  ElMessage.success('已彻底删除')
  load()
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">🗑️ 回收站</h3>
      <div class="tools">
        <el-input
          v-model="keyword"
          placeholder="按标题搜索"
          clearable
          style="width: 200px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <p class="tip">
      这里是在文章列表里删除（软删除）的文章。可以「恢复」回文章列表，或「永久删除」彻底清除。
    </p>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">{{ row.title }}</template>
      </el-table-column>
      <el-table-column label="作者" width="120">
        <template #default="{ row }">{{ row.authorName || '匿名' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">{{ formatTime(row.updateTime || row.publishTime || row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="restore(row)">恢复</el-button>
          <el-button size="small" type="danger" plain @click="purge(row)">永久删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <p class="anime-empty anime-empty--inline">🗑️ 回收站是空的～</p>
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="pageSize"
        @current-change="changePage"
      />
    </div>
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
  margin: 0 0 16px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.pager {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}
</style>
