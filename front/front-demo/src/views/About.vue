<script setup>
import { ref, onMounted } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { getAbout } from '@/api'

const html = ref('')

onMounted(async () => {
  const data = await getAbout()
  html.value = renderMarkdown(data.content)
})
</script>

<template>
  <div>
    <h2 class="page-title anime-title">👤 关于我</h2>
    <div class="anime-card">
      <div class="markdown-body" v-html="html"></div>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 24px;
  color: var(--text-strong);
}
</style>
