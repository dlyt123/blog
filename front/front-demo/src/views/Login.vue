<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import DisclaimerDialog from '@/components/DisclaimerDialog.vue'
import { getCaptcha } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const form = ref({ username: '', password: '', captchaCode: '' })
const loading = ref(false)

// 图形验证码：{ key, image(base64) }
const captcha = ref({ key: '', image: '' })
const captchaLoading = ref(false)

// 必须先同意免责声明与隐私说明，才展示登录表单
const agreed = ref(localStorage.getItem('policyAgreed') === 'v1')

/** 拉取（或刷新）验证码 */
async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await getCaptcha()
    captcha.value = data || { key: '', image: '' }
    form.value.captchaCode = ''
  } catch (e) {
    // 拿不到验证码也不阻塞页面，点图可重试
  } finally {
    captchaLoading.value = false
  }
}

onMounted(loadCaptcha)

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!form.value.captchaCode) {
    ElMessage.warning('请输入图形验证码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.value.username, form.value.password, {
      captchaKey: captcha.value.key,
      captchaCode: form.value.captchaCode
    })
    // 登录成功后立刻拉取用户信息，写入 store
    await userStore.fetchMe().catch(() => {})
    ElMessage.success('登录成功，欢迎回来～')
    // 有 redirect 参数则回跳，否则去首页
    const redirect = route.query.redirect
    router.push(redirect || '/')
  } catch (e) {
    // 响应拦截器已弹 ElMessage.error，这里不再重复
    // 但验证码是一次性的，失败后必须换一张，否则重试必然还是错
    loadCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <!-- 未同意条款前只显示弹窗，不展示登录表单 -->
    <div v-if="agreed" class="anime-card login-card">
      <h2 class="login-title">登录</h2>
      <!-- 原生 form：用 @submit.prevent 阻止默认提交，键盘回车也能触发 -->
      <form class="login-form" @submit.prevent="submit">
        <div class="form-item">
          <label>用户名</label>
          <input
            v-model="form.username"
            type="text"
            class="anime-input"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </div>
        <div class="form-item">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            class="anime-input"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>
        <div class="form-item">
          <label>验证码</label>
          <div class="captcha-row">
            <input
              v-model="form.captchaCode"
              type="text"
              class="anime-input captcha-input"
              placeholder="输入图中字符"
              maxlength="6"
              autocomplete="off"
            />
            <img
              v-if="captcha.image"
              :src="captcha.image"
              class="captcha-img"
              title="看不清？点击换一张"
              alt="点击刷新验证码"
              @click="loadCaptcha"
            />
            <button v-else type="button" class="captcha-img captcha-retry" @click="loadCaptcha">
              {{ captchaLoading ? '加载中' : '点击加载' }}
            </button>
          </div>
        </div>
        <!-- type=submit 让回车也能登录；@click 再绑一次确保点击生效 -->
        <button
          type="submit"
          class="anime-btn login-btn"
          :disabled="loading"
          @click="submit"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </button>
        <p class="hint">
          <router-link to="/forgot-password" class="link">忘记密码？</router-link>
        </p>
        <p class="hint">
          还没有账号？
          <router-link to="/register" class="link">立即注册</router-link>
        </p>
      </form>
    </div>

    <!-- 进入登录页先弹免责声明与隐私说明 -->
    <DisclaimerDialog @agreed="agreed = true" />
  </div>
</template>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  padding: 60px 20px 40px;
  min-height: 70vh;
}

.login-card {
  width: 400px;
  padding: 40px 36px;
}

.login-title {
  text-align: center;
  color: #e04e82;
  margin: 0 0 28px;
  font-size: 22px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
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

/* 验证码：输入框 + 图片并排 */
.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  width: 120px;
  height: 42px;
  border-radius: 10px;
  border: 1px solid var(--border-soft);
  cursor: pointer;
  flex-shrink: 0;
  object-fit: cover;
  background: var(--surface-soft);
}

.captcha-retry {
  font-size: 12px;
  color: var(--text-muted);
}

.login-btn {
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
  transition: opacity 0.2s, transform 0.1s;
}

.login-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.login-btn:active:not(:disabled) {
  transform: translateY(1px);
}

.login-btn:disabled {
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
