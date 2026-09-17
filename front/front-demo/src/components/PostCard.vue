<script setup>
import { useRouter } from 'vue-router'
import defaultCover from '@/assets/anime-cover.webp'

const props = defineProps({
  post: { type: Object, required: true }
})

const router = useRouter()

function goDetail() {
  router.push(`/posts/${props.post.id}`)
}

// 点标签跳到标签页并按标签精确筛选（tags 字段只有名字，这里用名字让标签页自己解析出 id）
function goTag(tag) {
  router.push({ path: '/tags', query: { name: tag } })
}

// 点作者头像 / 昵称 → 进 TA 的主页（@click.stop 阻止冒泡，否则会同时触发进文章详情）
function goAuthor() {
  if (props.post.authorId) {
    router.push(`/users/${props.post.authorId}`)
  }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}
</script>

<template>
  <article class="anime-card post-card" @click="goDetail">
    <div class="post-main">
      <h2 class="post-title">
        <span v-if="post.pinned" class="pin-badge">置顶</span>
        {{ post.title }}
      </h2>
      <p class="post-summary">{{ post.summary || '（无摘要）' }}</p>
      <div class="post-meta">
        <!-- 作者：头像 + 昵称（来自数据库用户表）；点击进入 TA 的主页 -->
        <span class="author" @click.stop="goAuthor">
          <span class="author-avatar">
            <img v-if="post.authorAvatar" :src="post.authorAvatar" :alt="post.authorName" />
            <span v-else class="author-fallback">{{ (post.authorName || '?')[0] }}</span>
          </span>
          <span class="author-name">{{ post.authorName || '匿名' }}</span>
        </span>
        <span v-if="post.categoryName" class="category">📁 {{ post.categoryName }}</span>
        <span class="time">🕒 {{ formatTime(post.publishTime || post.createTime) }}</span>
        <span class="views">👀 {{ post.views }} 阅读</span>
        <span class="likes">💗 {{ post.likes }} 赞</span>
      </div>
      <div v-if="post.tags && post.tags.length" class="tags">
        <span v-for="tag in post.tags" :key="tag" class="anime-tag" @click.stop="goTag(tag)">{{ tag }}</span>
      </div>
    </div>
    <div class="post-cover">
      <img :src="post.cover || defaultCover" :alt="post.title" />
    </div>
  </article>
</template>

<style scoped>
.post-card {
  display: flex;
  gap: 16px;
  cursor: pointer;
  margin-bottom: 16px;
}

.post-main {
  flex: 1;
}

.post-title {
  margin: 0 0 10px;
  font-size: 20px;
  color: var(--text-strong);
}

.pin-badge {
  display: inline-block;
  background: #ff6b9d;
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  margin-right: 6px;
  vertical-align: middle;
}

.post-summary {
  color: var(--text-body);
  margin: 0 0 10px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.author:hover {
  opacity: 0.75;
  text-decoration: underline;
}

.author-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #ffd6e4, #d6f0fb);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.author-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-fallback {
  color: #fff;
  font-weight: 700;
  font-size: 12px;
}

.author-name {
  color: #e04e82;
  font-weight: 600;
}

.tags {
  margin-top: 4px;
}

.post-cover {
  flex-shrink: 0;
  width: 160px;
  height: 110px;
  border-radius: 10px;
  overflow: hidden;
}

.post-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ===== 手机端：封面图改到上方、整宽显示，正文占满一行 =====
   横排时 160px 封面 + 16px 间距 + 正文，正文只剩 130px 左右，
   标题会被挤成好几行，还会把卡片顶宽（实测溢出 4px）。 */
@media (max-width: 640px) {
  .post-card {
    flex-direction: column-reverse;
    gap: 12px;
  }

  .post-main {
    min-width: 0;
  }

  .post-cover {
    width: 100%;
    height: 150px;
  }

  .post-title {
    font-size: 17px;
  }

  .post-summary {
    font-size: 14px;
  }

  .post-meta {
    gap: 8px 14px;
    font-size: 12px;
  }
}
</style>
