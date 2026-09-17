-- ================================================================
-- 手动创建管理员账号（create-admin.sql）
--
-- 为什么不用程序自动创建：
--   自动建号等于把"进来就能拿最高权限"的入口写进代码里，
--   一旦默认密码被猜到或代码公开，站点就被直接接管。
--   所以管理员只能由部署者用这个脚本手动创建。
--
-- 执行方式（任选其一）：
--   mysql -uroot -p < tools/create-admin.sql
--   Navicat / 其他客户端里打开本文件执行
--
-- 说明：本脚本是幂等的 —— 已经存在同名账号时不会再插入。
-- ================================================================

USE `blog`;

-- 创建管理员：用户名 admin，密码 admin123
-- 下面的哈希就是 admin123 的 BCrypt 哈希（已校验可用）
INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `email`, `role`)
SELECT 'admin',
       '$2a$10$K3wn5siEUzkQSfy6r0Y/.ePsbkmGjZcQz3uqEt0uxxTfyVeY3yDrK',
       '管理员', '', '', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

-- ================================================================
-- 改成自己的密码（推荐）
-- ================================================================
-- 1) 先生成哈希（在 demo 目录下执行，把 <你> 换成实际用户名）：
--    java -cp "C:/Users/<你>/.m2/repository/org/springframework/security/spring-security-crypto/6.5.0/spring-security-crypto-6.5.0.jar;C:/Users/<你>/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar" tools/GenHash.java 你的新密码
--
-- 2) 用输出的 HASH 替换下面这句里的 HASH 后执行：
--    INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `email`, `role`)
--    VALUES ('admin', 'HASH', '管理员', '', '', 'ADMIN');
--
-- 3) 或者先按上面的方式建好账号，登录后在「个人信息」页自行修改密码。
--
-- 提示：密码在库里是 BCrypt 哈希，无法反推，所以忘记密码时只能覆盖重设
--       （见 newsql.txt 的「五、忘记管理员密码时怎么找回」）。
-- ================================================================

-- 确认结果
SELECT `id`, `username`, `nickname`, `role`, `create_time` FROM `user`;
