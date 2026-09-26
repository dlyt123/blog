<script setup>
import { ref, onMounted } from 'vue'
import { getSeries, getSeriesPosts } from '@/api'
import PostCard from '@/components/PostCard.vue'
import SkeletonPostList from '@/components/SkeletonPostList.vue'

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
        <div
          class="series-head"
          tabindex="0"
          :aria-expanded="!!expanded[s.id]"
          @click="toggle(s.id)"
          @keydown.enter="toggle(s.id)"
        >
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

        <div v-if="expanded[s.id]" :aria-busy="!!loadingMap[s.id]" v-loading="loadingMap[s.id] && expanded[s.id].length > 0" class="series-posts">
          <SkeletonPostList v-if="loadingMap[s.id] && !expanded[s.id].length" :count="2" />
          <template v-else>
            <PostCard v-for="p in expanded[s.id]" :key="p.id" :post="p" />
            <p v-if="!expanded[s.id].length" class="anime-empty">📭 这个系列还没有文章</p>
          </template>
        </div>
      </div>
      <p v-if="!series.length" class="anime-empty">📚 还没有创建系列</p>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-2xl);
  color: var(--text-strong);
}

.subtitle {
  margin: 0 0 var(--space-5);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

/* 整张系列卡是一块可折叠容器，不给它 .anime-card 的悬停上浮，
   否则鼠标划过时整块会跳一下。只让边框变色做"可交互"的提示。 */
.series-card {
  margin-bottom: var(--space-4);
  cursor: default;
  overflow: hidden;
  transition: border-color var(--dur-base) var(--ease-out),
              box-shadow var(--dur-base) var(--ease-out);
}

/* 整张系列卡是一块可折叠容器：全局 .anime-card:hover 会让整块跳一下，
   这里显式取消上浮，只留边框变色做"可交互"的提示。 */
.series-card:hover {
  transform: none;
  border-color: var(--border-brand);
}

/* 展开态用实心品牌色描边，比 hover 再明确一档 */
.series-card.open {
  border-color: var(--brand-400);
}

.series-head {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-md);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.series-head:hover {
  background: var(--surface-pink);
}

/* 序号徽章：给卡片一个视觉锚点，不再是一行干巴巴的文字 */
.series-badge {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: var(--text-lg);
  color: var(--text-on-brand);
  background: linear-gradient(135deg, var(--brand-500), var(--purple-500));
  box-shadow: var(--shadow-brand);
}

.series-info { flex: 1; min-width: 0; }

.series-name {
  margin: 0 0 4px;
  font-size: var(--text-lg);
  color: var(--text-strong);
}

.series-desc {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.series-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.series-count {
  color: var(--brand-700);
  font-size: var(--text-xs);
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--surface-pink);
  border: 1px solid var(--border-soft);
  white-space: nowrap;
}

.series-arrow {
  color: var(--text-faint);
  transition: transform var(--dur-base) var(--ease-out),
              color var(--dur-fast) var(--ease-out);
}

.series-card.open .series-arrow {
  transform: rotate(90deg);
  color: var(--brand-700);
}

.series-posts {
  margin-top: 14px;
  animation: fade-in var(--dur-base) var(--ease-out);
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 手机端：徽章缩小、右侧计数收窄，避免把标题挤到换行 */
@media (max-width: 640px) {
  .series-badge {
    width: 38px;
    height: 38px;
    font-size: var(--text-base);
    border-radius: var(--radius-md);
  }
  .series-head { gap: 10px; }
  .series-name { font-size: var(--text-md); }
  .series-count { padding: 2px 8px; }
}
</style>
