package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Link;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LinkMapper {

    List<Link> list();

    int insert(Link link);

    int update(Link link);

    int delete(@Param("id") Long id);
}
