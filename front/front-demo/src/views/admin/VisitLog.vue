<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminGetVisits, adminGetVisitStats, adminClearVisits } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const userType = ref('all') // all | guest | member
const keyword = ref('')
const stats = ref({})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [data, s] = await Promise.all([
      adminGetVisits({
        page: page.value,
        pageSize: pageSize.value,
        userType: userType.value,
        keyword: keyword.value || undefined
      }),
      adminGetVisitStats()
    ])
    list.value = data.list || []
    total.value = data.total || 0
    stats.value = s || {}
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

function changeSize(size) {
  pageSize.value = size
  page.value = 1
  load()
}

const cards = computed(() => [
  { label: '今日访问量', value: stats.value.todayPv ?? 0, icon: '👀', color: '#ff6b9d' },
  { label: '今日访客数', value: stats.value.todayUv ?? 0, icon: '🧑', color: '#7ec8e3', sub: '按 IP 去重' },
  { label: '总访问量', value: stats.value.total ?? 0, icon: '📈', color: '#8fd3c4' },
  { label: '已登录访客', value: stats.value.memberCount ?? 0, icon: '🙋', color: '#b39ddb' },
  { label: '未登录游客', value: stats.value.guestCount ?? 0, icon: '🕶️', color: '#ffd98e' }
])

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 19) : ''
}

/** 本地 / 内网 IP 做个小标记，方便区分自己和真实访客 */
function ipTag(ip) {
  if (!ip) return ''
  if (ip === '127.0.0.1' || ip === '0:0:0:0:0:0:0:1' || ip === '::1') return '本机'
  if (/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip)) return '内网'
  if (ip === 'unknown') return '未知'
  return ''
}

async function clearAll() {
  try {
    await ElMessageBox.confirm(
      '将删除全部访问记录，且无法恢复。确定清空吗？',
      '清空访问记录',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await adminClearVisits()
  ElMessage.success('访问记录已清空')
  page.value = 1
  load()
}

function refresh() {
  load()
}
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">👣 访客记录</h3>
      <div class="tools">
        <el-button @click="refresh">刷新</el-button>
        <el-button type="danger" plain @click="clearAll">清空记录</el-button>
      </div>
    </div>

    <div class="stats">
      <div v-for="c in cards" :key="c.label" class="stat-card">
        <span class="stat-icon">{{ c.icon }}</span>
        <span class="stat-value">{{ c.value }}</span>
        <span class="stat-label">{{ c.label }}</span>
        <span v-if="c.sub" class="stat-sub">{{ c.sub }}</span>
      </div>
    </div>

    <div class="filters">
      <el-radio-group v-model="userType" @change="search">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="guest">仅未登录游客</el-radio-button>
        <el-radio-button value="member">仅已注册用户</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        placeholder="按 IP 或访问路径搜索"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column label="访问时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>

      <el-table-column label="访客身份" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.userId" type="success" size="small">
            {{ row.nickname || row.username || ('用户 #' + row.userId) }}
          </el-tag>
          <el-tag v-else type="info" size="small">未登录游客</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="IP 地址" width="180">
        <template #default="{ row }">
          <span class="mono">{{ row.ip }}</span>
          <el-tag v-if="ipTag(row.ip)" size="small" type="warning" effect="plain" class="ip-tag">
            {{ ipTag(row.ip) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="device" label="设备" width="150" />

      <el-table-column label="访问页面" min-width="200">
        <template #default="{ row }">
          <span class="mono path">{{ row.path }}</span>
        </template>
      </el-table-column>

      <el-table-column label="来源" min-width="160">
        <template #default="{ row }">
          <span v-if="row.referer" class="referer" :title="row.referer">{{ row.referer }}</span>
          <span v-else class="none">直接访问</span>
        </template>
      </el-table-column>

      <template #empty>
        <p class="empty">暂无访问记录～</p>
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        @current-change="changePage"
        @size-change="changeSize"
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
  margin-bottom: 16px;
}

.title {
  margin: 0;
  color: var(--text-strong);
}

.tools {
  display: flex;
  gap: 8px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.stat-card {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-icon {
  font-size: 20px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #e04e82;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
}

.stat-sub {
  font-size: 11px;
  color: var(--text-faint);
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.mono {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  color: var(--text-strong);
}

.ip-tag {
  margin-left: 6px;
}

.path {
  word-break: break-all;
}

.referer {
  font-size: 12px;
  color: var(--text-body);
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.none {
  font-size: 12px;
  color: var(--text-faint);
}

.empty {
  color: var(--text-muted);
  padding: 20px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
