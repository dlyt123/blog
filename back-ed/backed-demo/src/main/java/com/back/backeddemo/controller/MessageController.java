package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Message;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.MessageMapper;
import com.back.backeddemo.mapper.UserMapper;
import com.back.backeddemo.service.SensitiveWordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信（需登录）
 * - POST   /api/messages                       发私信
 * - GET    /api/messages/conversations         会话列表
 * - GET    /api/messages/with/{userId}         与某人的聊天记录
 * - PUT    /api/messages/with/{userId}/read    标记已读
 * - GET    /api/messages/unread-count          未读总数（导航角标）
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final int MAX_LEN = 500;

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;

    public MessageController(MessageMapper messageMapper, UserMapper userMapper,
                             SensitiveWordService sensitiveWordService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    /** 发私信 */
    @PostMapping
    public Result<Void> send(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long me = requireLogin(request);
        Object toRaw = body.get("toUserId");
        String content = body.get("content") == null ? null : String.valueOf(body.get("content")).trim();
        if (toRaw == null) {
            throw new BusinessException(400, "缺少收信人");
        }
        if (content == null || content.isEmpty()) {
            throw new BusinessException(400, "私信内容不能为空");
        }
        if (content.length() > MAX_LEN) {
            throw new BusinessException(400, "私信最多 " + MAX_LEN + " 个字");
        }
        Long to;
        try {
            to = Long.parseLong(String.valueOf(toRaw).trim());
        } catch (NumberFormatException e) {
            // 参数不合法要给 400，不能让它变成 500
            throw new BusinessException(400, "收信人参数不正确");
        }
        if (to.equals(me)) {
            throw new BusinessException(400, "不能给自己发私信");
        }
        User target = userMapper.findById(to);
        if (target == null) {
            throw new BusinessException(404, "收信人不存在");
        }
        // 私信同样是用户生成内容，接敏感词过滤
        sensitiveWordService.validate(content, "发送私信");

        Message m = new Message();
        m.setFromUserId(me);
        m.setToUserId(to);
        m.setContent(content);
        messageMapper.insert(m);
        return Result.success();
    }

    /** 会话列表 */
    @GetMapping("/conversations")
    public Result<List<Message>> conversations(HttpServletRequest request) {
        Long me = requireLogin(request);
        List<Message> list = messageMapper.conversations(me);
        return Result.success(list == null ? new ArrayList<>() : list);
    }

    /** 与某人的聊天记录 + 对方信息 */
    @GetMapping("/with/{otherId}")
    public Result<Map<String, Object>> chat(@PathVariable Long otherId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "50") int pageSize,
                                            HttpServletRequest request) {
        Long me = requireLogin(request);
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        List<Message> list = messageMapper.chat(me, otherId, (p - 1) * size, size);

        User other = userMapper.findById(otherId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list == null ? new ArrayList<>() : list);
        data.put("total", messageMapper.countChat(me, otherId));
        data.put("myId", me);
        if (other != null) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", other.getId());
            o.put("name", other.getNickname() != null ? other.getNickname() : other.getUsername());
            o.put("avatar", other.getAvatar());
            data.put("other", o);
        }
        // 打开聊天即视为已读
        messageMapper.markRead(me, otherId);
        return Result.success(data);
    }

    /** 标记与某人的消息为已读 */
    @PutMapping("/with/{otherId}/read")
    public Result<Void> markRead(@PathVariable Long otherId, HttpServletRequest request) {
        Long me = requireLogin(request);
        messageMapper.markRead(me, otherId);
        return Result.success();
    }

    /** 未读私信总数 */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Long me = requireLogin(request);
        return Result.success(Map.of("count", messageMapper.unreadCount(me)));
    }

    private Long requireLogin(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
