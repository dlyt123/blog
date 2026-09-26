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
        class="anime-input comment-textarea"
        rows="3"
        :placeholder="isLogin ? '说点什么吧～' : '请先登录后再评论'"
        :disabled="!isLogin"
      />
      <div class="form-actions">
        <button v-if="!isLogin" class="anime-btn anime-btn--primary" @click="goLogin">去登录</button>
        <button
          v-else
          class="anime-btn anime-btn--primary"
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
    <p v-else class="anime-empty">💬 还没有评论，快来抢沙发～</p>

    <div v-if="hasMore" class="load-more">
      <el-button plain @click="loadMore">加载更多评论</el-button>
    </div>
  </div>
</template>

<style scoped>
.comment-form {
  margin-bottom: var(--space-4);
}

.me-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.me-avatar,
.head-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  overflow: hidden;
  background: linear-gradient(135deg, var(--brand-200), var(--blue-300));
  display: flex;
  align-items: center;
  justify-content: center;
  /* 浅色渐变底上的首字母改成深色，白字在这块底上等于隐形 */
  color: var(--brand-800);
  font-weight: 700;
  flex-shrink: 0;
}

.head-avatar.small {
  width: 28px;
  height: 28px;
  font-size: var(--text-sm);
}

.me-avatar img,
.head-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  font-size: var(--text-lg);
}

.me-name strong {
  display: block;
  color: var(--text-strong);
  font-size: var(--text-md);
}

.me-tip {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

/* 输入框外观统一由全局 .anime-input 提供（圆角 10px、聚焦品牌色边框 + 3.5px 光环）。
   这里以前自己写了一份：圆角 10px 但聚焦用 #e04e82 + 3px 弱光环，
   和全站其它输入框的聚焦反馈不一样。已改用全局类，只保留尺寸差异。 */
.comment-textarea {
  line-height: 1.7;
}

.form-actions {
  margin-top: var(--space-3);
  display: flex;
  justify-content: flex-end;
}

/* 按钮外观统一由全局的 .anime-btn 提供（src/styles/anime.css）。
   这里以前自己写了一份 —— 而全站十几个页面各写了一份，
   结果同类按钮在不同页面的高度/圆角/字重都不一样。已删除。 */

.comment-item {
  margin-bottom: var(--space-3);
}

.comment-head,
.reply-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--space-2);
}

.head-info {
  display: flex;
  flex-direction: column;
}

.comment-nickname {
  font-weight: 600;
  color: var(--brand-700);
}

.comment-time {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.comment-content,
.reply-content {
  margin: 0;
  line-height: 1.7;
  color: var(--text-body);
}

.comment-replies {
  margin-top: 10px;
  padding: 10px var(--space-4);
  background: var(--surface-sunk);
  border-radius: var(--radius-md);
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
  color: var(--text-muted);
  font-weight: 600;
}

/* 头像与昵称可点击进用户主页 */
.clickable {
  cursor: pointer;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.clickable:hover {
  opacity: 0.75;
}

.comment-nickname.clickable:hover,
.reply-nickname.clickable:hover {
  text-decoration: underline;
  color: var(--brand-700);
}

.report-link {
  color: var(--text-faint);
  font-size: var(--text-xs);
  cursor: pointer;
  margin-left: auto;
  padding: 0 2px;
  transition: color var(--dur-fast) var(--ease-out);
}

.report-link:hover {
  color: var(--danger);
}

.load-more {
  text-align: center;
  margin: var(--space-4) 0;
}
</style>