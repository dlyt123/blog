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
        <button type="submit" class="anime-btn anime-btn--primary register-btn" :disabled="loading" @click="submit">
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
  padding: var(--space-10) var(--space-5) var(--space-8);
  min-height: 70vh;
}

.register-card {
  width: 420px;
  padding: var(--space-8);
}

.register-title {
  text-align: center;
  color: var(--brand-700);
  margin: 0 0 6px;
  font-size: var(--text-2xl);
  font-weight: 600;
}

.subtitle {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-sm);
  margin: 0 0 var(--space-6);
}

.register-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: var(--text-sm);
  color: var(--text-body);
}

/* 必填星号用语义色，而不是品牌色 —— "必填"是状态信息，不该跟主题色混在一起 */
.form-item .req {
  color: var(--danger);
}

/* 输入框外观统一由全局 .anime-input 提供，本页不再重复定义 */

/* 只定尺寸；渐变、内高光、辉光、按下反馈来自全局 .anime-btn--primary */
.register-btn {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  font-size: var(--text-md);
}

.hint {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-sm);
  margin: 4px 0 0;
}

.link {
  color: var(--brand-700);
  font-weight: 600;
}
</style>