<script setup>
import { ref, computed, onMounted } from 'vue'
import { getLinks } from '@/api'

const links = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    links.value = (await getLinks()) || []
  } catch (e) {
    links.value = []
  } finally {
    loading.value = false
  }
})

/** 按「分组」聚合，组内保持后端返回的 sort 顺序 */
const groups = computed(() => {
  const map = new Map()
  for (const l of links.value) {
    const g = l.groupName || '默认分组'
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(l)
  }
  return Array.from(map, ([name, items]) => ({ name, items }))
})

/** 取域名做副标题展示 */
function hostOf(url) {
  try {
    return new URL(url).host
  } catch (e) {
    return url || ''
  }
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">🔗 友情链接</h2>
    <p class="subtitle">这些是我常逛的站点，点击卡片即可跳转</p>

    <div v-if="groups.length" v-loading="loading">
      <div v-for="g in groups" :key="g.name" class="link-group">
        <h3 class="group-title">{{ g.name }}</h3>
        <div class="link-grid">
          <a
            v-for="l in g.items"
            :key="l.id"
            class="link-card anime-card"
            :href="l.url"
            target="_blank"
            rel="noopener noreferrer"
            :title="l.url"
          >
            <span class="link-avatar">{{ (l.name || '?')[0] }}</span>
            <span class="link-info">
              <span class="link-name">{{ l.name }}</span>
              <span class="link-host">{{ hostOf(l.url) }}</span>
            </span>
            <span class="link-arrow">↗</span>
          </a>
        </div>
      </div>
    </div>

    <p v-else-if="!loading" class="anime-empty">🔗 还没有添加友链～</p>

    <p class="tip">想和我交换友链？欢迎通过「关于」页面联系我。</p>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  color: var(--text-strong);
}

.subtitle {
  margin: 0 0 var(--space-6);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.link-group {
  margin-bottom: var(--space-6);
}

/* 分组标题：左侧一道品牌色短杠 —— 比加粗或换色更轻，也不干扰卡片本身 */
.group-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--brand-700);
  padding-left: 10px;
  border-left: 3px solid var(--brand-300);
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: var(--space-3);
}

.link-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 14px var(--space-4);
  text-decoration: none;
  transition: transform var(--dur-base) var(--ease-out),
              box-shadow var(--dur-base) var(--ease-out),
              border-color var(--dur-base) var(--ease-out);
}

.link-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-brand);
  box-shadow: var(--shadow-lg);
}

.link-avatar {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--brand-200), var(--blue-300));
  /* 浅色渐变底配品牌深色字；原来的白字在这个底上基本看不清 */
  color: var(--brand-800);
  font-weight: 700;
  font-size: var(--text-lg);
}

.link-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.link-name {
  color: var(--text-strong);
  font-weight: 600;
  font-size: var(--text-base);
  transition: color var(--dur-fast) var(--ease-out);
}

.link-card:hover .link-name {
  color: var(--brand-700);
}

.link-host {
  color: var(--text-muted);
  font-size: var(--text-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-arrow {
  color: var(--text-faint);
  font-size: var(--text-base);
  flex-shrink: 0;
  transition: color var(--dur-fast) var(--ease-out);
}

.link-card:hover .link-arrow {
  color: var(--brand-700);
}

.tip {
  margin-top: var(--space-8);
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-xs);
}
</style>
