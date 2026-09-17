package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Report;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReportMapper {
    int insert(Report report);

    List<Report> list(@Param("status") Integer status);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    long countPending();
}
