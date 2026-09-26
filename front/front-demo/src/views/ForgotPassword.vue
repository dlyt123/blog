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
          <button type="submit" class="anime-btn anime-btn--primary anime-btn--lg anime-btn--block" :disabled="loading">
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
  margin: 0 0 var(--space-5);
  font-size: var(--text-2xl);
  font-weight: 600;
}

.tip {
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: 1.7;
  margin: 0 0 var(--space-4);
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

/* 输入框和提交按钮的外观都来自全局类（.anime-input / .anime-btn--primary） */

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
