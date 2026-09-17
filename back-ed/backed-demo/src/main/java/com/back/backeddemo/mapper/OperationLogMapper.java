package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.OperationLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OperationLogMapper {

    int insert(OperationLog log);

    List<OperationLog> list(@Param("keyword") String keyword,
                            @Param("offset") int offset, @Param("limit") int limit);

    long count(@Param("keyword") String keyword);

    /** 清理 N 天以前的日志（避免无限增长） */
    int deleteBefore(@Param("days") int days);

    int clear();
}
