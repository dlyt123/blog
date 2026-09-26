package com.back.backeddemo.common;

import com.back.backeddemo.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局异常处理 + 异常邮件告警
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 两次告警之间的最小间隔（毫秒），防止一个故障刷出几百封邮件 */
    private static final long ALERT_INTERVAL_MS = 5 * 60_000L;

    private final MailService mailService;

    /** 告警收件人；留空则不告警 */
    @Value("${blog.alert.email:}")
    private String alertEmail;

    /** 上次告警时间戳（0 = 还没告警过） */
    private final AtomicLong lastAlertAt = new AtomicLong(0);

    public GlobalExceptionHandler(MailService mailService) {
        this.mailService = mailService;
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        // 业务异常：message 是刻意写给用户看的，可以原样返回
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.error(404, "资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(405, "请求方式不支持");
    }

    // ==================== 客户端参数错误 → 400（不是 500，也不必告警）====================
    // 这类错误是「调用方传错了」，不是服务器故障。
    // 如果不单独处理，会掉进下面的兜底分支变成 500，
    // 既让调用方看不到原因，还会给管理员刷一堆无意义的告警邮件。

    /** 缺少必填的请求参数，例如 /api/search 没带 keyword */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(400, "缺少必要参数：" + e.getParameterName());
    }

    /** 参数类型不对，例如把 id 传成 "abc" */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(400, "参数格式不正确：" + e.getName());
    }

    /** 请求体不是合法 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.error(400, "请求内容格式不正确，请检查提交的数据");
    }

    /**
     * 上传请求不是 multipart 表单（没带文件、或 Content-Type 不对）。
     * 属于「调用方传错了」，不是服务器故障 —— 不加这个处理会掉进兜底分支变成 500，
     * 还会给管理员发一封没意义的告警邮件。
     */
    @ExceptionHandler(MultipartException.class)
    public Result<Void> handleMultipart(MultipartException e) {
        return Result.error(400, "上传请求格式不正确，请使用 multipart/form-data 提交文件");
    }

    /**
     * 上传文件超过 spring.servlet.multipart.max-file-size（当前 10MB）。
     * 这个异常是 MultipartException 的子类，会被上面的处理器兜住，
     * 但用户最需要知道的是「文件太大」，所以单独给一句更准的话。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleTooLarge(MaxUploadSizeExceededException e) {
        return Result.error(400, "文件太大了，请压缩后再上传");
    }

    /**
     * 数据库约束不满足：必填字段为空、长度超标、唯一键冲突等。
     * 本质是「提交的数据不合法」，返回 400 并提示具体方向（不泄露表名/列名等内部信息）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性校验未通过：{}", e.getMostSpecificCause().getMessage());
        return Result.error(400, "提交的数据不完整或不符合要求，请检查必填项和长度");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 其它异常：详情只写进服务端日志，
        // 不把 SQL 报错、类名、文件路径等内部信息返回给客户端
        log.error("未处理异常", e);
        sendAlert(e);
        return Result.error(500, "服务器开小差了，请稍后重试");
    }

    /** 未处理异常时给管理员发一封告警邮件（5 分钟内最多一封） */
    private void sendAlert(Exception e) {
        if (alertEmail == null || alertEmail.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastAlertAt.get();
        if (now - last < ALERT_INTERVAL_MS) {
            return;
        }
        if (!lastAlertAt.compareAndSet(last, now)) {
            return; // 并发下只让一个线程发
        }
        String body = "站点出现未处理异常：\n\n"
                + "异常类型：" + e.getClass().getName() + "\n"
                + "异常信息：" + e.getMessage() + "\n\n"
                + "详细堆栈请查看服务器日志（./logs/blog.log）。";
        mailService.sendText(alertEmail, "【博客告警】服务器出现异常", body);
    }
}
