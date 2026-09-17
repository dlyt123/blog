package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.VisitLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface VisitLogMapper {

    int insert(VisitLog log);

    /**
     * 后台查询访问记录
     * @param userType all=全部 / guest=仅未登录游客 / member=仅已登录用户
     */
    List<VisitLog> list(@Param("userType") String userType,
                        @Param("keyword") String keyword,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    long count(@Param("userType") String userType,
               @Param("keyword") String keyword);

    /** 概览统计：总记录数、今日 PV、今日 UV（按 IP 去重）、已登录访客数、游客数 */
    Map<String, Object> stats();

    /** 最近 N 天的 PV / UV 趋势（按天聚合，date 为 YYYY-MM-DD） */
    List<Map<String, Object>> trend(@Param("days") int days);

    /** 来源分析：按访问来源（referer）聚合，返回 source / pv / uv */
    List<Map<String, Object>> referrerStats(@Param("limit") int limit);

    /** 清空全部访问记录 */
    int clearAll();

    /** 只保留最近 N 天，其余删除（防止表无限膨胀） */
    int deleteOlderThan(@Param("days") int days);
}
