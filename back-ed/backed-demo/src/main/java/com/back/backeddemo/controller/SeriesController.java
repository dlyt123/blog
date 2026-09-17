package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.common.Validate;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.Series;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.SeriesMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文章系列 / 专栏
 * 公开：GET /api/series、GET /api/series/{id}、GET /api/series/{id}/posts
 * 管理：POST / PUT / DELETE /api/admin/series（仅管理员，路径已在 WebConfig 登记）
 */
@RestController
public class SeriesController {

    private final SeriesMapper seriesMapper;
    private final PostMapper postMapper;

    public SeriesController(SeriesMapper seriesMapper, PostMapper postMapper) {
        this.seriesMapper = seriesMapper;
        this.postMapper = postMapper;
    }

    /** 公开：系列列表（含文章数） */
    @GetMapping("/api/series")
    public Result<List<Series>> list() {
        return Result.success(seriesMapper.list());
    }

    /** 公开：系列详情 */
    @GetMapping("/api/series/{id}")
    public Result<Series> detail(@PathVariable Long id) {
        Series s = seriesMapper.findById(id);
        if (s == null) {
            throw new BusinessException(404, "系列不存在");
        }
        return Result.success(s);
    }

    /** 公开：系列下的文章 */
    @GetMapping("/api/series/{id}/posts")
    public Result<PageResult<Post>> posts(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        List<Post> list = postMapper.listBySeries(id, (p - 1) * size, size);
        PageResult<Post> result = new PageResult<>();
        result.setList(list == null ? new ArrayList<>() : list);
        result.setTotal(postMapper.countBySeries(id));
        return Result.success(result);
    }

    // ===== 管理端 =====

    @PostMapping("/api/admin/series")
    public Result<Series> add(@RequestBody Series series) {
        series.setName(Validate.requiredText(series.getName(), "系列名称"));
        seriesMapper.insert(series);
        return Result.success(series);
    }

    @PutMapping("/api/admin/series/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Series series) {
        // 名称必填：否则动态 SET 可能拼成空语句，数据库会直接报语法错误
        series.setName(Validate.requiredText(series.getName(), "系列名称"));
        series.setId(id);
        seriesMapper.update(series);
        return Result.success();
    }

    @DeleteMapping("/api/admin/series/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        seriesMapper.detachPosts(id);
        seriesMapper.delete(id);
        return Result.success();
    }
}
