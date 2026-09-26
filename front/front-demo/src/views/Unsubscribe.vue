<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { checkUnsubscribe, unsubscribe } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const token = ref('')
const info = ref({ valid: false, email: '', unsubscribed: false })
const loading = ref(true)
const done = ref(false)

onMounted(async () => {
  token.value = route.query.token || ''
  if (!token.value) {
    loading.value = false
    return
  }
  try {
    info.value = (await checkUnsubscribe(token.value)) || {}
    // 已经退订过的，直接显示结果，不用再点一次
    if (info.value.unsubscribed) {
      done.value = true
    }
  } catch (e) {
    info.value = { valid: false }
  } finally {
    loading.value = false
  }
})

async function confirmUnsubscribe() {
  try {
    await unsubscribe(token.value)
    done.value = true
    ElMessage.success('已退订')
  } catch (e) {
    // 拦截器已提示
  }
}
</script>

<template>
  <div class="wrap">
    <div class="anime-card card">
      <h2 class="title">邮件订阅退订</h2>

      <div v-if="loading" class="done">
        <p class="done-text">正在校验链接…</p>
      </div>

      <div v-else-if="!token || !info.valid" class="done">
        <p class="done-icon">⚠️</p>
        <p class="done-text">退订链接无效或已失效</p>
        <p class="done-hint">链接可能已被使用过，或者复制时少了字符。</p>
      </div>

      <div v-else-if="done" class="done">
        <p class="done-icon">👋</p>
        <p class="done-text">已退订成功</p>
        <p class="done-hint">{{ info.email }} 不会再收到新文章通知了。</p>
        <p class="done-hint">想再订阅的话，随时回到网站底部重新填邮箱就行。</p>
      </div>

      <div v-else class="done">
        <p class="done-text">确认要退订吗？</p>
        <p class="done-hint">退订后，<b>{{ info.email }}</b> 将不再收到新文章通知。</p>
        <button class="anime-btn anime-btn--danger-solid anime-btn--lg anime-btn--block" @click="confirmUnsubscribe">确认退订</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  display: flex;
  justify-content: center;
  padding: var(--space-10) var(--space-5) var(--space-8);
  min-height: 70vh;
}

.card {
  width: 440px;
  padding: var(--space-10) var(--space-8);
}

.title {
  text-align: center;
  color: var(--brand-700);
  margin: 0 0 var(--space-6);
  font-size: var(--text-2xl);
  font-weight: 600;
}

.done {
  text-align: center;
}

.done-icon {
  font-size: 42px;
  line-height: 1;
  margin: 0 0 var(--space-3);
}

.done-text {
  color: var(--text-strong);
  margin: 0 0 10px;
  font-size: var(--text-md);
}

.done-hint {
  color: var(--text-muted);
  font-size: var(--text-sm);
  margin: 4px 0;
  line-height: 1.7;
}

/* 确认退订按钮的外观来自全局 .anime-btn--danger-solid，这里只补间距 */
.done .anime-btn {
  margin-top: var(--space-5);
}
</style>
