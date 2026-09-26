package com.back.backeddemo.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 「豁免管理员校验」标记。
 *
 * <p>背景：{@code /api/admin/**} 默认**全部要求管理员**（见 {@link AdminOnlyInterceptor}
 * 与 {@code WebConfig}）。这是刻意的默认值 —— 忘标注解最多让接口变得"太严"，
 * 不会让它悄悄对所有人开放。
 *
 * <p>少数接口确实不能要求管理员，就用这个注解显式豁免：
 * <ul>
 *   <li>{@code AdminPostController}（整类）：博主也要管理**自己的**文章，
 *       所以由 Controller 内部按「管理员 / 本人」做细粒度校验</li>
 *   <li>{@code MediaController#upload}：写文章要插图，任何登录用户都该能上传；
 *       同类的「列表 / 删除」仍然要求管理员</li>
 * </ul>
 *
 * <p><b>⚠️ 使用前提</b>：标了这个注解就等于放弃拦截器那一层保护，
 * 因此**被标注的方法必须自己做鉴权**（角色判断或资源归属判断）。
 * 标之前请确认这一点，否则就是把接口暴露给所有登录用户。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminExempt {
}
