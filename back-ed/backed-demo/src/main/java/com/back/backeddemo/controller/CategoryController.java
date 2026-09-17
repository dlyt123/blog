package com.back.backeddemo.controller;

import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.common.Validate;
import com.back.backeddemo.entity.Category;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.mapper.CategoryMapper;
import com.back.backeddemo.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CategoryController {

    private final CategoryMapper categoryMapper;
    private final PostService postService;

    public CategoryController(CategoryMapper categoryMapper, PostService postService) {
        this.categoryMapper = categoryMapper;
        this.postService = postService;
    }

    @GetMapping("/api/categories")
    public Result<List<Category>> list() {
        return Result.success(categoryMapper.list());
    }

    @GetMapping("/api/categories/{id}/posts")
    public Result<PageResult<Post>> posts(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(postService.list(id, null, null, "latest", page, pageSize));
    }

    @PostMapping("/api/admin/categories")
    public Result<Map<String, Long>> create(@RequestBody Category category) {
        category.setName(Validate.requiredText(category.getName(), "分类名称"));
        categoryMapper.insert(category);
        return Result.success(Map.of("id", category.getId()));
    }

    @PutMapping("/api/admin/categories/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Category category) {
        // 名称必填：否则动态 SET 可能拼成空语句，数据库会直接报语法错误
        category.setName(Validate.requiredText(category.getName(), "分类名称"));
        category.setId(id);
        categoryMapper.update(category);
        return Result.success();
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryMapper.delete(id);
        return Result.success();
    }
}
