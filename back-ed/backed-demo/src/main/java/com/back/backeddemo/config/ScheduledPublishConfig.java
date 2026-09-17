package com.back.backeddemo.config;

import com.back.backeddemo.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 定时发布 + 异步邮件等基础设施开关。
 */
@Configuration
@EnableScheduling
@EnableAsync
public class ScheduledPublishConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishConfig.class);

    private final PostMapper postMapper;

    public ScheduledPublishConfig(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    @Scheduled(fixedDelay = 60_000)
    public void publishScheduled() {
        try {
            int n = postMapper.publishScheduled(LocalDateTime.now());
            if (n > 0) {
                log.info("定时发布任务：自动发布了 {} 篇文章", n);
            }
        } catch (Exception e) {
            // 定时任务里的异常不能往外抛，否则会中断整个调度
            log.warn("定时发布任务执行失败：{}", e.getMessage());
        }
    }
}
