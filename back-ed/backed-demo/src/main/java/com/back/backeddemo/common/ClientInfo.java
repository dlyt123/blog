package com.back.backeddemo.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端信息提取：真实 IP 与设备（浏览器 / 系统）的粗略识别
 */
public final class ClientInfo {

    private ClientInfo() {
    }

    /**
     * 取客户端真实 IP。
     * 部署在 Nginx 之后时 getRemoteAddr() 拿到的是代理 IP，需要读代理透传的请求头。
     */
    public static String ip(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        String addr = request.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }

    /**
     * 从 User-Agent 粗略识别「浏览器 / 操作系统」。
     * 只做常见类型的判断，识别不出时返回「未知设备」，不引入额外依赖。
     */
    public static String device(String ua) {
        if (ua == null || ua.isBlank()) {
            return "未知设备";
        }
        String s = ua.toLowerCase();

        String browser;
        if (s.contains("micromessenger")) {
            browser = "微信";
        } else if (s.contains("edg/") || s.contains("edgios")) {
            browser = "Edge";
        } else if (s.contains("firefox")) {
            browser = "Firefox";
        } else if (s.contains("ucbrowser")) {
            browser = "UC";
        } else if (s.contains("qqbrowser")) {
            browser = "QQ 浏览器";
        } else if (s.contains("chrome") || s.contains("crios")) {
            browser = "Chrome";
        } else if (s.contains("safari")) {
            browser = "Safari";
        } else if (s.contains("curl") || s.contains("wget")) {
            browser = "命令行工具";
        } else if (s.contains("bot") || s.contains("spider") || s.contains("crawler")) {
            browser = "爬虫";
        } else {
            browser = "未知浏览器";
        }

        String os;
        if (s.contains("windows")) {
            os = "Windows";
        } else if (s.contains("iphone") || s.contains("ipad") || s.contains("ios")) {
            os = "iOS";
        } else if (s.contains("android")) {
            os = "Android";
        } else if (s.contains("mac os") || s.contains("macintosh")) {
            os = "macOS";
        } else if (s.contains("linux")) {
            os = "Linux";
        } else {
            os = "未知系统";
        }

        return browser + " / " + os;
    }

    /** 截断过长字段，避免超出数据库列长度 */
    public static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
