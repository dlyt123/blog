<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 进入登录 / 注册页前必须先同意的条款弹窗。
 *
 * 同意结果记在 localStorage，第二次进入不再打扰。
 * 如果用户不同意，可以离开弹窗（由父组件决定后续行为，一般回首页）。
 */
const props = defineProps({
  /** 记忆键：登录页和注册页共用一个，同意过就不再弹 */
  storageKey: { type: String, default: 'policyAgreed' },
  /** 条款版本：改动条款内容时把它 +1，老用户会被重新询问 */
  version: { type: String, default: 'v1' }
})

const emit = defineEmits(['agreed', 'declined'])

const router = useRouter()
const visible = ref(false)
const checked = ref(false)

onMounted(() => {
  const saved = localStorage.getItem(props.storageKey)
  visible.value = saved !== props.version
})

const points = [
  '本站是面向公众开放的内容平台，注册后即可发布文章与评论，发布内容仅代表发布者个人观点。',
  '本站会记录访问日志（IP、访问页面、来源、浏览器与系统），仅用于访问统计与安全防护，不用于商业用途、不对外提供。',
  '密码经 BCrypt 加盐哈希后存储，不保存明文，管理员也无法查看。',
  '禁止发布违法违规、色情暴力、诈骗广告、人身攻击以及侵犯他人权益的内容，违规内容将被删除，严重者封禁账号。',
  '本站为个人技术项目，不承诺服务不中断，请勿作为唯一的数据存储渠道，重要内容请自行备份。',
  '参考本站技术文章进行操作所产生的任何后果，由操作者自行承担。'
]

function agree() {
  if (!checked.value) return
  localStorage.setItem(props.storageKey, props.version)
  visible.value = false
  emit('agreed')
}

function decline() {
  emit('declined')
  router.push('/')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="mask">
      <div class="dialog">
        <h3 class="title">免责声明与隐私说明</h3>
        <p class="intro">
          在登录或注册之前，请先阅读并同意以下条款。完整内容见「用户协议 / 隐私政策 / 免责声明」页面。
        </p>

        <ul class="points">
          <li v-for="(p, i) in points" :key="i">{{ p }}</li>
        </ul>

        <router-link to="/policy" class="more">查看完整条款 →</router-link>

        <label class="check">
          <input v-model="checked" type="checkbox" />
          <span>我已阅读并同意《用户协议》《隐私政策》与《免责声明》</span>
        </label>

        <div class="actions">
          <button class="btn ghost" @click="decline">不同意</button>
          <button class="btn primary" :disabled="!checked" @click="agree">同意并继续</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(58, 58, 74, 0.45);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.dialog {
  width: 100%;
  max-width: 560px;
  max-height: 86vh;
  overflow-y: auto;
  background: var(--surface);
  border-radius: 16px;
  padding: 26px 28px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
}

.title {
  margin: 0 0 10px;
  font-size: 18px;
  color: #e04e82;
}

.intro {
  margin: 0 0 14px;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.7;
}

.points {
  margin: 0 0 14px;
  padding-left: 20px;
  color: var(--text-strong);
  font-size: 13px;
  line-height: 1.8;
}

.points li {
  margin: 7px 0;
}

.more {
  display: inline-block;
  margin-bottom: 18px;
  color: #e04e82;
  font-size: 13px;
  text-decoration: underline;
}

.check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  background: #fff8fb;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-strong);
  line-height: 1.6;
}

.check input {
  margin-top: 3px;
  accent-color: #e04e82;
  flex-shrink: 0;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn {
  height: 38px;
  padding: 0 20px;
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn.primary {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  font-weight: 600;
}

.btn.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn.ghost {
  background: var(--surface);
  border-color: var(--border-soft);
  color: #888;
}

@media (max-width: 560px) {
  .dialog {
    padding: 20px 18px;
  }
}
</style>
