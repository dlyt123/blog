<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { adminGetPosts, adminDeletePost, adminPublishPost, adminPinPost, adminRecommendPost, adminBatchPosts, adminAuditPost } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
// 管理员：可见全部文章与管理操作；普通用户（博主）：只管理自己的文章
const isAdmin = computed(() => (userStore.userInfo?.role || '').toUpperCase() === 'ADMIN')

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const status = ref(null)
const keyword = ref('')
const selection = ref([])

onMounted(load)

async function load() {
  const data = await adminGetPosts({ status: status.value, keyword: keyword.value, page: page.value, pageSize })
  list.value = data.list
  total.value = data.total
}

function reset() {
  page.value = 1
  load()
}

async function del(row) {
  await ElMessageBox.confirm(`确定删除文章「${row.title}」吗？`, '提示', { type: 'warning' })
  await adminDeletePost(row.id)
  ElMessage.success('已删除')
  load()
}

async function publish(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await adminPublishPost(row.id, newStatus)
  ElMessage.success(newStatus === 1 ? '已发布' : '已下架')
  load()
}

async function pin(row) {
  await adminPinPost(row.id, row.pinned ? 0 : 1)
  ElMessage.success(row.pinned ? '已取消置顶' : '已置顶')
  load()
}

async function recommend(row) {
  await adminRecommendPost(row.id, row.recommended ? 0 : 1)
  ElMessage.success(row.recommended ? '已取消推荐' : '已推荐')
  load()
}

async function audit(row, pass) {
  if (pass) {
    await adminAuditPost(row.id, true, null)
    ElMessage.success('已通过并发布')
    load()
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '驳回后文章会退回草稿（前台不可见）。请填写驳回理由（作者能看到）：',
      '驳回《' + (row.title || '') + '》',
      { confirmButtonText: '驳回', cancelButtonText: '取消', inputValue: '内容不符合发布规范' }
    )
    await adminAuditPost(row.id, false, value || '内容不符合发布规范')
    ElMessage.success('已驳回')
    load()
  } catch (e) {
    // 用户取消
  }
}

async function batch(action) {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选文章')
    return
  }
  await ElMessageBox.confirm(`确定批量执行「${action}」吗？`, '提示', { type: 'warning' })
  await adminBatchPosts({ ids: selection.value.map((p) => p.id), action })
  ElMessage.success('操作成功')
  selection.value = []
  load()
}

function edit(row) {
  router.push({ path: '/write', query: { id: row.id } })
}

function create() {
  router.push('/write')
}
</script>

<template>
  <div>
    <h3 class="page-title">{{ isAdmin ? '文章管理' : '我的文章' }}</h3>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索标题" style="width: 220px" clearable @keyup.enter="reset" />
      <el-select v-model="status" placeholder="状态" style="width: 130px" clearable @change="reset">
        <el-option label="已发布" :value="1" />
        <el-option label="草稿" :value="0" />
      </el-select>
      <el-button @click="reset">查询</el-button>
      <el-button type="primary" @click="create">➕ 写文章</el-button>
      <el-button @click="batch('publish')">批量发布</el-button>
      <el-button @click="batch('offline')">批量下架</el-button>
      <el-button type="danger" @click="batch('delete')">批量删除</el-button>
    </div>

    <el-table :data="list" style="width: 100%" @selection-change="(v) => (selection = v)">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column v-if="isAdmin" prop="authorName" label="作者" width="110" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
      <el-tag :type="row.auditStatus === 1 ? 'warning' : row.auditStatus === 2 ? 'danger' : row.status === 1 ? 'success' : 'info'">
        {{ row.auditStatus === 1 ? '待审核' : row.auditStatus === 2 ? '已驳回' : row.status === 1 ? '已发布' : '草稿' }}
      </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="70">
        <template #default="{ row }">{{ row.pinned ? '⭐' : '' }}</template>
      </el-table-column>
      <el-table-column prop="views" label="阅读" width="80" />
      <el-table-column prop="likes" label="赞" width="70" />
      <el-table-column :label="isAdmin ? '操作（管理员）' : '操作'" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="publish(row)">
            {{ row.status === 1 ? '下架' : '发布' }}
          </el-button>
          <!-- 置顶/推荐：管理员专属 -->
          <el-button v-if="isAdmin" size="small" @click="pin(row)">
            {{ row.pinned ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button v-if="isAdmin" size="small" @click="recommend(row)">
            {{ row.recommended ? '取消推荐' : '推荐' }}
          </el-button>
          <el-button v-if="isAdmin && row.auditStatus === 1" size="small" type="success" @click="audit(row, true)">通过</el-button>
      <el-button v-if="isAdmin && row.auditStatus === 1" size="small" type="danger" @click="audit(row, false)">驳回</el-button>
      <el-button size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="(p) => { page = p; load() }"
      />
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 16px;
  color: var(--text-strong);
  font-size: 18px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>