<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resetPassword } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const token = ref('')
const form = ref({ password: '', confirm: '' })
const loading = ref(false)
const done = ref(false)

onMounted(() => {
  token.value = route.query.token || ''
})

async function submit() {
  if (!form.value.password || form.value.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (form.value.password !== form.value.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword({ token: token.value, password: form.value.password })
    done.value = true
    ElMessage.success('密码已重置')
    setTimeout(() => router.push('/login'), 1500)
  } catch (e) {
    // 拦截器已提示（链接无效或过期）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <div class="anime-card card">
      <h2 class="title">重置密码</h2>

      <div v-if="!token" class="done">
        <p class="done-icon">⚠️</p>
        <p class="done-text">链接不完整，缺少重置令牌。</p>
        <p class="done-hint">请从邮件里的链接进入，或者重新申请一次。</p>
      </div>

      <div v-else-if="done" class="done">
        <p class="done-icon">✅</p>
        <p class="done-text">密码已重置成功</p>
        <p class="done-hint">正在跳转到登录页…</p>
      </div>

      <form v-else @submit.prevent="submit">
        <div class="form-item">
          <label>新密码</label>
          <input v-model="form.password" type="password" class="anime-input" placeholder="至少 6 位" />
        </div>
        <div class="form-item">
          <label>确认新密码</label>
          <input v-model="form.confirm" type="password" class="anime-input" placeholder="再输一次" />
        </div>
        <button type="submit" class="anime-btn anime-btn--primary anime-btn--lg anime-btn--block" :disabled="loading">
          {{ loading ? '提交中...' : '确认重置' }}
        </button>
      </form>

      <p class="hint"><router-link to="/login" class="link">返回登录</router-link></p>
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
  width: 420px;
  padding: var(--space-10) var(--space-8);
}

.title {
  text-align: center;
  color: var(--brand-700);
  margin: 0 0 var(--space-6);
  font-size: var(--text-2xl);
  font-weight: 600;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: var(--space-4);
}

.form-item label {
  font-size: var(--text-sm);
  color: var(--text-body);
}

/* 输入框和提交按钮的外观都来自全局类（.anime-input / .anime-btn--primary），
   本页不再重复定义，避免"同一个控件在不同页面长得不一样"。 */

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
  margin: 0 0 var(--space-2);
}

.done-hint {
  color: var(--text-muted);
  font-size: var(--text-sm);
  margin: 0;
}

.hint {
  text-align: center;
  margin: var(--space-5) 0 0;
  font-size: var(--text-sm);
}

.link {
  color: var(--brand-700);
  font-weight: 600;
}
</style>
