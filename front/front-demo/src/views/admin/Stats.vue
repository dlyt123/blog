<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminGetStats, adminGetPopular, adminGetVisitTrend, adminGetReferrers } from '@/api'

const stats = ref({})
const popular = ref([])
const trend = ref([])
const referrers = ref([])
const trendDays = ref(7)
const trendLoading = ref(false)

onMounted(async () => {
  // 每个请求独立容错：某一个挂了不该把整页拖死，
  // 否则后端没起来 / 网络抖一下，页面上连数字卡片都渲染不出来。
  // 失败时 axios 拦截器已经弹过提示了，这里只要保证页面还能渲染。
  try {
    stats.value = (await adminGetStats()) || {}
  } catch (e) {
    stats.value = {}
  }
  try {
    popular.value = (await adminGetPopular()) || []
  } catch (e) {
    popular.value = []
  }
  loadTrend()
  loadReferrers()
})

async function loadReferrers() {
  try {
    referrers.value = (await adminGetReferrers(8)) || []
  } catch (e) {
    referrers.value = []
  }
}

const maxRef = computed(() => Math.max(1, ...referrers.value.map((r) => Number(r.pv) || 0)))
function maxPct(pv) {
  return Math.round((Number(pv) / maxRef.value) * 100) + '%'
}

async function loadTrend() {
  trendLoading.value = true
  try {
    trend.value = (await adminGetVisitTrend(trendDays.value)) || []
  } finally {
    trendLoading.value = false
  }
}

function switchDays(d) {
  trendDays.value = d
  loadTrend()
}

// 标注每项的口径，避免和前台看到的数字对不上时产生误会
const cards = computed(() => [
  {
    key: 'totalPosts',
    label: '文章总数',
    icon: '📝',
    color: '#ff6b9d',
    sub: `已发布 ${stats.value.publishedPosts ?? 0} · 草稿 ${stats.value.draftPosts ?? 0}`
  },
  { key: 'totalComments', label: '评论数', icon: '💬', color: '#7ec8e3', sub: '含待审核 / 已驳回' },
  { key: 'totalViews', label: '总浏览', icon: '👀', color: '#8fd3c4', sub: '全部文章的阅读量之和' },
  { key: 'totalCategories', label: '分类数', icon: '📁', color: '#b39ddb' },
  { key: 'totalTags', label: '标签数', icon: '🏷️', color: '#ffd98e' },
  { key: 'totalLinks', label: '友链数', icon: '🔗', color: '#ff8fb5' }
])

// ===== PV/UV 趋势图（纯 SVG，无第三方图表库依赖）=====
const chart = computed(() => {
  const data = trend.value
  if (!data.length) return { bars: [], max: 0, ticks: [] }
  const W = 700
  const H = 230
  const padL = 40
  const padR = 8
  const padT = 12
  const padB = 30
  const plotW = W - padL - padR
  const plotH = H - padT - padB

  const max = Math.max(1, ...data.map((d) => Math.max(Number(d.pv) || 0, Number(d.uv) || 0)))
  const n = data.length
  const slot = plotW / n
  const barW = Math.min(15, slot * 0.26)

  const bars = data.map((d, i) => {
    const pv = Number(d.pv) || 0
    const uv = Number(d.uv) || 0
    const cx = padL + i * slot + slot / 2
    const hpv = (pv / max) * plotH
    const huv = (uv / max) * plotH
    return {
      label: (d.date || '').slice(5), // MM-DD
      cx, pv, uv,
      pvX: cx - barW - 1.5, pvY: padT + plotH - hpv, pvH: Math.max(hpv, pv > 0 ? 2 : 0),
      uvX: cx + 1.5, uvY: padT + plotH - huv, uvH: Math.max(huv, uv > 0 ? 2 : 0)
    }
  })

  // Y 轴刻度（0 / 一半 / 最大值，最多 4 档）
  const ticks = [0, Math.round(max / 2), max].map((v) => ({
    value: v,
    y: padT + plotH - (v / max) * plotH
  }))

  return { bars, max, ticks, W, H, plotH, padT, padL, plotW, barW }
})
</script>

<template>
  <div>
    <h3 class="title">📊 数据统计</h3>

    <div class="stat-grid">
      <div v-for="c in cards" :key="c.key" class="stat-card" :style="{ borderTopColor: c.color }">
        <span class="stat-icon">{{ c.icon }}</span>
        <span class="stat-value">{{ stats[c.key] ?? 0 }}</span>
        <span class="stat-label">{{ c.label }}</span>
        <span v-if="c.sub" class="stat-sub">{{ c.sub }}</span>
      </div>
    </div>

    <div class="section-head">
      <h3 class="title" style="margin: 28px 0 0">📈 访问趋势（PV / UV）</h3>
      <div class="days-toggle">
        <button
          v-for="d in [7, 30]"
          :key="d"
          class="day-btn"
          :class="{ on: trendDays === d }"
          @click="switchDays(d)"
        >近 {{ d }} 天</button>
      </div>
    </div>
    <p class="chart-note">PV = 页面访问次数，UV = 去重后的访客数（按 IP 去重）。数据来自访客留痕，后台页面不计入。</p>

    <div v-loading="trendLoading" class="chart-card">
      <svg v-if="chart.bars.length" :viewBox="`0 0 ${chart.W} ${chart.H}`" class="chart-svg" preserveAspectRatio="xMidYMid meet">
        <!-- 网格线 + Y 轴刻度 -->
        <g v-for="t in chart.ticks" :key="t.value + '-' + t.y">
          <line :x1="chart.padL" :y1="t.y" :x2="chart.W - 8" :y2="t.y" class="grid" />
          <text :x="chart.padL - 8" :y="t.y + 4" class="tick" text-anchor="end">{{ t.value }}</text>
        </g>

        <!-- 柱状 -->
        <g v-for="(b, i) in chart.bars" :key="i">
          <rect :x="b.pvX" :y="b.pvY" :width="chart.barW" :height="b.pvH" rx="2" class="bar-pv">
            <title>PV {{ b.pv }}</title>
          </rect>
          <rect :x="b.uvX" :y="b.uvY" :width="chart.barW" :height="b.uvH" rx="2" class="bar-uv">
            <title>UV {{ b.uv }}</title>
          </rect>
          <text :x="b.cx" :y="chart.plotH + chart.padT + 18" class="tick" text-anchor="middle">{{ b.label }}</text>
        </g>
      </svg>
      <p v-else-if="!trendLoading" class="empty">还没有访问数据，去前台逛逛再回来看看吧～</p>
    </div>

    <h3 class="title" style="margin-top: 28px">🧭 访问来源</h3>
    <div class="referrer-card">
      <p v-if="!referrers.length" class="empty">还没有访问来源数据，去前台逛逛再回来看看～</p>
      <div v-for="r in referrers" :key="r.source" class="ref-row">
        <span class="ref-name">{{ r.source }}</span>
        <div class="ref-bar-wrap">
          <div class="ref-bar" :style="{ width: maxPct(r.pv) }"></div>
        </div>
        <span class="ref-num">{{ r.pv }} 次 · {{ r.uv }} 人</span>
      </div>
    </div>

    <h3 class="title" style="margin-top: 28px">🔥 热门文章 TOP 10</h3>
    <el-table :data="popular">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="views" label="阅读" width="100" />
      <el-table-column prop="likes" label="赞" width="80" />
    </el-table>
  </div>
</template>

<style scoped>
.title {
  margin: 0 0 20px;
  color: var(--text-strong);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 16px;
}

.stat-card {
  background: var(--surface);
  border-radius: 14px;
  border-top: 4px solid #ff6b9d;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.stat-icon {
  font-size: 26px;
}

.stat-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-strong);
  margin: 6px 0;
}

.stat-label {
  color: var(--text-muted);
  font-size: 13px;
}

.stat-sub {
  display: block;
  margin-top: 6px;
  color: var(--text-faint);
  font-size: 11px;
  line-height: 1.4;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.days-toggle {
  display: flex;
  gap: 6px;
}

.day-btn {
  padding: 6px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-body);
  font-size: 12px;
  cursor: pointer;
}

.day-btn.on {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  border-color: transparent;
  color: #fff;
  font-weight: 600;
}

.chart-note {
  margin: 8px 0 14px;
  color: var(--text-muted);
  font-size: 12px;
}

.chart-card {
  background: var(--surface);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  min-height: 220px;
}

.chart-svg {
  width: 100%;
  height: auto;
  display: block;
}

.grid {
  stroke: #f0e8ee;
  stroke-width: 1;
}

.tick {
  fill: #b0a0b0;
  font-size: 10px;
}

.bar-pv {
  fill: #ff6b9d;
}

.bar-uv {
  fill: #7ec8e3;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 60px 0;
}

.referrer-card {
  background: var(--surface);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.ref-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}

.ref-name {
  width: 120px;
  flex-shrink: 0;
  color: #5a5a6a;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ref-bar-wrap {
  flex: 1;
  height: 10px;
  background: #f5eef3;
  border-radius: 999px;
  overflow: hidden;
}

.ref-bar {
  height: 100%;
  background: linear-gradient(90deg, #ff6b9d, #ff8fb5);
  border-radius: 999px;
}

.ref-num {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
