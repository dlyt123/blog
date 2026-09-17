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
              @click="go(p.id)"
            >
              <span class="date">{{ p.createTime }}</span>
              <span class="title">{{ p.title }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <p v-else class="empty">还没有归档内容～</p>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 24px;
  color: var(--text-strong);
}

.year-title {
  color: #e04e82;
  font-size: 20px;
  margin: 20px 0 12px;
}

.month-title {
  color: var(--text-body);
  margin: 10px 0 8px;
  padding-left: 8px;
}

.month-posts {
  padding: 8px 16px;
}

.archive-item {
  display: flex;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-soft);
  cursor: pointer;
}

.archive-item:last-child {
  border-bottom: none;
}

.archive-item .date {
  color: var(--text-muted);
  font-size: 13px;
  flex-shrink: 0;
}

.archive-item .title {
  color: var(--text-strong);
}

.archive-item:hover .title {
  color: #e04e82;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px;
}
</style>
