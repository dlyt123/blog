<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getConversations, getChat, sendMessage } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const conversations = ref([])
const current = ref(null)   // { id, name, avatar }
const messages = ref([])
const input = ref('')
const loading = ref(false)
const sending = ref(false)
const listRef = ref(null)

/* ===== 定时拉取（轮询）=====
   本站没有 WebSocket / SSE，所以用轮询模拟"实时收到消息"。

   之前的问题：只有 onMounted、切会话、发送后各拉一次，
   所以对方发来的消息必须刷新页面、或自己也发一条才看得到。

   现在的策略：
     · 当前会话消息  每 4 秒拉一次 → 对方发来的会自动出现
     · 会话列表      每 10 秒拉一次 → 新会话和未读角标会更新
     · 页面切到后台时暂停（省流量也省服务器），切回来立即刷一次

   ⚠️ 后端 GET /messages/with/{id} 会【顺带把消息标记为已读】，
      所以轮询只在会话打开期间跑 —— 这正好符合"打开着就是在看"的语义，
      不会出现"人在别的页面、消息却被悄悄标成已读"的情况。 */
const CHAT_INTERVAL = 4000
const CONV_INTERVAL = 10000
let chatTimer = null
let convTimer = null

function startPolling() {
  stopPolling()
  chatTimer = setInterval(pollChat, CHAT_INTERVAL)
  convTimer = setInterval(pollConversations, CONV_INTERVAL)
}

function stopPolling() {
  if (chatTimer) {
    clearInterval(chatTimer)
    chatTimer = null
  }
  if (convTimer) {
    clearInterval(convTimer)
    convTimer = null
  }
}

/** 拉当前会话的消息；只有真的变了才更新 */
async function pollChat() {
  // 正在加载 / 正在发送时不插手，避免和它们抢着覆盖 messages
  if (!current.value || loading.value || sending.value || document.hidden) {
    return
  }
  try {
    const data = await getChat(current.value.id, { page: 1, pageSize: 100 })
    const list = data.list || []
    // 条数没变就什么都不做 —— 避免无意义的重渲染，
    // 也避免打断用户正在阅读历史消息时的滚动位置
    if (list.length === messages.value.length) {
      return
    }
    const atBottom = isNearBottom()
    messages.value = list
    // 只有当用户本来就停在底部时才自动滚下去，
    // 否则会把正在翻旧消息的人硬拽到底部
    if (atBottom) {
      scrollToBottom()
    }
  } catch (e) {
    // 轮询失败静默处理：网络抖一下就弹提示会很烦，下一轮会自己恢复
  }
}

/** 拉会话列表（新会话 + 未读角标） */
async function pollConversations() {
  if (document.hidden) {
    return
  }
  try {
    conversations.value = (await getConversations()) || []
  } catch (e) {
    // 同上，静默
  }
}

/** 用户当前是否停在底部附近 */
function isNearBottom() {
  const el = listRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 60
}

function onVisibilityChange() {
  if (document.hidden) {
    stopPolling()
  } else {
    // 切回来的瞬间先补一次，再恢复轮询
    pollConversations()
    pollChat()
    startPolling()
  }
}

onMounted(async () => {
  await loadConversations()
  // 从用户主页点「发私信」过来：直接打开与 TA 的会话
  const to = route.query.to
  if (to) {
    const exist = conversations.value.find((c) => String(c.otherId) === String(to))
    openChat(exist || { otherId: Number(to) })
  } else if (conversations.value.length) {
    openChat(conversations.value[0])
  }
  document.addEventListener('visibilitychange', onVisibilityChange)
  startPolling()
})

onUnmounted(() => {
  // 离开页面必须清掉定时器和监听，否则会一直偷偷发请求
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

const myId = () => userStore.userInfo?.id

async function loadConversations() {
  conversations.value = (await getConversations()) || []
}

async function openChat(conv) {
  const id = Number(conv.otherId)
  current.value = { id, name: conv.otherName || '用户', avatar: conv.otherAvatar }
  loading.value = true
  try {
    const data = await getChat(id, { page: 1, pageSize: 100 })
    messages.value = data.list || []
    if (data.other) {
      current.value = { id, name: data.other.name || '用户', avatar: data.other.avatar }
    }
    await loadConversations()
    scrollToBottom()
    // 换会话时把 URL 同步一下，方便刷新后仍停在这个会话
    router.replace({ path: '/messages', query: { to: id } })
  } catch (e) {
    messages.value = []
  } finally {
    loading.value = false
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || !current.value) {
    return
  }
  sending.value = true
  try {
    await sendMessage({ toUserId: current.value.id, content: text })
    input.value = ''
    const data = await getChat(current.value.id, { page: 1, pageSize: 100 })
    messages.value = data.list || []
    scrollToBottom()
    loadConversations()
  } catch (e) {
    // 拦截器已提示
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

function isMine(m) {
  return m.fromUserId === myId()
}

function initial(name) {
  return (name || 'U').charAt(0).toUpperCase()
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(5, 16)
}
</script>

<template>
  <div>
    <h2 class="page-title anime-title">✉️ 私信</h2>

    <div
      class="chat-layout anime-card anime-card--flat"
      :class="{ 'has-current': !!current, 'is-empty': !conversations.length }"
    >
      <!-- 左：会话列表 -->
      <aside class="conv-list">
        <div v-if="!conversations.length" class="conv-empty">
          <p class="conv-empty-icon">📭</p>
          <p>还没有私信</p>
          <p class="conv-empty-hint">去别人的主页点「发私信」开始聊天</p>
        </div>
        <div
          v-for="c in conversations"
          :key="c.otherId"
          class="conv-item"
          :class="{ active: current && current.id === c.otherId }"
          tabindex="0"
          @click="openChat(c)"
          @keydown.enter="openChat(c)"
        >
          <div class="conv-avatar">
            <img v-if="c.otherAvatar" :src="c.otherAvatar" alt="" />
            <span v-else>{{ initial(c.otherName) }}</span>
          </div>
          <div class="conv-main">
            <div class="conv-top">
              <span class="conv-name">{{ c.otherName || '用户' }}</span>
              <span class="conv-time">{{ formatTime(c.lastTime) }}</span>
            </div>
            <div class="conv-bottom">
              <span class="conv-last">{{ c.lastContent }}</span>
              <span v-if="c.unread > 0" class="conv-badge">{{ c.unread }}</span>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右：聊天区 -->
      <section class="chat-panel">
        <template v-if="current">
          <header class="chat-header">
            <!-- 手机上回到会话列表（宽屏不显示） -->
            <button class="chat-back" title="返回会话列表" @click="current = null">←</button>
            <span class="chat-name">{{ current.name }}</span>
            <router-link :to="`/users/${current.id}`" class="chat-profile">查看主页</router-link>
          </header>

          <div ref="listRef" v-loading="loading" class="chat-body">
            <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: isMine(m) }">
              <div class="msg-bubble">{{ m.content }}</div>
              <div class="msg-time">{{ formatTime(m.createTime) }}</div>
            </div>
            <p v-if="!loading && !messages.length" class="chat-empty">
              还没有聊天记录，打个招呼吧～
            </p>
          </div>

          <footer class="chat-input">
            <input
              v-model="input"
              class="anime-input msg-input"
              type="text"
              maxlength="500"
              placeholder="输入消息，回车发送"
              @keyup.enter="send"
            />
            <button class="anime-btn anime-btn--primary send-btn" :disabled="sending || !input.trim()" @click="send">
              {{ sending ? '发送中' : '发送' }}
            </button>
          </footer>
        </template>

        <div v-else class="chat-placeholder">
          <p class="ph-icon">💬</p>
          <p>选择一个会话开始聊天</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 var(--space-4);
  font-size: var(--text-2xl);
  color: var(--text-strong);
}

/* 聊天主体：一张固定高度的卡片，左右两栏各自滚动。
   卡片本身不参与 .anime-card 的悬停上浮（整个面板会晃），所以是 --flat。 */
.chat-layout {
  display: flex;
  height: 620px;
  padding: 0;
  overflow: hidden;
}

/* ---------- 左栏：会话列表 ---------- */
.conv-list {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-soft);
  overflow-y: auto;
}

.conv-empty {
  text-align: center;
  padding: var(--space-10) var(--space-4);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.conv-empty-icon {
  font-size: 32px;
  margin: 0 0 var(--space-2);
}

.conv-empty-hint {
  font-size: var(--text-xs);
  color: var(--text-faint);
  margin: var(--space-2) 0 0;
  line-height: 1.6;
}

.conv-item {
  display: flex;
  gap: 10px;
  padding: var(--space-3) 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-softer);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.conv-item:hover {
  background: var(--surface-pink);
}

.conv-item.active {
  background: var(--surface-pink);
  box-shadow: inset 3px 0 0 var(--brand-500);
}

.conv-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
  overflow: hidden;
  background: linear-gradient(135deg, var(--brand-200), var(--blue-300));
  /* 浅色渐变底用品牌深色字，白字在这里读不出来 */
  color: var(--brand-800);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-md);
}

.conv-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.conv-main {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 6px;
}

.conv-name {
  font-size: var(--text-base);
  color: var(--text-strong);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: var(--text-faint);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}

.conv-last {
  font-size: var(--text-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-badge {
  background: var(--brand-500);
  color: var(--text-on-brand);
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--radius-full);
  padding: 1px 6px;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

/* ---------- 右栏：聊天区 ---------- */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-soft);
}

.chat-name {
  font-weight: 600;
  color: var(--text-strong);
}

.chat-profile {
  font-size: var(--text-xs);
  color: var(--brand-700);
  flex-shrink: 0;
}

.chat-profile:hover {
  text-decoration: underline;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  background: var(--surface-soft);
}

.msg-row {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.msg-row.mine {
  align-items: flex-end;
}

/* 气泡：靠"缺一个角"来暗示说话人，比只换颜色更直观 */
.msg-bubble {
  max-width: 70%;
  padding: 9px 13px;
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-lg) var(--radius-xs);
  font-size: var(--text-base);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  background: var(--surface);
  color: var(--text-strong);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-xs);
}

.msg-row.mine .msg-bubble {
  background: linear-gradient(135deg, var(--brand-500), var(--brand-400));
  color: var(--text-on-brand);
  border-color: transparent;
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-xs) var(--radius-lg);
  box-shadow: var(--shadow-brand);
}

.msg-time {
  font-size: 11px;
  color: var(--text-faint);
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}

.chat-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-sm);
  padding: var(--space-10) 0;
}

.chat-input {
  display: flex;
  gap: 10px;
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border-soft);
}

/* 输入框与发送按钮的外观都来自全局（.anime-input / .anime-btn--primary），
   本地只定尺寸 —— 以前这里又写了一份自己的粉色渐变，和全站主按钮不是一套。 */
.msg-input {
  flex: 1;
  min-width: 0;
  /* 覆盖全局 .anime-input 的 width: 100%：
     在 flex 行里 100% + flex:1 会让输入框顶破容器 */
  width: auto;
}

.send-btn {
  width: 84px;
  flex-shrink: 0;
}

.chat-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.ph-icon {
  font-size: 40px;
  margin: 0;
}

/* 返回按钮只在手机上出现 */
.chat-back {
  display: none;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: var(--brand-700);
  cursor: pointer;
  padding: 0 8px 0 0;
}

/* ===== 手机端：一次只显示一栏 =====
   分栏布局在 390px 下每栏只剩不到 200px，两边都挤得没法用。
   改成：默认显示会话列表；选了会话就整屏显示聊天，用「←」返回。 */
@media (max-width: 768px) {
  .page-title {
    font-size: var(--text-xl);
    margin-bottom: var(--space-3);
  }

  .chat-layout {
    flex-direction: column;
    height: calc(100vh - 190px);
    min-height: 420px;
  }

  /* 一条会话都没有时不用撑满整屏，否则是一大片空白 */
  .chat-layout.is-empty {
    height: auto;
    min-height: 0;
  }

  .conv-list {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border-soft);
  }

  /* 未选中会话：只显示列表 */
  .chat-panel {
    display: none;
  }

  /* 选中会话：只显示聊天 */
  .chat-layout.has-current .conv-list {
    display: none;
  }

  .chat-layout.has-current .chat-panel {
    display: flex;
    flex: 1;
  }

  .chat-back {
    display: block;
  }

  .chat-header {
    padding: var(--space-3) 14px;
  }

  .chat-body {
    padding: 14px;
  }

  .msg-bubble {
    max-width: 82%;
  }

  .chat-input {
    padding: 10px var(--space-3);
  }

  .send-btn {
    width: 68px;
  }
}
</style>
