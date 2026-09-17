package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Media;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MediaMapper {

    List<Media> list();

    Media findById(@Param("id") Long id);

    int insert(Media media);

    int delete(@Param("id") Long id);
}
