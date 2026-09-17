package com.back.backeddemo.config;

import com.back.backeddemo.mapper.OperationLogMapper;
import com.back.backeddemo.mapper.VisitLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期数据定时清理。
 *
 * <p>为什么必须做：访客记录（visit_log）和操作日志（operation_log）是**只增不减**的表，
 * 一个正常访问量的站点一年能积累几十万行。不清理的话：
 * 磁盘越占越多、后台列表越查越慢、备份文件越来越大。
 *
 * <p>保留策略可配（默认访客 90 天、操作日志 180 天），每天凌晨 4:00 跑一次。
 */
@Component
public class DataCleanupConfig {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupConfig.class);

    private final VisitLogMapper visitLogMapper;
    private final OperationLogMapper operationLogMapper;

    /** 访客记录保留天数 */
    @Value("${blog.cleanup.visit-days:90}")
    private int visitDays;

    /** 操作日志保留天数（审计日志留久一点） */
    @Value("${blog.cleanup.operation-days:180}")
    private int operationDays;

    public DataCleanupConfig(VisitLogMapper visitLogMapper, OperationLogMapper operationLogMapper) {
        this.visitLogMapper = visitLogMapper;
        this.operationLogMapper = operationLogMapper;
    }

    /** 默认每天 4:00；cron 可配，方便临时调整或本地验证 */
    @Scheduled(cron = "${blog.cleanup.cron:0 0 4 * * ?}")
    public void cleanup() {
        try {
            int visits = visitLogMapper.deleteOlderThan(visitDays);
            int logs = operationLogMapper.deleteBefore(operationDays);
            if (visits > 0 || logs > 0) {
                log.info("数据清理完成：访客记录 {} 条（保留 {} 天）、操作日志 {} 条（保留 {} 天）",
                        visits, visitDays, logs, operationDays);
            }
        } catch (Exception e) {
            // 定时任务里的异常不能往外抛，否则会中断后续调度
            log.warn("数据清理失败：{}", e.getMessage());
        }
    }
}
