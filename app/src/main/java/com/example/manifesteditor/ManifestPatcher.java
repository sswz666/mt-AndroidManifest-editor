package com.example.manifesteditor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 最小改动写回：把内存 DOM 的修改"打补丁"到原文上。
 *
 * <p>做法：把 DOM 树和原文结构索引（{@link ManifestSource}）逐层配对，
 * 只针对三种情况生成编辑：
 * <ul>
 *   <li>属性变化 —— 只重写该元素的开始标签，未变的属性片段逐字节沿用；</li>
 *   <li>元素删除 —— 只删掉该元素占用的那几行；</li>
 *   <li>元素新增 —— 按同组兄弟的排版风格插入新的一行。</li>
 * </ul>
 * 其余内容（注释、空行、换行位置、缩进、属性顺序）完全不动。
 *
 * <p>任何一步对不上（结构不匹配、补丁区间重叠等）都返回 null，
 * 由调用方退回"整篇重新序列化"，保证不会改坏文件。
 */
final class ManifestPatcher {

    /** 一处文本替换 */
    private static final class Edit {
        final int start;
        final int end;
        final String text;

        Edit(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private final Document doc;
    private final ManifestSource src;
    private final String text;
    private final List<Edit> edits = new ArrayList<>();

    private ManifestPatcher(Document doc, ManifestSource src) {
        this.doc = doc;
        this.src = src;
        this.text = src.text;
    }

    /** 返回打完补丁的新文本；无法安全打补丁时返回 null */
    static String patch(Document doc, ManifestSource src) {
        if (doc == null || src == null || src.root == null) return null;
        return new ManifestPatcher(doc, src).run();
    }

    private String run() {
        Element domRoot = doc.getDocumentElement();
        if (domRoot == null || !domRoot.getTagName().equals(src.root.tag)) return null;
        if (!diffElement(domRoot, src.root)) return null;
        if (edits.isEmpty()) return text;

        List<Edit> sorted = new ArrayList<>(edits);
        sorted.sort((a, b) -> Integer.compare(b.start, a.start));
        StringBuilder sb = new StringBuilder(text);
        int limit = Integer.MAX_VALUE;
        for (Edit e : sorted) {
            if (e.end > limit || e.start < 0 || e.end > sb.length()) return null;
            sb.replace(e.start, e.end, e.text);
            limit = e.start;
        }
        return sb.toString();
    }

    /** 递归对比一个元素及其子树，把差异转成编辑；返回 false 表示放弃打补丁 */
    private boolean diffElement(Element domEl, ManifestSource.Node srcNode) {
        if (!attrsEqual(domEl, srcNode)) {
            String tagText = text.substring(srcNode.start, srcNode.startTagEnd);
            String patched = buildTag(tagText, domEl);
            if (patched == null) return false;
            edits.add(new Edit(srcNode.start, srcNode.startTagEnd, patched));
        }

        List<Element> domKids = elementChildren(domEl);
        List<ManifestSource.Node> srcKids = srcNode.children;
        int n = srcKids.size();
        boolean[] used = new boolean[n];
        ManifestSource.Node[] match = new ManifestSource.Node[domKids.size()];

        // 第一轮：标签名 + android:name 完全一致（权限、组件等最稳的锚点）
        for (int i = 0; i < domKids.size(); i++) {
            Element d = domKids.get(i);
            String tag = d.getTagName();
            String name = d.getAttribute("android:name");
            for (int j = 0; j < n; j++) {
                if (used[j]) continue;
                ManifestSource.Node s = srcKids.get(j);
                if (!s.tag.equals(tag)) continue;
                if (!decode(s.attr("android:name")).equals(name)) continue;
                used[j] = true;
                match[i] = s;
                break;
            }
        }
        // 第二轮：只要求标签名一致（同名子元素按出现顺序配对）
        for (int i = 0; i < domKids.size(); i++) {
            if (match[i] != null) continue;
            Element d = domKids.get(i);
            for (int j = 0; j < n; j++) {
                if (used[j] || !srcKids.get(j).tag.equals(d.getTagName())) continue;
                used[j] = true;
                match[i] = srcKids.get(j);
                break;
            }
        }

        for (int i = 0; i < domKids.size(); i++) {
            if (match[i] == null) continue;
            if (!diffElement(domKids.get(i), match[i])) return false;
        }
        // 删除：原文有、DOM 里已经没有
        for (int j = 0; j < n; j++) {
            if (!used[j]) addDelete(srcKids.get(j));
        }
        // 新增：DOM 里有、原文里没有
        for (int i = 0; i < domKids.size(); i++) {
            if (match[i] != null) continue;
            if (!addInsert(srcNode, domKids, match, i)) return false;
        }
        return true;
    }

    /** 删除一个元素：整行独占时连同该行一起删掉，否则只删元素本身 */
    private void addDelete(ManifestSource.Node s) {
        int lineFrom = lineStart(s.start);
        int lineTo = nextLineStart(s.end);
        boolean blankBefore = isBlank(lineFrom, s.start);
        boolean blankAfter = isBlank(s.end, Math.max(s.end, lineTo - 1));
        if (blankBefore && blankAfter && lineTo > lineFrom) {
            edits.add(new Edit(lineFrom, lineTo, ""));
        } else {
            edits.add(new Edit(s.start, s.end, ""));
        }
    }

    /** 插入一个新元素：位置和缩进都从相邻兄弟那里复制 */
    private boolean addInsert(ManifestSource.Node parentSrc, List<Element> domKids,
                             ManifestSource.Node[] match, int index) {
        Element d = domKids.get(index);
        if (!elementChildren(d).isEmpty()) {
            // 带子节点的新元素（当前 UI 不会产生），交给整篇重排更安全
            return false;
        }

        // 模板：优先取同标签的已有兄弟，排版风格直接继承
        ManifestSource.Node template = null;
        for (int k = 0; k < match.length; k++) {
            if (match[k] == null) continue;
            if (!match[k].tag.equals(d.getTagName())) continue;
            if (!match[k].children.isEmpty()) continue;
            template = match[k];
            break;
        }
        String tagText = template == null
                ? buildTagFromScratch(d)
                : buildTag(text.substring(template.start, template.startTagEnd), d);
        if (tagText == null) return false;

        String indent;
        int pos;
        ManifestSource.Node prev = null;
        for (int k = index - 1; k >= 0; k--) {
            if (match[k] != null) {
                prev = match[k];
                break;
            }
        }
        if (prev != null) {
            indent = lineIndent(prev.start);
            pos = nextLineStart(prev.end);
        } else {
            ManifestSource.Node next = null;
            for (int k = index + 1; k < match.length; k++) {
                if (match[k] != null) {
                    next = match[k];
                    break;
                }
            }
            if (next != null) {
                indent = lineIndent(next.start);
                pos = lineStart(next.start);
            } else {
                // 父元素还没有任何子元素：插到父开始标签所在行之后
                indent = lineIndent(parentSrc.start) + srcIndentUnit();
                pos = nextLineStart(parentSrc.startTagEnd - 1);
            }
        }
        if (pos < 0 || pos > text.length()) return false;
        edits.add(new Edit(pos, pos, indent + tagText + "\n"));
        return true;
    }

    private String srcIndentUnit() {
        // 原文缩进风格：从父元素与第一个子元素的差值推断，退化为 4 空格
        String parentIndent = lineIndent(src.root.start);
        for (ManifestSource.Node c : src.root.children) {
            String ci = lineIndent(c.start);
            if (ci.length() > parentIndent.length() && ci.startsWith(parentIndent)) {
                return ci.substring(parentIndent.length());
            }
        }
        return "    ";
    }

    // ==================== 开始标签重写 ====================

    /**
     * 按原文排版重写开始标签：未变化的属性片段原样沿用，只替换值的差异，
     * 新增属性按原文件的换行/缩进风格追加。
     */
    private String buildTag(String tagText, Element el) {
        ManifestSource.Node parsed;
        try {
            parsed = ManifestSource.parseTagText(tagText);
        } catch (Exception e) {
            return null;
        }
        boolean multiline = tagText.indexOf('\n') >= 0;
        String contIndent = continuationIndent(tagText);
        StringBuilder sb = new StringBuilder(tagText.length() + 32);
        sb.append('<').append(parsed.tag);
        Set<String> emitted = new LinkedHashSet<>();
        for (ManifestSource.Attr a : parsed.attrList) {
            if (!el.hasAttribute(a.name)) continue;
            emitted.add(a.name);
            String v = el.getAttribute(a.name);
            if (decode(a.value).equals(v)) {
                // 值没变：整个片段（含前导空白）原样搬过去
                sb.append(tagText, a.segStart, a.segEnd);
            } else {
                sb.append(tagText, a.segStart, a.valueStart + 1)
                        .append(escapeAttr(v))
                        .append(tagText, a.valueEnd, a.segEnd);
            }
        }
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node an = attrs.item(i);
            String name = an.getNodeName();
            if (emitted.contains(name) || !el.hasAttribute(name)) continue;
            String sep = multiline && !parsed.attrList.isEmpty() ? "\n" + contIndent : " ";
            sb.append(sep).append(name).append("=\"")
                    .append(escapeAttr(an.getNodeValue())).append('"');
        }
        if (parsed.selfClosing || tagText.trim().endsWith("/>")) {
            sb.append(" />");
        } else {
            sb.append('>');
        }
        return sb.toString();
    }

    /** 新元素没有任何同标签兄弟可参照时的兜底拼装 */
    private String buildTagFromScratch(Element el) {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(el.getTagName());
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            sb.append(' ').append(a.getNodeName()).append("=\"")
                    .append(escapeAttr(a.getNodeValue())).append('"');
        }
        sb.append(" />");
        return sb.toString();
    }

    /** 多行开始标签里，后续属性行的缩进 */
    private static String continuationIndent(String tagText) {
        int nl = tagText.indexOf('\n');
        if (nl < 0) return "";
        int i = nl + 1;
        while (i < tagText.length() && (tagText.charAt(i) == ' ' || tagText.charAt(i) == '\t')) i++;
        return tagText.substring(nl + 1, i);
    }

    // ==================== 工具 ====================

    private boolean attrsEqual(Element el, ManifestSource.Node s) {
        int count = 0;
        for (ManifestSource.Attr a : s.attrList) {
            if (!el.hasAttribute(a.name)) return false;
            if (!decode(a.value).equals(el.getAttribute(a.name))) return false;
            count++;
        }
        return count == el.getAttributes().getLength();
    }

    private static List<Element> elementChildren(Element parent) {
        List<Element> list = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) list.add((Element) n);
        }
        return list;
    }

    private boolean isBlank(int from, int to) {
        if (from < 0) return true;
        for (int i = from; i < to && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') return false;
        }
        return true;
    }

    private int lineStart(int pos) {
        if (pos <= 0) return 0;
        int p = text.lastIndexOf('\n', pos - 1);
        return p < 0 ? 0 : p + 1;
    }

    /** pos 所在行之后的第一行起点（含换行符） */
    private int nextLineStart(int pos) {
        if (pos < 0) return 0;
        int p = text.indexOf('\n', Math.min(pos, text.length()));
        return p < 0 ? text.length() : p + 1;
    }

    /** pos 所在行的前导空白 */
    private String lineIndent(int pos) {
        int s = lineStart(pos);
        int i = s;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.substring(s, i);
    }

    static String decode(String v) {
        if (v == null || v.indexOf('&') < 0) return v == null ? "" : v;
        return v.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#10;", "\n").replace("&amp;", "&");
    }

    static String escapeAttr(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\n': sb.append("&#10;"); break;
                case '\t': sb.append("&#9;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 结构一致性校验：打补丁的结果必须和内存 DOM 完全等价 */
    static boolean sameTree(Element a, Element b) {
        if (a == null || b == null) return false;
        if (!a.getTagName().equals(b.getTagName())) return false;
        NamedNodeMap ma = a.getAttributes();
        NamedNodeMap mb = b.getAttributes();
        if (ma.getLength() != mb.getLength()) return false;
        for (int i = 0; i < ma.getLength(); i++) {
            Node at = ma.item(i);
            String name = at.getNodeName();
            if (!b.hasAttribute(name)) return false;
            if (!decode(at.getNodeValue()).equals(decode(b.getAttribute(name)))) return false;
        }
        List<Element> ca = elementChildren(a);
        List<Element> cb = elementChildren(b);
        if (ca.size() != cb.size()) return false;
        for (int i = 0; i < ca.size(); i++) {
            if (!sameTree(ca.get(i), cb.get(i))) return false;
        }
        return true;
    }
}
