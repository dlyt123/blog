package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.SensitiveWord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SensitiveWordMapper {

    /** 取全部词（给内存缓存用，只要词本身） */
    List<String> listAllWords();

    /** 后台列表（可按词模糊搜索） */
    List<SensitiveWord> list(@Param("keyword") String keyword);

    long count(@Param("keyword") String keyword);

    long countAll();

    int insert(@Param("word") String word);

    int delete(@Param("id") Long id);
}
