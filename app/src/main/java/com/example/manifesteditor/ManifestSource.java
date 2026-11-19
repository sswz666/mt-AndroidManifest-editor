package com.example.manifesteditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原文结构索引。
 *
 * <p>只做一件事：扫描原始 XML 文本，记录每个元素在原文中的字符区间
 * （开始标签的起止、整个元素的起止）以及每个属性的原始片段位置。
 *
 * <p>有了这份索引，"写回"就可以变成最小改动：只替换/插入/删除真正变化的那几行，
 * 用户原有的排版（属性换行、注释、空行、缩进）逐字节保留下来。
 */
public class ManifestSource {

    /** 一个属性在原文中的片段 */
    public static class Attr {
        public String name;
        /** 引号内的原始（未反转义）值 */
        public String value;
        /** 属性名前空白字符的起点 */
        public int segStart;
        /** 属性值结束引号之后的位置 */
        public int segEnd;
        /** 值起始引号的位置 */
        public int valueStart;
        /** 值结束引号的位置 */
        public int valueEnd;
        public char quote = '"';
    }

    /** 一个元素在原文中的位置 */
    public static class Node {
        public final String tag;
        /** '<' 所在下标 */
        public int start;
        /** 开始标签 '>' 之后的下标 */
        public int startTagEnd;
        /** 元素结束（'</tag>' 或 '/>' 之后）的下标 */
        public int end;
        public boolean selfClosing;
        public Node parent;
        public final List<Node> children = new ArrayList<>();
        public final Map<String, String> attrs = new LinkedHashMap<>();
        public final List<Attr> attrList = new ArrayList<>();

        Node(String tag) {
            this.tag = tag;
        }

        public String attr(String name) {
            String v = attrs.get(name);
            return v == null ? "" : v;
        }
    }

    public final String text;
    public final Node root;

    private ManifestSource(String text, Node root) {
        this.text = text;
        this.root = root;
    }

    /** 扫描全文，建立索引；结构异常时返回 root == null */
    public static ManifestSource scan(String text) {
        Node root = null;
        List<Node> stack = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            int lt = text.indexOf('<', i);
            if (lt < 0) break;
            if (text.startsWith("<!--", lt)) {
                int e = text.indexOf("-->", lt + 4);
                i = e < 0 ? n : e + 3;
                continue;
            }
            if (text.startsWith("<![CDATA[", lt)) {
                int e = text.indexOf("]]>", lt + 9);
                i = e < 0 ? n : e + 3;
                continue;
            }
            if (text.startsWith("<?", lt)) {
                int e = text.indexOf("?>", lt + 2);
                i = e < 0 ? n : e + 2;
                continue;
            }
            if (text.startsWith("<!", lt)) {
                int e = text.indexOf('>', lt + 2);
                i = e < 0 ? n : e + 1;
                continue;
            }
            if (text.startsWith("</", lt)) {
                int e = text.indexOf('>', lt + 2);
                if (e < 0) break;
                if (!stack.isEmpty()) {
                    Node node = stack.remove(stack.size() - 1);
                    node.end = e + 1;
                }
                i = e + 1;
                continue;
            }
            int j = lt + 1;
            while (j < n && isNameChar(text.charAt(j))) j++;
            if (j == lt + 1) {
                i = lt + 1;
                continue;
            }
            int gt = findTagEnd(text, j);
            if (gt < 0) break;
            Node node = new Node(text.substring(lt + 1, j));
            node.start = lt;
            node.startTagEnd = gt + 1;
            node.selfClosing = gt > 0 && text.charAt(gt - 1) == '/';
            node.parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            if (node.parent != null) {
                node.parent.children.add(node);
            } else if (root == null) {
                root = node;
            }
            parseAttrs(text, j, node.selfClosing ? gt - 1 : gt, node);
            if (node.selfClosing) {
                node.end = gt + 1;
            } else {
                stack.add(node);
            }
            i = gt + 1;
        }
        // 未闭合的元素（理论上不会发生）一律延伸到文本末尾
        for (Node node : stack) node.end = n;
        return new ManifestSource(text, root);
    }

    /** 解析一段独立的开始标签文本（如 {@code <application a="1" b="2">}） */
    public static Node parseTagText(String tagText) {
        int n = tagText.length();
        int lt = tagText.indexOf('<');
        if (lt < 0) lt = 0;
        int j = lt + 1;
        while (j < n && isNameChar(tagText.charAt(j))) j++;
        Node node = new Node(tagText.substring(lt + 1, j));
        int gt = findTagEnd(tagText, j);
        if (gt < 0) gt = n - 1;
        node.selfClosing = gt > 0 && tagText.charAt(gt - 1) == '/';
        parseAttrs(tagText, j, node.selfClosing ? gt - 1 : gt, node);
        return node;
    }

    private static void parseAttrs(String text, int from, int to, Node node) {
        int i = from;
        while (i < to) {
            int wsStart = i;
            while (i < to && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= to) break;
            int nameStart = i;
            while (i < to && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '=') i++;
            if (i == nameStart) {
                i++;
                continue;
            }
            String name = text.substring(nameStart, i);
            while (i < to && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= to || text.charAt(i) != '=') {
                // 无值属性（非法 XML），记录空值后继续
                node.attrs.put(name, "");
                continue;
            }
            i++; // '='
            while (i < to && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= to) break;
            Attr a = new Attr();
            a.name = name;
            a.segStart = wsStart;
            char q = text.charAt(i);
            if (q != '"' && q != '\'') {
                // 无引号值（非法 XML）
                int vs = i;
                while (i < to && !Character.isWhitespace(text.charAt(i))) i++;
                a.value = text.substring(vs, i);
                a.valueStart = vs;
                a.valueEnd = i;
                a.segEnd = i;
            } else {
                int vs = i + 1;
                int ve = text.indexOf(q, vs);
                if (ve < 0 || ve > to) ve = to;
                a.quote = q;
                a.value = text.substring(vs, Math.min(ve, to));
                a.valueStart = i;
                a.valueEnd = Math.min(ve, to);
                a.segEnd = Math.min(ve + 1, to);
                i = a.segEnd;
            }
            node.attrList.add(a);
            node.attrs.put(name, a.value);
        }
    }

    private static boolean isNameChar(char c) {
        return c != ' ' && c != '\t' && c != '\n' && c != '\r'
                && c != '>' && c != '/' && c != '<';
    }

    /** 从标签名之后找到开始标签的 '>'（跳过引号内的内容） */
    private static int findTagEnd(String text, int from) {
        int n = text.length();
        int k = from;
        while (k < n) {
            char c = text.charAt(k);
            if (c == '"' || c == '\'') {
                char q = c;
                k++;
                while (k < n && text.charAt(k) != q) k++;
                k++;
                continue;
            }
            if (c == '>') return k;
            k++;
        }
        return -1;
    }
}
