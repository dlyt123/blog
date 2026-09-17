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

    <p v-else-if="!loading" class="empty">还没有添加友链～</p>

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
  margin: 0 0 24px;
  color: var(--text-muted);
  font-size: 13px;
}

.link-group {
  margin-bottom: 24px;
}

.group-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: #e04e82;
  padding-left: 10px;
  border-left: 3px solid #ffb3cd;
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.link-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-decoration: none;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.link-card:hover {
  transform: translateY(-3px);
  border-color: #ffb3cd;
  box-shadow: 0 6px 18px rgba(224, 78, 130, 0.15);
}

.link-avatar {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  color: #fff;
  font-weight: 700;
  font-size: 17px;
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
  font-size: 14px;
}

.link-card:hover .link-name {
  color: #e04e82;
}

.link-host {
  color: var(--text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-arrow {
  color: #d0c0d0;
  font-size: 14px;
  flex-shrink: 0;
}

.link-card:hover .link-arrow {
  color: #e04e82;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px;
}

.tip {
  margin-top: 28px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
