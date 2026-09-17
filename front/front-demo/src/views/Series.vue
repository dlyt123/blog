<script setup>
import { ref, onMounted } from 'vue'
import { getSeries, getSeriesPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'

const series = ref([])
const loading = ref(false)
const expanded = ref({})   // { [seriesId]: posts[] }
const loadingMap = ref({}) // { [seriesId]: true }

onMounted(load)

async function load() {
  loading.value = true
  try {
    series.value = (await getSeries()) || []
    // 默认展开第一个系列：页面一进来就有内容，不至于整页空荡荡
    if (series.value.length) {
      toggle(series.value[0].id)
    }
  } finally {
    loading.value = false
  }
}

async function toggle(id) {
  if (expanded.value[id]) {
    delete expanded.value[id]
    return
  }
  loadingMap.value[id] = true
  try {
    const data = await getSeriesPosts(id, { page: 1, pageSize: 100 })
    expanded.value[id] = data.list || []
  } finally {
    loadingMap.value[id] = false
  }
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">📚 系列 / 专栏</h2>
    <p class="subtitle">把连载文章串成合集，按发布顺序阅读。点开一个系列开始阅读。</p>

    <div v-loading="loading">
      <div
        v-for="(s, idx) in series"
        :key="s.id"
        class="series-card anime-card"
        :class="{ open: expanded[s.id] }"
      >
        <div class="series-head" @click="toggle(s.id)">
          <div class="series-badge">{{ String(idx + 1).padStart(2, '0') }}</div>
          <div class="series-info">
            <h3 class="series-name">{{ s.name }}</h3>
            <p v-if="s.description" class="series-desc">{{ s.description }}</p>
          </div>
          <div class="series-right">
            <span class="series-count">{{ s.postCount || 0 }} 篇</span>
            <span class="series-arrow">{{ expanded[s.id] ? '▾' : '▸' }}</span>
          </div>
        </div>

        <div v-if="expanded[s.id]" v-loading="loadingMap[s.id]" class="series-posts">
          <PostCard v-for="p in expanded[s.id]" :key="p.id" :post="p" />
          <p v-if="!expanded[s.id].length" class="empty">这个系列还没有文章</p>
        </div>
      </div>
      <p v-if="!series.length" class="empty">还没有创建系列</p>
    </div>
  </div>
</template>

<style scoped>
.page-title { margin: 0 0 8px; font-size: 24px; color: var(--text-strong); }
.subtitle { margin: 0 0 20px; color: var(--text-muted); font-size: 13px; }

.series-card {
  margin-bottom: 16px;
  cursor: default;
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.series-card:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 107, 157, 0.4);
}

.series-card.open {
  border-color: rgba(255, 107, 157, 0.45);
}

.series-head {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  padding: 4px;
  border-radius: 12px;
  transition: background 0.2s;
}

.series-head:hover {
  background: rgba(255, 214, 228, 0.35);
}

/* 序号徽章：给卡片一个视觉锚点，不再是一行干巴巴的文字 */
.series-badge {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  color: #fff;
  background: linear-gradient(135deg, #ff6b9d, #b39ddb);
  box-shadow: 0 4px 12px rgba(255, 107, 157, 0.28);
}

.series-info { flex: 1; min-width: 0; }
.series-name { margin: 0 0 4px; font-size: 17px; color: var(--text-strong); }
.series-desc { margin: 0; color: var(--text-muted); font-size: 13px; }
.series-right { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }

.series-count {
  color: #e04e82;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--surface-pink);
  border: 1px solid #ffd6e4;
  white-space: nowrap;
}

.series-arrow { color: var(--text-faint); transition: transform 0.2s; }
.series-card.open .series-arrow { transform: rotate(90deg); color: #e04e82; }

.series-posts { margin-top: 14px; animation: fade-in 0.25s ease; }
.empty { color: var(--text-muted); padding: 30px; text-align: center; }

@keyframes fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 手机端：徽章缩小、右侧计数收窄，避免把标题挤到换行 */
@media (max-width: 640px) {
  .series-badge { width: 38px; height: 38px; font-size: 14px; border-radius: 11px; }
  .series-head { gap: 10px; }
  .series-name { font-size: 15px; }
  .series-count { padding: 2px 8px; }
}
</style>
