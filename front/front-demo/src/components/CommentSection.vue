<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getComments, addComment, submitReport } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  postId: { type: [Number, String], required: true }
})

const router = useRouter()
const userStore = useUserStore()

const comments = ref([])
const content = ref('')
const submitting = ref(false)
const page = ref(1)
const pageSize = ref(10)
const totalTop = ref(0)

const isLogin = computed(() => !!userStore.token)
const currentUser = computed(() => userStore.userInfo || {})

/** 点评论人头像/昵称 → 进 TA 的用户主页（老评论可能没有 userId，跳过） */
function goUser(userId) {
  if (userId) {
    router.push(`/users/${userId}`)
  }
}

/** 举报评论 */
async function reportComment(c) {
  if (!userStore.isLogin) {
    ElMessage.warning('登录后才能举报')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请填写举报理由（选填）', '举报这条评论', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：广告、辱骂、垃圾信息'
    })
    await submitReport({ targetType: 'comment', targetId: c.id, reason: value || '' })
    ElMessage.success('举报已提交，感谢你的反馈')
  } catch (e) {
    // 取消
  }
}

/** 评论总数（顶层评论数，来自后端分页 total） */
const totalCount = computed(() => totalTop.value)
const hasMore = computed(() => comments.value.length < totalTop.value)

onMounted(async () => {
  await load()
  // 顺手拉一次用户信息（确保头像昵称是最新的）
  if (isLogin.value) {
    userStore.fetchMe().catch(() => {})
  }
})

// 切换文章（上一篇/下一篇）时组件被复用，需要重新拉取该文章的评论
watch(
  () => props.postId,
  () => {
    content.value = ''
    load()
  }
)

async function load(reset = true) {
  if (reset) {
    page.value = 1
    comments.value = []
  }
  try {
    const data = await getComments(props.postId, { page: page.value, pageSize: pageSize.value })
    const list = data.list || []
    comments.value = reset ? list : comments.value.concat(list)
    totalTop.value = data.total || 0
  } catch (e) {
    comments.value = []
  }
}

function loadMore() {
  page.value++
  load(false)
}

function goLogin() {
  router.push('/login')
}

async function submit() {
  if (!isLogin.value) {
    ElMessage.warning('请先登录后再评论')
    goLogin()
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    // 后端会从 token 里取 userId 并自动从数据库填昵称/头像，这里只传 content
    await addComment(props.postId, { content: content.value })
    ElMessage.success('评论发表成功')
    content.value = ''
    // 免审核，刷新后立刻可见
    await load()
  } finally {
    submitting.value = false
  }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}
</script>

<template>
  <div class="comment-section">
    <h3 class="anime-title">💬 评论 ({{ totalCount }})</h3>

    <!-- 发表评论区：显示当前登录用户头像与昵称（来自数据库） -->
    <div class="anime-card comment-form">
      <div class="me-row">
        <div class="me-avatar">
          <img v-if="currentUser.avatar" :src="currentUser.avatar" :alt="currentUser.nickname || currentUser.username" />
          <span v-else class="avatar-fallback">{{ ((currentUser.nickname || currentUser.username) || '?')[0] }}</span>
        </div>
        <div class="me-name">
          <strong>{{ currentUser.nickname || currentUser.username || '未登录' }}</strong>
          <span class="me-tip">发表评论时将自动使用此昵称与头像</span>
        </div>
      </div>

      <textarea
        v-model="content"
        class="comment-textarea"
        rows="3"
        :placeholder="isLogin ? '说点什么吧～' : '请先登录后再评论'"
        :disabled="!isLogin"
      />
      <div class="form-actions">
        <button v-if="!isLogin" class="anime-btn primary" @click="goLogin">去登录</button>
        <button
          v-else
          class="anime-btn primary"
          :disabled="submitting"
          @click="submit"
        >
          {{ submitting ? '提交中...' : '发表评论' }}
        </button>
      </div>
    </div>

    <!-- 评论列表 -->
    <div v-if="comments.length" class="comment-list">
      <div v-for="c in comments" :key="c.id" class="comment-item anime-card">
        <div class="comment-head">
          <div class="head-avatar clickable" @click="goUser(c.userId)">
            <img v-if="c.avatar" :src="c.avatar" :alt="c.nickname" />
            <span v-else class="avatar-fallback">{{ (c.nickname || '?')[0] }}</span>
          </div>
          <div class="head-info">
            <span class="comment-nickname clickable" @click="goUser(c.userId)">{{ c.nickname }}</span>
            <span class="comment-time">{{ formatTime(c.createTime) }}</span>
            <span class="report-link" @click="reportComment(c)">举报</span>
          </div>
        </div>
        <p class="comment-content">{{ c.content }}</p>
        <div v-if="c.replies && c.replies.length" class="comment-replies">
          <div v-for="r in c.replies" :key="r.id" class="reply-item">
            <div class="reply-head">
              <div class="head-avatar small clickable" @click="goUser(r.userId)">
                <img v-if="r.avatar" :src="r.avatar" :alt="r.nickname" />
                <span v-else class="avatar-fallback">{{ (r.nickname || '?')[0] }}</span>
              </div>
              <span class="reply-nickname clickable" @click="goUser(r.userId)">{{ r.nickname }}</span>
              <span class="comment-time">{{ formatTime(r.createTime) }}</span>
            </div>
            <p class="reply-content">{{ r.content }}</p>
          </div>
        </div>
      </div>
    </div>
    <p v-else class="empty">还没有评论，快来抢沙发～</p>

    <div v-if="hasMore" class="load-more">
      <el-button plain @click="loadMore">加载更多评论</el-button>
    </div>
  </div>
</template>

<style scoped>
.comment-form {
  margin-bottom: 16px;
}

.me-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.me-avatar,
.head-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}

.head-avatar.small {
  width: 28px;
  height: 28px;
  font-size: 13px;
}

.me-avatar img,
.head-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  font-size: 16px;
}

.me-name strong {
  display: block;
  color: var(--text-strong);
  font-size: 15px;
}

.me-tip {
  color: var(--text-muted);
  font-size: 12px;
}

.comment-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
  transition: border-color 0.2s;
}

.comment-textarea:focus {
  border-color: #e04e82;
  box-shadow: 0 0 0 3px rgba(224, 78, 130, 0.1);
}

.comment-textarea:disabled {
  background: var(--surface-soft);
  cursor: not-allowed;
}

.form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.anime-btn {
  height: 38px;
  padding: 0 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(135deg, #ff6b9d, #ff8fb5);
  color: #fff;
  transition: opacity 0.2s;
}

.anime-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.anime-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.comment-item {
  margin-bottom: 12px;
}

.comment-head,
.reply-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.head-info {
  display: flex;
  flex-direction: column;
}

.comment-nickname {
  font-weight: 600;
  color: #e04e82;
}

.comment-time {
  color: var(--text-muted);
  font-size: 12px;
}

.comment-content,
.reply-content {
  margin: 0;
  line-height: 1.6;
  color: var(--text-strong);
}

.comment-replies {
  margin-top: 10px;
  padding: 10px 14px;
  background: #faf2f6;
  border-radius: 10px;
}

.reply-item {
  padding: 6px 0;
}

.reply-item + .reply-item {
  border-top: 1px dashed var(--border-soft);
  margin-top: 6px;
  padding-top: 10px;
}

.reply-nickname {
  color: #8a7a9a;
  font-weight: 600;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 20px;
}
/* 头像与昵称可点击进用户主页 */
.clickable {
  cursor: pointer;
  transition: opacity 0.2s;
}

.clickable:hover {
  opacity: 0.75;
}

.comment-nickname.clickable:hover,
.reply-nickname.clickable:hover {
  text-decoration: underline;
  color: #e04e82;
}

.report-link {
  color: var(--text-faint);
  font-size: 12px;
  cursor: pointer;
  margin-left: auto;
}

.report-link:hover {
  color: #e24b4a;
}

.load-more {
  text-align: center;
  margin: 16px 0;
}
</style>