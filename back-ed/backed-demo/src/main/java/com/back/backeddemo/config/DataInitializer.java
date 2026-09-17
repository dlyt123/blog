package com.back.backeddemo.config;

import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时自动补建缺失的表与列（兜底），并检查管理员账号 / JWT 密钥是否就绪。
 *
 * ⚠️ 这里**不会自动创建管理员账号**，管理员必须由部署者手动执行 SQL 创建，
 *    原因见 {@link #checkAdminExists()}。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${blog.jwt.secret:}")
    private String jwtSecret;

    public DataInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(String... args) {
        warnIfJwtSecretIsPlaceholder();
        ensureTables();
        checkAdminExists();
        ensureAuthorColumn();
        ensureCommentColumn();
    }

    /** JWT 密钥还是占位符时给出醒目告警（占位符是公开的，等于没有防护） */
    private void warnIfJwtSecretIsPlaceholder() {
        if (jwtSecret == null || jwtSecret.isBlank()
                || "CHANGE_ME_set_blog_jwt_secret".equals(jwtSecret)) {
            System.out.println("========================================================");
            System.out.println("[DataInitializer] ⚠️ 安全告警：JWT 密钥还是占位符！");
            System.out.println("[DataInitializer]    任何人都能用这个公开值伪造管理员登录令牌。");
            System.out.println("[DataInitializer]    请在 application-local.yml 里设置 blog.jwt.secret，");
            System.out.println("[DataInitializer]    或设置环境变量 BLOG_JWT_SECRET。");
            System.out.println("========================================================");
        }
    }

    /** 启动时自动建缺失的表（兜底，避免漏跑 SQL 脚本导致点赞/收藏等报错） */
    private void ensureTables() {
        createIfAbsent(
            "post_like",
            "CREATE TABLE IF NOT EXISTS post_like (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  user_id BIGINT NOT NULL," +
            "  post_id BIGINT NOT NULL," +
            "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  PRIMARY KEY (id)," +
            "  UNIQUE KEY uk_user_post (user_id, post_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        createIfAbsent(
            "post_favorite",
            "CREATE TABLE IF NOT EXISTS post_favorite (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  user_id BIGINT NOT NULL," +
            "  post_id BIGINT NOT NULL," +
            "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  PRIMARY KEY (id)," +
            "  UNIQUE KEY uk_user_post (user_id, post_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        createIfAbsent(
            "visit_log",
            "CREATE TABLE IF NOT EXISTS visit_log (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT," +
            "  user_id BIGINT DEFAULT NULL COMMENT '访问者用户ID；为空表示游客'," +
            "  ip VARCHAR(64) DEFAULT NULL," +
            "  path VARCHAR(255) DEFAULT NULL COMMENT '访问的前端路由'," +
            "  referer VARCHAR(500) DEFAULT NULL," +
            "  user_agent VARCHAR(500) DEFAULT NULL," +
            "  device VARCHAR(100) DEFAULT NULL COMMENT '浏览器 / 系统'," +
            "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  PRIMARY KEY (id)," +
            "  KEY idx_create_time (create_time)," +
            "  KEY idx_user_id (user_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void createIfAbsent(String table, String ddl) {
        try {
            // 先查表是否已存在：已存在就直接跳过。
            // 好处：生产环境用低权限账号（无 DDL 权限）时，不会因为 CREATE 被拒而刷错误日志。
            List<String> exists = jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    String.class, table);
            if (!exists.isEmpty()) {
                return;
            }
            jdbc.execute(ddl);
            System.out.println("[DataInitializer] 表 " + table + " 已就绪");
        } catch (Exception e) {
            System.err.println("[DataInitializer] 建表 " + table + " 失败：" + e.getMessage());
        }
    }

    /** 启动时为 post 表补 author_id 列（老库迁移兜底），并把历史文章的 author_id 归到管理员 */
    private void ensureAuthorColumn() {
        try {
            jdbc.execute("ALTER TABLE post ADD COLUMN author_id BIGINT DEFAULT NULL COMMENT '作者ID'");
            System.out.println("[DataInitializer] post 表已新增 author_id 列");
        } catch (Exception ignored) {
            // 列已存在则忽略
        }
        try {
            Long adminId = null;
            User admin = userMapper.findByUsername("admin");
            if (admin != null) {
                adminId = admin.getId();
            }
            if (adminId != null) {
                // 把「没有作者」或「作者已不存在」的历史文章统一归到管理员账号名下，
                // 这样前台展示的作者名/头像就是管理员当前的信息。
                int n = jdbc.update(
                    "UPDATE post SET author_id = ? " +
                    "WHERE author_id IS NULL OR author_id NOT IN (SELECT id FROM `user`)",
                    adminId
                );
                if (n > 0) {
                    System.out.println("[DataInitializer] 已将 " + n + " 篇文章的作者归到 admin(id=" + adminId + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("[DataInitializer] 回填 author_id 失败：" + e.getMessage());
        }
    }

    /**
     * 启动时为 comment 表补 user_id 列（老库迁移兜底）。
     *
     * 注意：这里**故意不再自动删除**「无对应用户」的历史评论 ——
     * 自动删数据太危险（曾把示例文章误删过一次），这类清理请手动执行脚本完成。
     */
    private void ensureCommentColumn() {
        try {
            jdbc.execute("ALTER TABLE comment ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT '评论者用户ID'");
            System.out.println("[DataInitializer] comment 表已新增 user_id 列");
        } catch (Exception ignored) {
            // 列已存在则忽略
        }
    }

    /**
     * 检查管理员账号是否存在。
     *
     * ⚠️ 这里**故意不自动创建管理员**：
     *    程序自动建号等于把"进来就能拿到最高权限"的入口写在代码里，
     *    一旦默认密码被猜到（或代码公开），站点就直接被接管。
     *    所以管理员必须由部署者用 SQL 手动创建，见 demo/tools/create-admin.sql。
     */
    private void checkAdminExists() {
        User admin = userMapper.findByUsername("admin");
        if (admin == null) {
            System.out.println("========================================================");
            System.out.println("[DataInitializer] ⚠️ 库中还没有管理员账号，后台管理功能将不可用。");
            System.out.println("[DataInitializer]    请执行 demo/tools/create-admin.sql 手动创建，");
            System.out.println("[DataInitializer]    或按 newsql.txt 的说明生成自己的密码哈希。");
            System.out.println("========================================================");
        }
    }
}
