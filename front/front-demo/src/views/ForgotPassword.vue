<script setup>
import { ref } from 'vue'
import { forgotPassword } from '@/api'
import { ElMessage } from 'element-plus'

const email = ref('')
const loading = ref(false)
const sent = ref(false)

async function submit() {
  if (!email.value || !email.value.includes('@')) {
    ElMessage.warning('请输入正确的邮箱')
    return
  }
  loading.value = true
  try {
    await forgotPassword(email.value.trim())
    // 后端无论邮箱是否存在都返回成功，这里也只给中性提示，避免暴露「哪些邮箱注册过」
    sent.value = true
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <div class="anime-card card">
      <h2 class="title">找回密码</h2>

      <div v-if="!sent">
        <p class="tip">输入你注册时填的邮箱，我们会发一封重置邮件过去。</p>
        <form @submit.prevent="submit">
          <div class="form-item">
            <label>邮箱</label>
            <input v-model="email" type="email" class="anime-input" placeholder="you@example.com" />
          </div>
          <button type="submit" class="btn" :disabled="loading">
            {{ loading ? '发送中...' : '发送重置邮件' }}
          </button>
        </form>
      </div>

      <div v-else class="done">
        <p class="done-icon">📧</p>
        <p class="done-text">如果该邮箱已经注册，重置邮件已发送。</p>
        <p class="done-hint">请去邮箱查收（30 分钟内有效），没收到也检查一下垃圾邮件。</p>
      </div>

      <p class="hint"><router-link to="/login" class="link">返回登录</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.wrap { display: flex; justify-content: center; padding: 60px 20px 40px; min-height: 70vh; }
.card { width: 420px; padding: 40px 36px; }
.title { text-align: center; color: #e04e82; margin: 0 0 20px; font-size: 22px; }
.tip { color: #8a8a9a; font-size: 13px; line-height: 1.7; margin: 0 0 18px; }
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
