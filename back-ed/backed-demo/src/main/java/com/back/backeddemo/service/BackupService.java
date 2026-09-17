package com.back.backeddemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 数据库备份。
 *
 * <p>实现方式：调用 MySQL 自带的 <code>mysqldump</code> 命令导出整个库为 .sql 文件。
 * 密码通过环境变量 <code>MYSQL_PWD</code> 传递，<b>不出现在命令行里</b>（避免被 ps 看到）。
 *
 * <p>两种触发方式：
 * <ul>
 *   <li>自动：每天凌晨 3 点定时备份（见 {@link #autoBackup()}）</li>
 *   <li>手动：后台「立即备份」按钮 → {@link #backupNow()}</li>
 * </ul>
 *
 * <p>配置：
 * <pre>
 * blog.backup.dir      备份目录，默认 ./backups
 * blog.backup.keep     保留最近几个备份，默认 7
 * blog.backup.mysqldump mysqldump 可执行文件路径，默认走 PATH
 * </pre>
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String schema;

    @Value("${blog.backup.dir:./backups}")
    private String backupDir;

    @Value("${blog.backup.keep:7}")
    private int keep;

    @Value("${blog.backup.mysqldump:mysqldump}")
    private String mysqldumpPath;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public BackupService(@Value("${spring.datasource.url}") String jdbcUrl) {
        this.schema = parseSchema(jdbcUrl);
    }

    /** 每天凌晨 3:00 自动备份 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void autoBackup() {
        try {
            Map<String, Object> r = backupNow();
            log.info("自动备份完成：{}", r.get("file"));
        } catch (Exception e) {
            log.warn("自动备份失败：{}", e.getMessage());
        }
    }

    /** 立即备份，返回备份文件信息 */
    public Map<String, Object> backupNow() throws IOException, InterruptedException {
        Path dir = Paths.get(backupDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String fileName = "blog-" + LocalDateTime.now().format(NAME_FMT) + ".sql";
        File target = dir.resolve(fileName).toFile();

        List<String> cmd = new ArrayList<>();
        cmd.add(mysqldumpPath);
        cmd.add("-u" + dbUser);
        cmd.add("--single-transaction");
        cmd.add("--default-character-set=utf8mb4");
        cmd.add("--databases");
        cmd.add(schema);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // 密码走环境变量，不出现在命令行参数里
        pb.environment().put("MYSQL_PWD", dbPassword == null ? "" : dbPassword);
        pb.redirectOutput(target);
        pb.redirectErrorStream(false);

        Process process = pb.start();
        int code = process.waitFor();
        if (code != 0) {
            Files.deleteIfExists(target.toPath());
            throw new IOException("mysqldump 退出码 " + code + "，请检查 mysqldump 路径与数据库账号");
        }

        cleanupOld();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file", fileName);
        result.put("size", target.length());
        return result;
    }

    /** 备份文件列表（新的在前） */
    public List<Map<String, Object>> listBackups() {
        Path dir = Paths.get(backupDir).toAbsolutePath().normalize();
        List<Map<String, Object>> list = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return list;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .forEach(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("file", p.getFileName().toString());
                        try {
                            m.put("size", Files.size(p));
                            m.put("time", Files.getLastModifiedTime(p).toString());
                        } catch (IOException ignore) {
                            m.put("size", 0);
                        }
                        list.add(m);
                    });
        } catch (IOException e) {
            log.warn("读取备份目录失败：{}", e.getMessage());
        }
        return list;
    }

    /** 只保留最近 keep 个备份 */
    private void cleanupOld() {
        Path dir = Paths.get(backupDir).toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (int i = keep; i < files.size(); i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (IOException e) {
            log.warn("清理旧备份失败：{}", e.getMessage());
        }
    }

    /** jdbc:mysql://localhost:3306/blog?xxx → blog */
    private static String parseSchema(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "blog";
        }
        int q = jdbcUrl.indexOf('?');
        String base = q > 0 ? jdbcUrl.substring(0, q) : jdbcUrl;
        int slash = base.lastIndexOf('/');
        return slash > 0 ? base.substring(slash + 1) : "blog";
    }
}
