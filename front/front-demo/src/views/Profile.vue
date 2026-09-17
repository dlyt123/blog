<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { updateProfile, uploadMedia, deleteAccount } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const form = ref({
  username: '',
  nickname: '',
  email: '',
  avatar: '',
  password: ''
})

const loading = ref(false)
const fileInput = ref(null)
const uploading = ref(false)

onMounted(async () => {
  if (!userStore.token) {
    router.push('/login')
    return
  }
  // 拉取最新用户信息
  const data = await userStore.fetchMe()
  if (data) {
    form.value.username = data.username || ''
    form.value.nickname = data.nickname || ''
    form.value.email = data.email || ''
    form.value.avatar = data.avatar || ''
  }
})

async function save() {
  if (form.value.password && form.value.password.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  loading.value = true
  try {
    const payload = {
      nickname: form.value.nickname,
      email: form.value.email,
      avatar: form.value.avatar
    }
    // 填了才改密码，留空表示不修改
    if (form.value.password) {
      payload.password = form.value.password
    }
    const updated = await updateProfile(payload)
    userStore.userInfo = updated
    form.value.password = ''
    ElMessage.success('保存成功～')
  } finally {
    loading.value = false
  }
}

/** 注销账号：需输入密码二次确认，不可恢复 */
async function handleDeleteAccount() {
  try {
    const { value } = await ElMessageBox.prompt(
      '注销后不可恢复：\n\n· 账号、私信、收藏、点赞、关注关系将被永久删除\n· 你发布的文章会保留，但作者显示为「已注销用户」\n\n请输入登录密码确认：',
      '注销账号',
      {
        confirmButtonText: '确认注销',
        cancelButtonText: '我再想想',
        inputType: 'password',
        inputPlaceholder: '输入登录密码'
      }
    )
    await deleteAccount(value)
    userStore.logout()
    ElMessage.success('账号已注销')
    router.push('/')
  } catch (e) {
    // 用户取消
  }
}

function logout() {
  userStore.logout()
  ElMessage.info('已退出登录')
  router.push('/login')
}

/** 点击「选择图片」 */
function pickFile() {
  fileInput.value?.click()
}

/** 选中本地图片后立即上传，拿到 URL 回填到头像 */
async function onFileChange(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件（jpg / png / gif / webp）')
    e.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    e.target.value = ''
    return
  }
  uploading.value = true
  try {
    const res = await uploadMedia(file)
    form.value.avatar = res.url
    ElMessage.success('头像上传成功，记得点击「保存修改」')
  } catch (err) {
    // 拦截器已提示
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}
</script>

<template>
  <div class="profile-wrap">
    <div class="anime-card profile-card">
      <h2 class="profile-title">个人信息</h2>

      <div class="avatar-row">
        <div class="avatar-preview">
          <img v-if="form.avatar" :src="form.avatar" :alt="form.nickname" />
          <span v-else class="avatar-fallback">{{ (form.nickname || form.username || '?')[0] }}</span>
        </div>
        <div class="avatar-tip">
          <p>点击按钮选择本地图片，上传后自动作为头像（≤ 5MB）</p>
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            class="hidden-file"
            @change="onFileChange"
          />
          <button class="anime-btn ghost" :disabled="uploading" @click="pickFile">
            {{ uploading ? '上传中...' : '选择图片' }}
          </button>
          <p v-if="form.avatar" class="avatar-path">当前头像：{{ form.avatar }}</p>
        </div>
      </div>

      <div class="form-grid">
        <div class="form-item">
          <label>登录用户名</label>
          <input class="anime-input" :value="form.username" disabled />
          <p class="form-hint">用户名不可修改</p>
        </div>

        <div class="form-item">
          <label>昵称</label>
          <input v-model="form.nickname" class="anime-input" placeholder="博客上显示的名称" />
        </div>

        <div class="form-item">
          <label>邮箱</label>
          <input v-model="form.email" class="anime-input" placeholder="选填，仅自己可见" />
          <p class="form-hint">填了邮箱才能用「忘记密码」找回账号</p>
        </div>

        <div class="form-item">
          <label>新密码</label>
          <input
            v-model="form.password"
            type="password"
            class="anime-input"
            placeholder="留空表示不修改"
            autocomplete="new-password"
          />
          <p class="form-hint">至少 6 位，填写后保存即生效</p>
        </div>
      </div>

      <div class="action-row">
        <button class="anime-btn primary" :disabled="loading" @click="save">
          {{ loading ? '保存中...' : '保存修改' }}
        </button>
        <button class="anime-btn logout-btn" @click="logout">退出登录</button>
      </div>

      <!-- 危险操作区 -->
      <div class="danger-zone">
        <h4 class="danger-title">危险操作</h4>
        <p class="danger-tip">
          注销账号会永久删除你的账号及个人数据，<b>不可恢复</b>。
          你发布的文章会保留，但作者信息会被匿名。
        </p>
        <button class="anime-btn danger-btn" @click="handleDeleteAccount">注销我的账号</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-wrap {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 20px;
}

.profile-card {
  padding: 32px;
}

.profile-title {
  color: #e04e82;
  margin: 0 0 24px;
  font-size: 22px;
}

.avatar-row {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px dashed var(--border-soft);
}

.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 28px;
  flex-shrink: 0;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-tip {
  flex: 1;
}

.avatar-tip p {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: 12px;
}

/* 隐藏原生 file input，用按钮触发 */
.hidden-file {
  display: none;
}

.anime-btn.ghost {
  background: var(--surface-pink);
  color: #e04e82;
  border: 1px solid #ffb3cd;
  height: 36px;
  padding: 0 18px;
}

.avatar-path {
  margin: 8px 0 0;
  color: #c0b0c0;
  font-size: 12px;
  word-break: break-all;
}

.form-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 24px;
}

.form-item label {
  display: block;
  font-size: 13px;
  color: #6a6a7a;
  margin-bottom: 6px;
}

.anime-input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s;
}

.anime-input:focus:not(:disabled) {
  border-color: #e04e82;
  box-shadow: 0 0 0 3px rgba(224, 78, 130, 0.1);
}

.anime-input:disabled {
  background: var(--surface-soft);
  color: #999;
  cursor: not-allowed;
}

.form-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.action-row {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.anime-btn {
  height: 42px;
  padding: 0 22px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.anime-btn.primary {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
}

.anime-btn.logout-btn {
  background: var(--surface);
  color: #888;
  border: 1px solid var(--border-soft);
}

.anime-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.anime-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ===== 危险操作区（注销账号）===== */
.danger-zone {
  margin-top: 36px;
  padding-top: 22px;
  border-top: 1px dashed var(--border-soft);
}

.danger-title {
  margin: 0 0 8px;
  font-size: 14px;
  color: #d05070;
}

.danger-tip {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.8;
}

.anime-btn.danger-btn {
  height: 38px;
  padding: 0 18px;
  font-size: 13px;
  background: var(--surface);
  color: #e24b4a;
  border: 1px solid #f0b6b5;
}

.anime-btn.danger-btn:hover {
  background: #fef2f2;
}
</style>