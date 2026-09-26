package com.back.backeddemo.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 敏感词匹配引擎（纯 Java 的 Trie/DFA 实现，零依赖）。
 *
 * <p>为什么不用现成的库：本项目要求离线构建（{@code mvn -o}），
 * 加新依赖会拉不下来，所以自己实现 —— 代码量不大，而且逻辑透明可控。
 *
 * <h3>匹配策略（比原来的 {@code String.contains} 强在哪）</h3>
 *
 * <p>原来的实现是「把词库循环一遍、逐个 contains」，有两个明显短板：
 * <ol>
 *   <li>词多了会慢（每篇文章要扫几百上千次）</li>
 *   <li>只能精确匹配，用户插个符号就绕过去了（"办*证"、"办 证"、"办-证"）</li>
 * </ol>
 *
 * <p>现在改成：<b>先归一化文本，再用 Trie 一次扫描</b>。
 *
 * <p><b>归一化会做什么：</b>
 * <ul>
 *   <li>全角转半角（Ａ→A、１→1）</li>
 *   <li>英文统一小写</li>
 *   <li>删掉「不可见字符」和「插入型干扰符号」：空白、零宽字符、
 *       {@code * . - _ · ~ ^ | \ / ★ ☆ ● ○} 之类</li>
 * </ul>
 *
 * <p><b>但故意保留句读符号</b>（{@code 。！？，；：、}）—— 因为它代表「断句」，
 * 不该被穿透。否则「我们去办。证明一下」会被归一化成「我们去办证明一下」，
 * 里面正好含「办证」而误杀。<b>这是"宁可漏、不可误杀"的取舍</b>：
 * 误杀正常用户比漏掉几个广告更伤。
 *
 * <p>所以「办*证」→「办证」会被命中 ✓，而「办。证明」→「办。证明」不会被误判 ✓。
 *
 * <p>线程安全：构建完成后只读，可安全并发使用。
 */
class SensitiveWordEngine {

    /** Trie 节点 */
    private static final class Node {
        /** 子节点；用 HashMap 而不是数组 —— 中文词的字符集太大，数组会浪费内存 */
        Map<Character, Node> next;
        /** 非 null 表示从根到这里构成一个完整的词 */
        String word;

        void put(char c) {
            if (next == null) {
                next = new HashMap<>(4);
            }
            next.computeIfAbsent(c, k -> new Node());
        }

        Node get(char c) {
            return next == null ? null : next.get(c);
        }
    }

    /**
     * 归一化时需要删掉的「插入型干扰字符」。
     * 注意：不包含句读符号（。！？，；：、）和字母数字汉字。
     */
    private static final String NOISE = " \t\r\n\u3000"
            + "\u200B\u200C\u200D\u200E\u200F\uFEFF"      // 零宽字符
            + "*.*-_~^|\\/+=#@$%&<>[]{}\"'`()（）【】《》…·•★☆●○◎◇◆■□▲△▼▽";

    private final Node root = new Node();
    private int size = 0;

    /** 用词典构建 Trie */
    void build(Collection<String> words) {
        if (words == null) {
            return;
        }
        for (String w : words) {
            add(w);
        }
    }

    /** 加入一个词（会先归一化；归一化后为空的词忽略） */
    void add(String raw) {
        String w = normalize(raw);
        if (w.isEmpty()) {
            return;
        }
        Node cur = root;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            cur.put(c);
            cur = cur.get(c);
        }
        if (cur.word == null) {
            cur.word = w;
            size++;
        }
    }

    /** 当前词条数 */
    int size() {
        return size;
    }

    /**
     * 在文本里找第一个命中的词；没有命中返回 null。
     * 复杂度 O(文本长度 × 最长词长度)，与词库规模基本无关。
     */
    String firstHit(String text) {
        if (text == null || text.isEmpty() || size == 0) {
            return null;
        }
        String s = normalize(text);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            Node cur = root;
            for (int j = i; j < len; j++) {
                cur = cur.get(s.charAt(j));
                if (cur == null) {
                    break;
                }
                if (cur.word != null) {
                    return cur.word;
                }
            }
        }
        return null;
    }

    /**
     * 文本归一化：全角转半角 → 删掉干扰字 → 转小写。
     *
     * <p>抽成 static 是方便单测，也方便别处复用。
     */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 全角 ASCII（ＦＵＬＬＷＩＤＴＨ）转半角
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            } else if (c == 0x3000) {
                c = ' ';
            }
            if (NOISE.indexOf(c) >= 0) {
                continue;   // 丢弃干扰字符与空白
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
