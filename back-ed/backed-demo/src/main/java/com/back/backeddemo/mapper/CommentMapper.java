package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Comment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentMapper {

    List<Comment> listByPostId(@Param("postId") Long postId, @Param("status") Integer status);

    /** 顶层评论（分页，用于评论分页加载） */
    List<Comment> listTopByPostId(@Param("postId") Long postId, @Param("status") Integer status,
                                  @Param("offset") int offset, @Param("limit") int limit);

    long countTopByPostId(@Param("postId") Long postId, @Param("status") Integer status);

    /** 某几个顶层评论的楼中楼回复 */
    List<Comment> listRepliesByParentIds(@Param("ids") List<Long> ids);

    List<Comment> listAdmin(@Param("status") Integer status);

    /** 最新评论（公开，首页侧边栏用）：只取已通过的评论，联文章标题便于跳转 */
    List<Comment> listRecent(@Param("limit") int limit);

    int insert(Comment comment);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int delete(@Param("id") Long id);

    /** 按 id 查评论（回复通知需要拿父评论的作者） */
    Comment findById(@Param("id") Long id);

    /** 注销账号：评论保留，作者匿名 */
    int anonymizeUser(@Param("userId") Long userId);
}
