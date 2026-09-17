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
        <button type="submit" class="btn" :disabled="loading">
          {{ loading ? '提交中...' : '确认重置' }}
        </button>
      </form>

      <p class="hint"><router-link to="/login" class="link">返回登录</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.wrap { display: flex; justify-content: center; padding: 60px 20px 40px; min-height: 70vh; }
.card { width: 420px; padding: 40px 36px; }
.title { text-align: center; color: #e04e82; margin: 0 0 24px; font-size: 22px; }
.form-item { display: flex; flex-direction: column; gap: 6px; margin-bottom: 18px; }
.form-item label { font-size: 13px; color: #6a6a7a; }
.anime-input {
  width: 100%; height: 42px; padding: 0 14px; border: 1px solid var(--border-soft);
  border-radius: 10px; font-size: 14px; outline: none; box-sizing: border-box;
}
.anime-input:focus { border-color: #e04e82; box-shadow: 0 0 0 3px rgba(224, 78, 130, 0.1); }
.btn {
  width: 100%; height: 44px; border: none; border-radius: 10px; color: #fff;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5); font-size: 15px;
  font-weight: 600; cursor: pointer;
}
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.done { text-align: center; }
.done-icon { font-size: 42px; margin: 0 0 12px; }
.done-text { color: #5a5a6a; margin: 0 0 8px; }
.done-hint { color: var(--text-muted); font-size: 13px; margin: 0; }
.hint { text-align: center; margin: 20px 0 0; font-size: 13px; }
.link { color: #e04e82; font-weight: 600; }
</style>
