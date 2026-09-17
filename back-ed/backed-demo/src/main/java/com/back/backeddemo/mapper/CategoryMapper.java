package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {

    List<Category> list();

    Category findById(@Param("id") Long id);

    int insert(Category category);

    int update(Category category);

    int delete(@Param("id") Long id);
}
