package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.ImageUtil;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Media;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.MediaMapper;
import com.back.backeddemo.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 媒体（图片）接口
 *
 * 权限说明（重要）：
 *   - 上传 POST /api/admin/media：**任何已登录用户**都可以（写文章要插图）
 *   - 列表 GET 与删除 DELETE：**仅管理员**
 *     （列表能看到全站图片，删除会影响别人文章里引用的图）
 *   因为「上传」和「列表 / 删除」权限不同，无法用 AdminOnlyInterceptor 按路径统一拦截，
 *   所以在方法里显式判断角色。
 */
@RestController
@RequestMapping("/api/admin/media")
public class MediaController {

    /** 只允许图片后缀：上传的文件会被 /uploads/** 同源访问，若允许 html/svg 会造成存储型 XSS */
    private static final Set<String> ALLOWED_EXT =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");

    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    private static final String UPLOAD_PREFIX = "/uploads/";

    @Value("${blog.upload.dir}")
    private String uploadDir;

    private final MediaMapper mediaMapper;
    private final UserMapper userMapper;

    public MediaController(MediaMapper mediaMapper, UserMapper userMapper) {
        this.mediaMapper = mediaMapper;
        this.userMapper = userMapper;
    }

    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.error(400, "图片大小不能超过 5MB");
        }
        // 1) Content-Type 必须是图片
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return Result.error(400, "只能上传图片文件");
        }
        // 2) 后缀必须在白名单内（扩展名直接来自用户，必须校验）
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            return Result.error(400, "只支持 jpg / png / gif / webp / bmp 格式的图片");
        }

        try {
            // 3) 先压缩再落盘：压缩可能把「无透明通道的 PNG」转成 JPEG 以省流量，
            //    所以要拿到最终格式后再决定文件后缀，避免后缀与实际内容不一致。
            byte[] raw = file.getBytes();
            String fmt = ext.startsWith(".") ? ext.substring(1) : ext;
            ImageUtil.CompressResult cr = ImageUtil.compress(raw, fmt, 1920, 0.85f);
            String finalExt = "." + cr.format();
            byte[] data = cr.data();

            // 4) 落盘文件名用随机 UUID + 最终后缀，杜绝路径穿越与可控文件名
            String filename = UUID.randomUUID().toString().replace("-", "") + finalExt;
            // 注意：必须 normalize()，否则配置里的 "./uploads/" 会残留 "." 段，
            // 导致下面的 startsWith(dir) 路径校验误判成「非法文件名」
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                return Result.error(400, "非法的文件名");
            }
            Files.write(target, data);

            Media media = new Media();
            media.setFilename(original);
            media.setUrl(UPLOAD_PREFIX + filename);
            media.setType("jpg".equals(cr.format()) ? "image/jpeg" : "image/" + cr.format());
            media.setSize((long) data.length);
            mediaMapper.insert(media);

            return Result.success(Map.of("url", media.getUrl()));
        } catch (Exception e) {
            return Result.error(500, "上传失败，请稍后重试");
        }
    }

    @GetMapping
    public Result<List<Media>> list(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(mediaMapper.list());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        Media media = mediaMapper.findById(id);
        mediaMapper.delete(id);
        // 同时删掉磁盘上的文件，否则空间会被一直占着
        if (media != null && media.getUrl() != null && media.getUrl().startsWith(UPLOAD_PREFIX)) {
            try {
                Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
                Path target = dir.resolve(media.getUrl().substring(UPLOAD_PREFIX.length())).normalize();
                if (target.startsWith(dir)) {
                    Files.deleteIfExists(target);
                }
            } catch (Exception ignored) {
                // 文件删不掉不影响业务
            }
        }
        return Result.success();
    }

    /** 列表与删除仅管理员可用 */
    private void requireAdmin(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        User user = (userId instanceof Long id) ? userMapper.findById(id) : null;
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new BusinessException(403, "仅管理员可访问此功能");
        }
    }
}
