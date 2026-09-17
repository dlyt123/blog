package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Post;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostMapper {

    // ===== 前台 =====
    List<Post> list(@Param("categoryId") Long categoryId,
                    @Param("tagId") Long tagId,
                    @Param("keyword") String keyword,
                    @Param("orderBy") String orderBy,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    long count(@Param("categoryId") Long categoryId,
               @Param("tagId") Long tagId,
               @Param("keyword") String keyword);

    Post findById(@Param("id") Long id);

    int incrementViews(@Param("id") Long id);

    int incrementLikes(@Param("id") Long id);

    int decrementLikes(@Param("id") Long id);

    Post findPrev(@Param("id") Long id);

    Post findNext(@Param("id") Long id);

    List<Post> listArchives();

    List<Post> listPopular(@Param("limit") int limit);

    /**
     * 相关推荐：优先「标签重合多」的，其次同分类，最后按阅读量兜底。
     * 排除自身，只取已发布未删除的。
     */
    List<Post> listRelated(@Param("id") Long id, @Param("limit") int limit);

    /** 关注流：我关注的人发布的文章 */
    List<Post> listByFollowees(@Param("followerId") Long followerId,
                               @Param("offset") int offset, @Param("limit") int limit);

    long countByFollowees(@Param("followerId") Long followerId);

    /** 某个系列下的文章（按发布时间正序，便于连载阅读） */
    List<Post> listBySeries(@Param("seriesId") Long seriesId,
                            @Param("offset") int offset, @Param("limit") int limit);

    long countBySeries(@Param("seriesId") Long seriesId);

    /** 数据导出：取全部未删除文章（含正文），供 Markdown 打包下载 */
    List<Post> listAllForExport();

    /** 定时发布：把到期的草稿转为已发布，返回影响行数 */
    int publishScheduled(@Param("now") java.time.LocalDateTime now);

    /** 注销账号：把该作者的文章改为「已注销用户」（内容保留，作者匿名） */
    int anonymizeAuthor(@Param("authorId") Long authorId);

    long sumViews();

    // ===== 后台 =====
    List<Post> listAdmin(@Param("status") Integer status,
                         @Param("keyword") String keyword,
                         @Param("authorId") Long authorId,
                         @Param("offset") int offset,
                         @Param("limit") int limit);

    long countAdmin(@Param("status") Integer status,
                    @Param("keyword") String keyword,
                    @Param("authorId") Long authorId);

    int insert(Post post);

    int update(Post post);

    int softDelete(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updatePinned(@Param("id") Long id, @Param("pinned") Integer pinned);

    int updateRecommended(@Param("id") Long id, @Param("recommended") Integer recommended);

    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);

    int batchSoftDelete(@Param("ids") List<Long> ids);

    int batchUpdateCategory(@Param("ids") List<Long> ids, @Param("categoryId") Long categoryId);

    // ===== 标签关联 =====
    int insertTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    int deleteTags(@Param("postId") Long postId);

    // ===== 回收站 =====
    List<Post> listTrash(@Param("keyword") String keyword,
                         @Param("offset") int offset,
                         @Param("limit") int limit);

    long countTrash(@Param("keyword") String keyword);

    int restore(@Param("id") Long id);

    int hardDelete(@Param("id") Long id);

    int hardDeleteLikes(@Param("id") Long id);

    int hardDeleteFavorites(@Param("id") Long id);

    int hardDeleteComments(@Param("id") Long id);
}
