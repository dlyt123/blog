<script setup>
import { ref, onMounted, nextTick } from 'vue'
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
      class="chat-layout anime-card"
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
          @click="openChat(c)"
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
              class="msg-input"
              type="text"
              maxlength="500"
              placeholder="输入消息，回车发送"
              @keyup.enter="send"
            />
            <button class="send-btn" :disabled="sending || !input.trim()" @click="send">
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
.page-title { margin: 0 0 16px; font-size: 24px; color: var(--text-strong); }
.chat-layout {
  display: flex;
  height: 620px;
  padding: 0;
  overflow: hidden;
}
.conv-list {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-soft);
  overflow-y: auto;
}
.conv-empty { text-align: center; padding: 40px 16px; color: var(--text-muted); font-size: 13px; }
.conv-empty-icon { font-size: 32px; margin: 0 0 8px; }
.conv-empty-hint { font-size: 12px; color: var(--text-faint); margin: 6px 0 0; }
.conv-item {
  display: flex; gap: 10px; padding: 12px 14px; cursor: pointer;
  border-bottom: 1px solid #faf3f7; transition: background 0.2s;
}
.conv-item:hover { background: var(--surface-pink); }
.conv-item.active { background: var(--surface-pink); }
.conv-avatar {
  width: 40px; height: 40px; border-radius: 50%; flex-shrink: 0; overflow: hidden;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb); color: #fff; font-weight: 700;
  display: flex; align-items: center; justify-content: center; font-size: 15px;
}
.conv-avatar img { width: 100%; height: 100%; object-fit: cover; }
.conv-main { flex: 1; min-width: 0; }
.conv-top { display: flex; justify-content: space-between; align-items: baseline; gap: 6px; }
.conv-name { font-size: 14px; color: var(--text-strong); font-weight: 600; }
.conv-time { font-size: 11px; color: var(--text-faint); flex-shrink: 0; }
.conv-bottom { display: flex; justify-content: space-between; align-items: center; gap: 6px; margin-top: 3px; }
.conv-last {
  font-size: 12px; color: var(--text-muted); overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap;
}
.conv-badge {
  background: #ff6b9d; color: #fff; font-size: 11px; border-radius: 999px;
  padding: 1px 6px; flex-shrink: 0;
}
.chat-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; border-bottom: 1px solid var(--border-soft);
}
.chat-name { font-weight: 600; color: var(--text-strong); }
.chat-profile { font-size: 12px; color: #e04e82; }
.chat-body { flex: 1; overflow-y: auto; padding: 18px; background: #fdfafc; }
.msg-row { margin-bottom: 14px; display: flex; flex-direction: column; align-items: flex-start; }
.msg-row.mine { align-items: flex-end; }
.msg-bubble {
  max-width: 70%; padding: 9px 13px; border-radius: 12px; font-size: 14px;
  line-height: 1.6; word-break: break-word; white-space: pre-wrap;
  background: var(--surface); color: var(--text-strong); border: 1px solid #f2e6ee;
}
.msg-row.mine .msg-bubble {
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5); color: #fff; border-color: transparent;
}
.msg-time { font-size: 11px; color: var(--text-faint); margin-top: 4px; }
.chat-empty { text-align: center; color: var(--text-muted); font-size: 13px; padding: 40px 0; }
.chat-input { display: flex; gap: 10px; padding: 12px 16px; border-top: 1px solid var(--border-soft); }
.msg-input {
  flex: 1; height: 40px; padding: 0 14px; border: 1px solid var(--border-soft);
  border-radius: 10px; font-size: 14px; outline: none;
}
.msg-input:focus { border-color: #e04e82; }
.send-btn {
  width: 84px; height: 40px; border: none; border-radius: 10px; color: #fff;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5); font-size: 14px;
  font-weight: 600; cursor: pointer;
}
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.chat-placeholder {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; color: var(--text-muted); font-size: 13px;
}
.ph-icon { font-size: 40px; margin: 0 0 10px; }

/* 返回按钮只在手机上出现 */
.chat-back {
  display: none;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: #e04e82;
  cursor: pointer;
  padding: 0 8px 0 0;
}

/* ===== 手机端：一次只显示一栏 =====
   分栏布局在 390px 下每栏只剩不到 200px，两边都挤得没法用。
   改成：默认显示会话列表；选了会话就整屏显示聊天，用「←」返回。 */
@media (max-width: 768px) {
  .page-title {
    font-size: 20px;
    margin-bottom: 12px;
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
    padding: 12px 14px;
  }

  .chat-body {
    padding: 14px;
  }

  .msg-bubble {
    max-width: 82%;
  }

  .chat-input {
    padding: 10px 12px;
  }

  .send-btn {
    width: 68px;
  }
}
</style>
