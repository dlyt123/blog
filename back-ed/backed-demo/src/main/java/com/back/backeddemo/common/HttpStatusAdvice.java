package com.back.backeddemo.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 让 HTTP 状态码与业务码保持一致。
 *
 * <p>改造前：所有响应都是 HTTP 200，真正的结果藏在 body 的 code 里。
 * 这样前端能用，但监控、WAF、Nginx 日志都看不出请求到底成功还是失败
 * （比如暴力破解登录，日志里全是 200，完全无法告警）。
 *
 * <p>改造后：code=401 就返回 HTTP 401，403 返回 403，429 返回 429……
 * body 仍然是原来的统一结构 { code, message, data }，所以**前端完全不用改**
 * （前端拦截器本来就同时处理「业务码 401」和「HTTP 401」两种情况）。
 *
 * <p>实现方式是 Spring MVC 的 ResponseBodyAdvice：在写响应体之前统一调整状态码。
 * 因为它对「控制器正常返回的 Result」和「全局异常处理器返回的 Result」都生效，
 * 所以只需要这一个类，不用改任何 Controller。
 */
@RestControllerAdvice
public class HttpStatusAdvice implements ResponseBodyAdvice<Result<?>> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 只处理统一响应结构 Result，其它类型（如 RSS / sitemap 的 XML 字符串）不动
        return Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Result<?> beforeBodyWrite(Result<?> body,
                                     MethodParameter returnType,
                                     MediaType selectedContentType,
                                     Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                     ServerHttpRequest request,
                                     ServerHttpResponse response) {
        if (body != null) {
            HttpStatus status = toHttpStatus(body.getCode());
            if (status != null) {
                response.setStatusCode(status);
            }
        }
        return body;
    }

    /**
     * 业务码 → HTTP 状态码。
     * 只映射语义明确的那几个，其余一律按 200 处理，
     * 避免出现 600 这种非法 HTTP 状态码导致响应异常。
     */
    private HttpStatus toHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 405 -> HttpStatus.METHOD_NOT_ALLOWED;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> null; // 200 及其它业务码：保持 HTTP 200
        };
    }
}
