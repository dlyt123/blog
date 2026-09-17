package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.SensitiveLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SensitiveLogMapper {
    int insert(SensitiveLog log);

    List<SensitiveLog> list(@Param("offset") int offset, @Param("limit") int limit);

    long count();

    int clear();
}
