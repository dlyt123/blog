<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserProfile, getUserPosts, followUser, unfollowUser } from '@/api'
import { useUserStore } from '@/store/user'
import PostCard from '@/components/PostCard.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const info = ref(null)
const posts = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const following = ref(false)
const followLoading = ref(false)
const notFound = ref(false)

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      page.value = 1
      loadProfile(id)
      loadPosts(id)
    }
  },
  { immediate: true }
)

async function loadProfile(id) {
  loading.value = true
  notFound.value = false
  try {
    const data = await getUserProfile(id)
    info.value = data
    following.value = !!data.isFollowing
  } catch (e) {
    // 404 等情况：展示「用户不存在」
    info.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

async function loadPosts(id) {
  try {
    const data = await getUserPosts(id, { page: page.value, pageSize: pageSize.value })
    posts.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    posts.value = []
    total.value = 0
  }
}

function changePage(p) {
  page.value = p
  loadPosts(route.params.id)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function toggleFollow() {
  if (!userStore.isLogin) {
    ElMessage.warning('登录后才能关注哦')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (followLoading.value) return
  followLoading.value = true
  try {
    if (following.value) {
      const res = await unfollowUser(info.value.user.id)
      following.value = false
      if (res && typeof res.followerCount === 'number') info.value.followerCount = res.followerCount
      ElMessage.info('已取消关注')
    } else {
      const res = await followUser(info.value.user.id)
      following.value = true
      if (res && typeof res.followerCount === 'number') info.value.followerCount = res.followerCount
      ElMessage.success('关注成功～')
    }
  } finally {
    followLoading.value = false
  }
}

function formatDate(t) {
  return t ? String(t).substring(0, 10) : ''
}

/** 给这个用户发私信 */
function sendMessage() {
  if (!userStore.isLogin) {
    ElMessage.warning('登录后才能发私信')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  router.push({ path: '/messages', query: { to: info.value.user.id } })
}
</script>

<template>
  <div>
    <div v-if="notFound" class="anime-card not-found">
      <p class="nf-code">404</p>
      <p class="nf-text">这个用户不存在</p>
      <el-button @click="router.push('/')">回到首页</el-button>
    </div>

    <template v-else-if="info">
      <div class="anime-card profile-card">
        <div class="avatar">
          <img v-if="info.user.avatar" :src="info.user.avatar" :alt="info.user.nickname" />
          <span v-else class="avatar-fallback">{{ (info.user.nickname || '?')[0] }}</span>
        </div>

        <div class="info">
          <h2 class="nickname">
            {{ info.user.nickname }}
            <el-tag v-if="info.user.role === 'ADMIN'" size="small" type="danger" effect="plain">站长</el-tag>
          </h2>
          <p class="username">@{{ info.user.username }}</p>
          <p class="joined">加入于 {{ formatDate(info.user.createTime) }}</p>
        </div>

        <div class="actions">
          <el-button v-if="info.isSelf" @click="router.push('/profile')">编辑我的资料</el-button>
          <el-button
            v-else
            :type="following ? 'default' : 'primary'"
            :loading="followLoading"
            @click="toggleFollow"
          >
            {{ following ? '已关注' : '+ 关注' }}
          </el-button>
          <el-button v-if="!info.isSelf" @click="sendMessage">✉️ 发私信</el-button>
        </div>
      </div>

      <div class="counts">
        <div class="count-item">
          <span class="count-value">{{ info.postCount ?? 0 }}</span>
          <span class="count-label">文章</span>
        </div>
        <div class="count-item">
          <span class="count-value">{{ info.followerCount ?? 0 }}</span>
          <span class="count-label">粉丝</span>
        </div>
        <div class="count-item">
          <span class="count-value">{{ info.followingCount ?? 0 }}</span>
          <span class="count-label">关注</span>
        </div>
      </div>

      <h3 class="section-title">📝 TA 的文章</h3>
      <div v-loading="loading">
        <PostCard v-for="post in posts" :key="post.id" :post="post" />
        <p v-if="!loading && !posts.length" class="empty">这位博主还没有发布文章～</p>
      </div>

      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="changePage"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.not-found {
  text-align: center;
  padding: 60px 24px;
}

.nf-code {
  font-size: 56px;
  font-weight: 800;
  margin: 0 0 8px;
  color: #ffb3cd;
}

.nf-text {
  margin: 0 0 20px;
  color: var(--text-body);
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  flex-wrap: wrap;
}

.avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  color: #fff;
  font-weight: 700;
  font-size: 30px;
}

.info {
  flex: 1;
  min-width: 160px;
}

.nickname {
  margin: 0 0 4px;
  font-size: 20px;
  color: var(--text-strong);
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  margin: 0 0 2px;
  color: var(--text-muted);
  font-size: 13px;
}

.joined {
  margin: 0;
  color: var(--text-faint);
  font-size: 12px;
}

.counts {
  display: flex;
  gap: 12px;
  margin: 16px 0 24px;
}

.count-item {
  flex: 1;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 14px;
  text-align: center;
}

.count-value {
  display: block;
  font-size: 20px;
  font-weight: 600;
  color: #e04e82;
}

.count-label {
  font-size: 12px;
  color: var(--text-muted);
}

.section-title {
  margin: 0 0 14px;
  font-size: 16px;
  color: var(--text-strong);
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
