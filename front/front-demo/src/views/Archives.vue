<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getArchives } from '@/api'

const router = useRouter()
const archives = ref([])

onMounted(async () => {
  archives.value = await getArchives()
})

function go(id) {
  router.push(`/posts/${id}`)
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">🗂️ 文章归档</h2>

    <div v-if="archives.length" class="timeline">
      <div v-for="year in archives" :key="year.year" class="year-block">
        <h3 class="year-title">{{ year.year }} 年</h3>
        <div v-for="month in year.months" :key="month.month" class="month-block">
          <h4 class="month-title">{{ month.month }} 月</h4>
          <div class="anime-card month-posts">
            <div
              v-for="p in month.posts"
              :key="p.id"
              class="archive-item"
              tabindex="0"
              @click="go(p.id)"
              @keydown.enter="go(p.id)"
            >
              <span class="date">{{ p.createTime }}</span>
              <span class="title">{{ p.title }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <p v-else class="anime-empty">📭 还没有归档内容～</p>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 var(--space-5);
  font-size: var(--text-2xl);
  color: var(--text-strong);
}

/* 年份是这一页的一级分组，用品牌色做视觉锚点 */
.year-title {
  color: var(--brand-700);
  font-size: var(--text-xl);
  font-weight: 700;
  margin: var(--space-5) 0 var(--space-3);
}

.month-title {
  color: var(--text-body);
  font-size: var(--text-md);
  font-weight: 600;
  margin: 10px 0 var(--space-2);
  padding-left: var(--space-2);
}

.month-posts {
  padding: var(--space-2) var(--space-4);
}

.archive-item {
  display: flex;
  gap: var(--space-4);
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-soft);
  cursor: pointer;
}

.archive-item:last-child {
  border-bottom: none;
}

.archive-item .date {
  color: var(--text-muted);
  font-size: var(--text-sm);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.archive-item .title {
  color: var(--text-strong);
  transition: color var(--dur-fast) var(--ease-out);
}

.archive-item:hover .title {
  color: var(--brand-700);
}
</style>
