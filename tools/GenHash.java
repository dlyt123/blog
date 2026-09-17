import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 生成 BCrypt 密码哈希的小工具（用于忘记密码时手动重置数据库里的密码）。
 *
 * 用法（在 demo 目录下，Windows 注意 classpath 用分号分隔）：
 *   java -cp "C:/Users/你的用户名/.m2/repository/org/springframework/security/spring-security-crypto/6.5.0/spring-security-crypto-6.5.0.jar;C:/Users/你的用户名/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar" tools/GenHash.java 你的新密码
 *
 * 把输出的 HASH 复制到这条 SQL 里执行即可：
 *   UPDATE `user` SET `password` = '<HASH>' WHERE `username` = 'admin';
 */
public class GenHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = args.length > 0 ? args[0] : "admin123";
        String hash = encoder.encode(raw);
        System.out.println("RAW  = " + raw);
        System.out.println("HASH = " + hash);
        System.out.println("校验 = " + encoder.matches(raw, hash));
    }
}
