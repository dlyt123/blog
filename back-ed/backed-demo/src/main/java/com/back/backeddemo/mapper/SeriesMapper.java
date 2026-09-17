package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Series;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SeriesMapper {
    List<Series> list();

    Series findById(@Param("id") Long id);

    int insert(Series series);

    int update(Series series);

    /** 删除系列前，先把属于该系列的文章的 series_id 清空 */
    int detachPosts(@Param("seriesId") Long seriesId);

    int delete(@Param("id") Long id);
}
