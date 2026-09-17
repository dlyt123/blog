package com.back.backeddemo.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserFollowMapper {

    int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 注销账号时清空该用户的全部关注关系（两个方向都清） */
    int deleteByUser(@Param("userId") Long userId);

    int exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 我的粉丝数 */
    long countFollowers(@Param("userId") Long userId);

    /** 我关注的人数 */
    long countFollowing(@Param("userId") Long userId);

    /** 我关注的人的 id 列表（用于「关注的人」页与文章流） */
    List<Long> listFollowingIds(@Param("userId") Long userId);
}
