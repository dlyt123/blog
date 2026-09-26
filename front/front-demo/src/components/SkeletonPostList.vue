<script setup>
/**
 * 列表页骨架屏。
 *
 * 为什么不用 v-loading 的转圈遮罩：
 * 遮罩只告诉用户"在等"，骨架屏还额外告诉他"等的是什么、有几条、多高"，
 * 视觉上不会跳版，感知等待时间明显更短。
 *
 * 用法：
 *   <SkeletonPostList v-if="loading && !posts.length" :count="3" />
 * 注意要和真实内容用同一套卡片骨架（.anime-card.--flat + 同样的 flex 布局），
 * 否则加载完成的一瞬间会"跳"一下，反而更难看。
 */
defineProps({
  /** 渲染几条占位卡片 */
  count: { type: Number, default: 3 },
  /** 是否显示右侧封面占位块（移动端布局会把它隐藏，和 PostCard 保持一致） */
  cover: { type: Boolean, default: true }
})
</script>

<template>
  <!-- aria-hidden：骨架屏是纯视觉占位，对读屏器应该完全不可见，
       真正的加载状态由外层的 aria-busy / 加载文案表达 -->
  <div class="skeleton-list" aria-hidden="true">
    <div v-for="i in count" :key="i" class="anime-card anime-card--flat sk-card">
      <div class="sk-main">
        <div class="anime-skeleton sk-title"></div>
        <div class="anime-skeleton sk-line"></div>
        <div class="anime-skeleton sk-line sk-line--short"></div>
        <div class="sk-meta">
          <div class="anime-skeleton sk-avatar"></div>
          <div class="anime-skeleton sk-chip"></div>
          <div class="anime-skeleton sk-chip sk-chip--wide"></div>
        </div>
      </div>
      <div v-if="cover" class="anime-skeleton sk-cover"></div>
    </div>
  </div>
</template>

<style scoped>
.skeleton-list {
  display: block;
}

/* 骨架卡片的尺寸、间距、圆角都和 PostCard 对齐，加载完不会跳版 */
.sk-card {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  cursor: default;
}

/* 骨架卡片不该有 hover 上浮 —— 它是"还不存在的内容" */
.sk-card:hover {
  transform: none;
  box-shadow: var(--shadow-xs);
  border-color: var(--border-soft);
}

.sk-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sk-title {
  height: 22px;
  width: 62%;
  border-radius: var(--radius-xs);
}

.sk-line {
  height: 12px;
  width: 100%;
}

.sk-line--short {
  width: 78%;
}

.sk-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-2);
}

.sk-avatar {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.sk-chip {
  height: 12px;
  width: 64px;
  border-radius: var(--radius-xs);
}

.sk-chip--wide {
  width: 96px;
}

/* 封面占位块的尺寸必须和 PostCard 的 .post-cover 完全一致（160×110），
   差几像素加载完就会看到卡片高度跳一下 */
.sk-cover {
  width: 160px;
  height: 110px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
}

@media (max-width: 640px) {
  .sk-card {
    flex-direction: column-reverse;
    gap: 12px;
  }

  .sk-cover {
    width: 100%;
    height: 150px;
  }
}
</style>
