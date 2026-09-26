<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/')
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<template>
  <div class="notfound">
    <div class="anime-card card">
      <div class="code">404</div>
      <p class="msg">哎呀，这个页面走丢了～</p>
      <p class="sub">你访问的地址不存在，可能已经删除或链接有误。</p>

      <div class="actions">
        <button class="anime-btn anime-btn--primary" @click="goHome">回到首页</button>
        <button class="anime-btn anime-btn--secondary" @click="goBack">返回上一页</button>
      </div>

      <router-link to="/archives" class="archive-link">或者去归档页看看历史文章 →</router-link>
    </div>
  </div>
</template>

<style scoped>
.notfound {
  display: flex;
  justify-content: center;
  padding: var(--space-12) var(--space-5);
  min-height: 60vh;
}

.card {
  max-width: 480px;
  width: 100%;
  text-align: center;
  padding: var(--space-12) var(--space-8);
}

/* 404 用品牌渐变做"文字剪裁"，比纯色更有记忆点 */
.code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, var(--brand-500), var(--purple-500));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: var(--space-3);
}

.msg {
  margin: 0 0 6px;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-strong);
}

.sub {
  margin: 0 0 var(--space-6);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.actions {
  display: flex;
  gap: var(--space-3);
  justify-content: center;
  margin-bottom: var(--space-5);
}

/* 两个按钮的外观来自全局 .anime-btn--primary / .anime-btn--secondary */

.archive-link {
  color: var(--brand-700);
  font-size: var(--text-sm);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
