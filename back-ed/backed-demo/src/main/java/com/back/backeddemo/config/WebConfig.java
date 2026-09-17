package com.back.backeddemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 配置：拦截器 + 跨域 + 静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RequireLoginInterceptor requireLoginInterceptor;
    private final AdminOnlyInterceptor adminOnlyInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final OperationLogInterceptor operationLogInterceptor;

    @Value("${blog.upload.dir}")
    private String uploadDir;

    @Value("${blog.cors.allowed-origins}")
    private String allowedOrigins;

    public WebConfig(JwtInterceptor jwtInterceptor,
                     RequireLoginInterceptor requireLoginInterceptor,
                     AdminOnlyInterceptor adminOnlyInterceptor,
                     RateLimitInterceptor rateLimitInterceptor,
                     OperationLogInterceptor operationLogInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.requireLoginInterceptor = requireLoginInterceptor;
        this.adminOnlyInterceptor = adminOnlyInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.operationLogInterceptor = operationLogInterceptor;
    }

    /**
     * 拦截器顺序很重要，注册顺序 = 执行顺序：
     *   ① 限流 → ② 解析 token（软鉴权）→ ③ 判断是否必须登录 → ④ 判断是否必须是管理员
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ① 限流：登录 / 注册 / 评论 / 上传等写操作按 IP 限次
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        // ② 解析 token：带合法 token 就识别出用户身份；匿名 / 过期 token 一律当作访客放行
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**");

        // ③ 登录校验：读接口公开（首页、文章、分类、标签、归档、搜索…），
        //    写接口 / 后台 / 个人资料 / 点赞收藏 需要登录
        registry.addInterceptor(requireLoginInterceptor)
                .addPathPatterns("/api/**");

        // ④ 管理员专属：分类/标签/评论/友链/设置/统计 的后台管理
        //    （文章管理不在此列，由 AdminPostController 内部按「管理员 / 本人」细粒度校验）
        registry.addInterceptor(adminOnlyInterceptor)
                .addPathPatterns(
                        "/api/admin/categories/**",
                        "/api/admin/tags/**",
                        "/api/admin/comments/**",
                        "/api/admin/links/**",
                        "/api/admin/settings/**",
                        "/api/admin/stats/**",
                        "/api/admin/subscribes/**",
                        "/api/admin/visits/**",
                        "/api/admin/sensitive-words/**",
                        "/api/admin/sensitive-logs/**",
                        "/api/admin/reports/**",
                        "/api/admin/series/**",
                        "/api/admin/export/**",
                        "/api/admin/backup/**",
                        "/api/admin/operation-logs/**",
                        "/api/admin/mail/**"
                );

        // ⑤ 操作日志（审计）：只记非 GET 的后台请求，注册在最外层，业务抛异常也能记到
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 只放行配置里列出的前端来源（默认仅本地开发地址），
        // 不要用 "*" 配 allowCredentials(true)——那等于允许任意站点带凭证访问后端。
        // 同源部署（Nginx 同时托管前端与后端）时其实不需要 CORS。
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadPath);
    }
}
