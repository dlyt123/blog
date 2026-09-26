package com.back.backeddemo.common;

/**
 * 分页参数（值对象）—— 「页码 / 每页条数」的边界规则只在这里写一遍。
 *
 * <p>为什么需要它：
 * 之前每个控制器自己处理分页，有的写了 {@code Math.min(Math.max(pageSize, 1), 100)}，
 * 有的直接把参数透传给 SQL。结果是两件真事：
 * <ul>
 *   <li>{@code GET /api/posts?page=0} → {@code offset = (0-1)*10 = -10}
 *       → 拼成 {@code LIMIT -10,10} → MySQL 语法错误 → 用户看到 500</li>
 *   <li>{@code pageSize} 没有上限，传 100000 就等于把整张表拉出来</li>
 * </ul>
 * 规则散在十几处，漏掉一处就出事。收进这个类之后，「漏」这件事在结构上不成立了。
 *
 * <p>用法：控制器里
 * <pre>{@code
 * PageQuery pq = PageQuery.of(page, pageSize);
 * return Result.success(postService.list(..., pq.page(), pq.size()));
 * }</pre>
 */
public final class PageQuery {

    /** 没传或传了非法值时的每页条数 */
    public static final int DEFAULT_SIZE = 10;

    /**
     * 每页条数上限。
     * 取 100 而不是更小的值，是因为私信记录（Messages）和系列文章（Series）
     * 前端就是一次性拉 100 条渲染的，压低会直接让这两个页面少显示内容。
     */
    public static final int MAX_SIZE = 100;

    private final int page;
    private final int size;

    private PageQuery(int page, int size) {
        this.page = page;
        this.size = size;
    }

    /** 用默认上限（{@value #MAX_SIZE}）归一化 */
    public static PageQuery of(Integer page, Integer size) {
        return of(page, size, MAX_SIZE);
    }

    /**
     * 归一化并指定上限。
     *
     * @param page    页码，{@code null} 或小于 1 一律当作第 1 页
     * @param size    每页条数，{@code null} 或小于 1 用默认值，超过 {@code maxSize} 截断
     * @param maxSize 该接口允许的最大条数（例如评论区一次最多 50 条）
     */
    public static PageQuery of(Integer page, Integer size, int maxSize) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, maxSize);
        return new PageQuery(safePage, safeSize);
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    /**
     * MyBatis {@code LIMIT #{offset}, #{limit}} 用的偏移量。
     * 因为 page 已经被保证 >= 1，这里一定是非负数。
     */
    public int offset() {
        return (page - 1) * size;
    }
}
