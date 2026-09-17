package com.back.backeddemo.common;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * 图形验证码生成（纯 JDK，不依赖任何第三方）。
 *
 * <p>防的是「脚本批量试密码撞库」，不是专业破解。所以做了这些基本的干扰：
 * 字符随机旋转、随机颜色、干扰线、噪点。
 *
 * <p>去掉了容易看错的字符（0/O、1/I/L），降低正常用户输错的概率。
 */
public final class CaptchaUtil {

    /** 易混字符已剔除 */
    private static final char[] CHARS = "ABCDEFGHJKMNPQRSTUVWXY23456789".toCharArray();

    private static final Random RANDOM = new Random();

    private CaptchaUtil() {
    }

    public static String randomCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    /** 把验证码渲染成 PNG 字节 */
    public static byte[] render(String code) throws Exception {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景
        g.setColor(new Color(250, 245, 248));
        g.fillRect(0, 0, width, height);

        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(randomColor(170, 230));
            g.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height),
                    RANDOM.nextInt(width), RANDOM.nextInt(height));
        }

        // 噪点
        for (int i = 0; i < 60; i++) {
            g.setColor(randomColor(180, 240));
            g.fillRect(RANDOM.nextInt(width), RANDOM.nextInt(height), 1, 1);
        }

        // 字符（带随机旋转与轻微上下偏移）
        g.setFont(new Font("Arial", Font.BOLD, 26));
        int step = (width - 20) / code.length();
        for (int i = 0; i < code.length(); i++) {
            int x = 12 + i * step;
            int y = 28 + RANDOM.nextInt(6) - 3;
            double angle = (RANDOM.nextInt(50) - 25) * Math.PI / 180;
            AffineTransform old = g.getTransform();
            g.rotate(angle, x, y);
            g.setColor(randomColor(40, 140));
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.setTransform(old);
        }

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static Color randomColor(int min, int max) {
        int span = Math.max(1, max - min);
        return new Color(min + RANDOM.nextInt(span),
                min + RANDOM.nextInt(span),
                min + RANDOM.nextInt(span));
    }
}
