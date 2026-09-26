package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.mapper.PostFavoriteMapper;
import com.back.backeddemo.mapper.PostLikeMapper;
import com.back.backeddemo.mapper.PostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞 / 收藏。
 *
 * <p><b>为什么要单独抽一个 Service 出来</b>：
 * 点赞看起来是"插一行记录"，实际上是**两次写**：
 * <ol>
 *   <li>往 {@code post_like} 插一条（或删一条）</li>
 *   <li>把 {@code post.likes} 这个计数字段 ±1</li>
 * </ol>
 * 这两步属于同一个业务动作，必须在一个事务里。
 * 以前这段逻辑写在 Controller 里直接调 Mapper，没有事务可挂 ——
 * 如果第 2 步失败（连接断了、超时、进程重启），就会留下
 * 「点赞记录存在、但文章点赞数没加」的**永久不一致**，而且不会自愈。
 *
 * <p>放在 Service 里之后，{@code @Transactional} 才有地方生效。
 */
@Service
public class InteractionService {

    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;

    public InteractionService(PostMapper postMapper,
                              PostLikeMapper postLikeMapper,
                              PostFavoriteMapper postFavoriteMapper) {
        this.postMapper = postMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
    }

    // ==================== 点赞 ====================

    /**
     * 点赞（幂等：同一个人重复点不报错、也不重复计数）。
     *
     * @return 操作后文章的点赞总数
     */
    @Transactional
    public int like(Long userId, Long postId) {
        requirePost(postId);
        if (postLikeMapper.exists(userId, postId) == 0) {
            postLikeMapper.insert(userId, postId);
            postMapper.incrementLikes(postId);
        }
        return likeCount(postId);
    }

    /**
     * 取消点赞（幂等）。
     *
     * @return 操作后文章的点赞总数
     */
    @Transactional
    public int unlike(Long userId, Long postId) {
        requirePost(postId);
        if (postLikeMapper.exists(userId, postId) > 0) {
            postLikeMapper.delete(userId, postId);
            postMapper.decrementLikes(postId);
        }
        return likeCount(postId);
    }

    public boolean isLiked(Long userId, Long postId) {
        return userId != null && postLikeMapper.exists(userId, postId) > 0;
    }

    /**
     * 文章当前点赞数，以 {@code post.likes} 为准
     * （保留种子数据里的初始值，不是简单的 count(*)）。
     */
    public int likeCount(Long postId) {
        Post p = postMapper.findById(postId);
        return (p != null && p.getLikes() != null) ? p.getLikes() : 0;
    }

    // ==================== 收藏 ====================

    @Transactional
    public int favorite(Long userId, Long postId) {
        requirePost(postId);
        if (postFavoriteMapper.exists(userId, postId) == 0) {
            postFavoriteMapper.insert(userId, postId);
        }
        return postFavoriteMapper.countByPost(postId);
    }

    @Transactional
    public int unfavorite(Long userId, Long postId) {
        requirePost(postId);
        if (postFavoriteMapper.exists(userId, postId) > 0) {
            postFavoriteMapper.delete(userId, postId);
        }
        return postFavoriteMapper.countByPost(postId);
    }

    public boolean isFavorited(Long userId, Long postId) {
        return userId != null && postFavoriteMapper.exists(userId, postId) > 0;
    }

    public int favoriteCount(Long postId) {
        return postFavoriteMapper.countByPost(postId);
    }

    // ==================== 辅助 ====================

    /**
     * 点赞 / 收藏前确认文章存在。
     *
     * <p>{@code post_like} / {@code post_favorite} 这两张表**没有外键约束**
     * （只有 user_id + post_id 的唯一索引），所以对不存在的文章点赞会插进一条
     * 永远不会被清理的孤儿记录。这里直接挡掉。
     */
    private void requirePost(Long postId) {
        if (postId == null || postMapper.findById(postId) == null) {
            throw new BusinessException(404, "文章不存在");
        }
    }
}
