package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.common.Validate;
import com.back.backeddemo.entity.Tag;
import com.back.backeddemo.mapper.TagMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TagController {

    private final TagMapper tagMapper;

    public TagController(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @GetMapping("/api/tags")
    public Result<List<Tag>> list() {
        return Result.success(tagMapper.list());
    }

    @PostMapping("/api/admin/tags")
    public Result<Map<String, Long>> create(@RequestBody Tag tag) {
        tag.setName(Validate.requiredText(tag.getName(), "标签名称"));
        tagMapper.insert(tag);
        return Result.success(Map.of("id", tag.getId()));
    }

    @PutMapping("/api/admin/tags/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Tag tag) {
        // 名称必填：否则动态 SET 可能拼成空语句，数据库会直接报语法错误
        tag.setName(Validate.requiredText(tag.getName(), "标签名称"));
        tag.setId(id);
        tagMapper.update(tag);
        return Result.success();
    }

    @DeleteMapping("/api/admin/tags/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagMapper.delete(id);
        return Result.success();
    }
}
