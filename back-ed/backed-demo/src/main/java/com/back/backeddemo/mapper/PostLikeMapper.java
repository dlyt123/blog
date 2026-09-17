package com.back.backeddemo.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostLikeMapper {

    int insert(@Param("userId") Long userId, @Param("postId") Long postId);

    int delete(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 注销账号时清空该用户的全部点赞 */
    int deleteByUser(@Param("userId") Long userId);

    /** 注销前先拿到他点过赞的文章 id，好把文章的点赞数一起扣掉 */
    List<Long> listLikedPostIds(@Param("userId") Long userId);

    int countByPost(@Param("postId") Long postId);

    int exists(@Param("userId") Long userId, @Param("postId") Long postId);
}
