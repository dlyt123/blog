<script setup>
import { ref } from 'vue'
import { subscribe } from '@/api'
import { ElMessage } from 'element-plus'

const email = ref('')
const submitting = ref(false)
const done = ref(false)

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

async function submit() {
  const val = email.value.trim()
  if (!val) {
    ElMessage.warning('请输入邮箱地址')
    return
  }
  if (!EMAIL_RE.test(val)) {
    ElMessage.warning('邮箱格式不正确，请检查一下')
    return
  }
  submitting.value = true
  try {
    await subscribe({ email: val })
    done.value = true
    email.value = ''
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="subscribe">
    <div class="label">
      <span class="icon">📮</span>
      <div class="text">
        <strong>订阅更新</strong>
        <span class="sub">留下邮箱，有新文章时通知你</span>
      </div>
    </div>

    <div v-if="done" class="done">
      <span>✅ 订阅成功，感谢关注～</span>
      <button class="again" @click="done = false">再订一个</button>
    </div>

    <div v-else class="form">
      <input
        v-model="email"
        type="email"
        class="input"
        placeholder="你的邮箱地址"
        @keyup.enter="submit"
      />
      <button class="btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '订阅' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.subscribe {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: center;
  padding: 14px 18px;
  margin-bottom: 14px;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: #fff8fb;
}

.label {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon {
  font-size: 20px;
}

.text {
  display: flex;
  flex-direction: column;
  text-align: left;
}

.text strong {
  font-size: 13px;
  color: var(--text-strong);
  font-weight: 600;
}

.sub {
  font-size: 11px;
  color: var(--text-muted);
}

.form {
  display: flex;
  gap: 8px;
}

.input {
  width: 220px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  outline: none;
  background: var(--surface);
  box-sizing: border-box;
}

.input:focus {
  border-color: #e04e82;
}

.btn {
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.done {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #3b6d11;
}

.again {
  border: none;
  background: none;
  color: #e04e82;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}

@media (max-width: 640px) {
  .form {
    width: 100%;
  }
  .input {
    flex: 1;
    width: auto;
  }
}
</style>
