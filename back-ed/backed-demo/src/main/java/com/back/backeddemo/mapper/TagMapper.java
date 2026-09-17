package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Tag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TagMapper {

    List<Tag> list();

    Tag findById(@Param("id") Long id);

    List<Tag> listByPostId(@Param("postId") Long postId);

    int insert(Tag tag);

    int update(Tag tag);

    int delete(@Param("id") Long id);
}
