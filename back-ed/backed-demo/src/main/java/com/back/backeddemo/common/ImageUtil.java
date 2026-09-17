package com.back.backeddemo.common;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片压缩工具（纯 JDK ImageIO，不引入额外依赖）。
 *
 * <p>目的：用户经常直接传手机拍的原图或屏幕截图（动辄好几 MB），
 * 既拖慢页面加载又占磁盘。这里做三件事：
 * <ol>
 *   <li><b>缩放</b>：宽度超过 {@code maxWidth} 时等比缩小</li>
 *   <li><b>重编码</b>：JPEG 按指定质量重写，通常能省一大半体积</li>
 *   <li><b>PNG 无透明通道时转 JPEG</b>：这是省得最多的一条 ——
 *       JDK 内置的 PNG 编码器比不过专业优化过的 PNG，重编码往往**更大**；
 *       而截图这类图本来就没有透明背景，转成 JPEG 通常能省 80% 以上。
 *       有透明通道的 PNG 保持 PNG，避免透明区域变黑。</li>
 * </ol>
 *
 * <p>安全边界：
 * <ul>
 *   <li>GIF（可能多帧动画）／WebP／BMP 一律原样返回，交给原逻辑保存</li>
 *   <li>压缩后如果反而更大，就返回原图 —— 保证「压缩」永远不会让文件变大</li>
 *   <li>任何异常都吞掉并返回原图，绝不让上传失败</li>
 * </ul>
 */
public final class ImageUtil {

    private ImageUtil() {
    }

    /** 压缩结果：字节 + 实际格式（可能是 jpg 也可能是 png，调用方要用它决定后缀） */
    public record CompressResult(byte[] data, String format, boolean compressed) {
    }

    /**
     * @param original    原始字节
     * @param format      上传文件的后缀，如 "jpg" / "png"
     * @param maxWidth    最大宽度（像素）
     * @param jpegQuality JPEG 质量 0~1
     */
    public static CompressResult compress(byte[] original, String format, int maxWidth, float jpegQuality) {
        if (original == null || original.length == 0 || format == null) {
            return new CompressResult(original, normalize(format), false);
        }
        String fmt = format.toLowerCase();
        boolean isJpeg = fmt.equals("jpg") || fmt.equals("jpeg");
        boolean isPng = fmt.equals("png");
        if (!isJpeg && !isPng) {
            // GIF / WebP / BMP：不动
            return new CompressResult(original, fmt, false);
        }

        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) {
                return new CompressResult(original, fmt, false);
            }
            int w = img.getWidth();
            int h = img.getHeight();
            boolean needResize = w > maxWidth;
            boolean hasAlpha = img.getColorModel().hasAlpha();
            // PNG 无透明通道 + 体积够大 → 转 JPEG（省得最多）
            boolean pngToJpeg = isPng && !hasAlpha && original.length > 200 * 1024;
            boolean toJpeg = isJpeg || pngToJpeg;

            // 既不用缩放、也不值得重编码，就原样返回
            if (!needResize && !pngToJpeg && original.length < 300 * 1024) {
                return new CompressResult(original, fmt, false);
            }

            BufferedImage target = img;
            if (needResize) {
                int nh = Math.max(1, (int) (h * (maxWidth / (double) w)));
                target = new BufferedImage(maxWidth, nh,
                        toJpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = target.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                // 转 JPEG 时先铺白底，避免透明区域变黑
                if (toJpeg) {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, maxWidth, nh);
                }
                g.drawImage(img, 0, 0, maxWidth, nh, null);
                g.dispose();
            } else if (pngToJpeg) {
                // 不缩放但要从 PNG 转 JPEG：需要重画一遍把 alpha 换成白底
                target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = target.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.drawImage(img, 0, 0, null);
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (toJpeg) {
                writeJpeg(target, out, jpegQuality);
            } else {
                ImageIO.write(target, "png", out);
            }
            byte[] result = out.toByteArray();

            if (result.length > 0 && result.length < original.length) {
                return new CompressResult(result, toJpeg ? "jpg" : "png", true);
            }
            // 压完更大就不划算，退回原图与原格式
            return new CompressResult(original, fmt, false);
        } catch (Exception e) {
            return new CompressResult(original, fmt, false);
        }
    }

    private static String normalize(String format) {
        if (format == null) {
            return "png";
        }
        String f = format.toLowerCase();
        return (f.equals("jpeg") || f.equals("jpg")) ? "jpg" : f;
    }

    /** 用质量参数写 JPEG（ImageIO.write 不支持指定质量） */
    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream out, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpeg", out);
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.min(Math.max(quality, 0.1f), 1.0f));
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
