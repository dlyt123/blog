package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Post;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostFavoriteMapper {

    int insert(@Param("userId") Long userId, @Param("postId") Long postId);

    int delete(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 注销账号时清空该用户的全部收藏 */
    int deleteByUser(@Param("userId") Long userId);

    int exists(@Param("userId") Long userId, @Param("postId") Long postId);

    int countByPost(@Param("postId") Long postId);

    int countByUser(@Param("userId") Long userId);

    /** 「我的收藏」列表：按收藏时间倒序，只取已发布且未删除的文章 */
    List<Post> listByUser(@Param("userId") Long userId,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    /** 「我的收藏」总数，过滤条件必须与 listByUser 完全一致，否则分页数对不上 */
    long countActiveByUser(@Param("userId") Long userId);
}
