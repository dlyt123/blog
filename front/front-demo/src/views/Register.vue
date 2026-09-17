<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import DisclaimerDialog from '@/components/DisclaimerDialog.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const form = ref({ username: '', password: '', confirm: '', nickname: '', email: '' })
const loading = ref(false)

// 必须先同意免责声明与隐私说明，才展示注册表单
const agreed = ref(localStorage.getItem('policyAgreed') === 'v1')

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.value.username.trim().length < 3) {
    ElMessage.warning('用户名至少 3 个字符')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (form.value.password !== form.value.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await userStore.register({
      username: form.value.username.trim(),
      password: form.value.password,
      nickname: form.value.nickname,
      email: form.value.email
    })
    await userStore.fetchMe().catch(() => {})
    ElMessage.success('注册成功，已自动登录～')
    router.push('/')
  } catch (e) {
    // 响应拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-wrap">
    <!-- 未同意条款前只显示弹窗，不展示注册表单 -->
    <div v-if="agreed" class="anime-card register-card">
      <h2 class="register-title">注册账号</h2>
      <p class="subtitle">注册后即可发布并管理自己的文章</p>
      <form class="register-form" @submit.prevent="submit">
        <div class="form-item">
          <label>用户名 <span class="req">*</span></label>
          <input v-model="form.username" type="text" class="anime-input" placeholder="至少 3 个字符，登录用" autocomplete="username" />
        </div>
        <div class="form-item">
          <label>昵称</label>
          <input v-model="form.nickname" type="text" class="anime-input" placeholder="显示在文章与评论中（选填）" />
        </div>
        <div class="form-item">
          <label>邮箱</label>
          <input v-model="form.email" type="email" class="anime-input" placeholder="选填" />
        </div>
        <div class="form-item">
          <label>密码 <span class="req">*</span></label>
          <input v-model="form.password" type="password" class="anime-input" placeholder="至少 6 位" autocomplete="new-password" />
        </div>
        <div class="form-item">
          <label>确认密码 <span class="req">*</span></label>
          <input v-model="form.confirm" type="password" class="anime-input" placeholder="再次输入密码" autocomplete="new-password" />
        </div>
        <button type="submit" class="anime-btn register-btn" :disabled="loading" @click="submit">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
        <p class="hint">
          已有账号？
          <router-link to="/login" class="link">去登录</router-link>
        </p>
      </form>
    </div>

    <!-- 进入注册页先弹免责声明与隐私说明 -->
    <DisclaimerDialog @agreed="agreed = true" />
  </div>
</template>

<style scoped>
.register-wrap {
  display: flex;
  justify-content: center;
  padding: 48px 20px 40px;
  min-height: 70vh;
}

.register-card {
  width: 420px;
  padding: 36px;
}

.register-title {
  text-align: center;
  color: #e04e82;
  margin: 0 0 6px;
  font-size: 22px;
}

.subtitle {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  margin: 0 0 24px;
}

.register-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: 13px;
  color: #6a6a7a;
}

.form-item .req {
  color: #e04e82;
}

.anime-input {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.anime-input:focus {
  border-color: #e04e82;
  box-shadow: 0 0 0 3px rgba(224, 78, 130, 0.1);
}

.register-btn {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.register-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  margin: 4px 0 0;
}

.link {
  color: #e04e82;
  font-weight: 600;
}
</style>