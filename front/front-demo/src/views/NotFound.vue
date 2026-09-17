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
        <button class="btn primary" @click="goHome">回到首页</button>
        <button class="btn ghost" @click="goBack">返回上一页</button>
      </div>

      <router-link to="/archives" class="archive-link">或者去归档页看看历史文章 →</router-link>
    </div>
  </div>
</template>

<style scoped>
.notfound {
  display: flex;
  justify-content: center;
  padding: 80px 20px;
  min-height: 60vh;
}

.card {
  max-width: 480px;
  width: 100%;
  text-align: center;
  padding: 48px 32px;
}

.code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, #ff6b9d, #c8b6e8);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 12px;
}

.msg {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-strong);
}

.sub {
  margin: 0 0 28px;
  color: var(--text-muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 20px;
}

.btn {
  height: 40px;
  padding: 0 22px;
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn.primary {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  font-weight: 600;
}

.btn.ghost {
  background: var(--surface);
  border-color: var(--border-soft);
  color: var(--text-body);
}

.archive-link {
  color: #e04e82;
  font-size: 13px;
  text-decoration: underline;
}
</style>
