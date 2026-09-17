package com.back.backeddemo.common;

/**
 * 入参校验小工具。
 *
 * <p>为什么要它：Controller 直接把请求体塞进 Mapper，
 * 空值就会一路走到数据库，最后以「Column 'name' cannot be null」
 * 或者动态 SET 为空导致的 SQL 语法错误收场 —— 用户看到 500，日志里全是报错。
 * 在这里挡一下，返回 400 和一句人能看懂的话。
 */
public final class Validate {

    private Validate() {
    }

    /** 必填文本：null 或纯空白都算没填；顺手去掉首尾空格并限制长度 */
    public static String requiredText(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, fieldLabel + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException(400, fieldLabel + "最多 100 个字");
        }
        return trimmed;
    }

    /** 选填文本：超长时截断保护（避免超出数据库列长度） */
    public static String optionalText(String value, String fieldLabel, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            throw new BusinessException(400, fieldLabel + "最多 " + maxLen + " 个字");
        }
        return trimmed;
    }
}
