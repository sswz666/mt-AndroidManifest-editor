package com.example.manifesteditor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.editor.TextEditor;

/**
 * AndroidManifest.xml 数据会话：负责解析、查询、修改与序列化。
 * 所有修改都在内存中的 DOM 上进行，最后通过 writeBack() 一次性写回文本编辑器。
 */
public class ManifestSession {

    /** 一条权限声明（uses-permission 或 uses-permission-sdk-23） */
    public static class PermissionEntry {
        public final Element element;
        public final String name;   // android:name 的值
        public final String tag;    // 元素标签名

        PermissionEntry(Element element, String name, String tag) {
            this.element = element;
            this.name = name;
            this.tag = tag;
        }
    }

    /** 一个组件声明（activity/service/receiver/provider/activity-alias） */
    public static class ComponentEntry {
        public final Element element;
        public final String tag;
        public final String name;       // android:name 的值（可能为空）
        public final String label;      // android:name 的值（可能为空）
        public final boolean exported;  // android:exported 的值
        public final boolean hasExported;

        ComponentEntry(Element element) {
            this.element = element;
            this.tag = element.getTagName();
            this.name = element.getAttribute("android:name");
            this.label = element.getAttribute("android:label");
            String exported = element.getAttribute("android:exported");
            this.hasExported = !exported.isEmpty();
            this.exported = "true".equals(exported);
        }
    }

    public final PluginUI ui;
    public final TextEditor editor;
    public Document doc;
    public boolean modified = false;
    /** 写回时使用的缩进单位（解析时从原文件自动检测，默认 4 空格） */
    public String indentUnit = "    ";

    public ManifestSession(PluginUI ui, TextEditor editor, Document doc) {
        this.ui = ui;
        this.editor = editor;
        this.doc = doc;
    }

    // ==================== 解析与序列化 ====================

    public static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = builder.parse(is);
            Element root = document.getDocumentElement();
            if (root == null || !"manifest".equals(root.getTagName())) {
                throw new IllegalArgumentException("根元素不是 <manifest>，请确认这是 AndroidManifest.xml 文件");
            }
            return document;
        }
    }

    /** 从原文件文本检测缩进单位（2 空格 / 4 空格 / Tab），保持写回后风格一致 */
    public static String detectIndent(String xml) {
        // 找第一行以缩进开头且以元素标签开始的行（即根元素的第一个子元素）
        Matcher m = Pattern.compile("\\n([ \\t]+)<[^!/?]").matcher(xml);
        if (m.find()) {
            String ws = m.group(1);
            if (ws.indexOf('\t') >= 0) return "\t";
            return ws;
        }
        return "    ";
    }

    /**
     * 序列化为带缩进的 XML 文本。
     * 不使用系统 Transformer（Android 上它会把已有的缩进空白压缩掉），
     * 而是手写格式化输出：声明独立一行、每个元素独立一行、按原文件缩进风格缩进、注释原样保留。
     */
    public String toXml() throws Exception {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        NodeList nodes = doc.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                serializeElement(sb, node, 0);
            } else if (node.getNodeType() == Node.COMMENT_NODE) {
                String text = node.getNodeValue() == null ? "" : node.getNodeValue().trim();
                if (!text.isEmpty()) sb.append("<!--").append(text).append("-->\n");
            }
        }
        return sb.toString();
    }

    /** 递归序列化一个元素（含属性与子节点），depth 为缩进层级 */
    private void serializeElement(StringBuilder sb, Node node, int depth) {
        Element el = (Element) node;
        String indent = indent(depth);
        sb.append(indent).append('<').append(el.getTagName());
        NamedNodeMap attrs = el.getAttributes();
        // xmlns 声明固定排在最前（符合 AndroidManifest 惯例），其余属性按解析器顺序
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (!a.getNodeName().startsWith("xmlns")) continue;
            sb.append(' ').append(a.getNodeName()).append("=\"")
                    .append(escapeAttr(a.getNodeValue())).append('"');
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (a.getNodeName().startsWith("xmlns")) continue;
            sb.append(' ').append(a.getNodeName()).append("=\"")
                    .append(escapeAttr(a.getNodeValue())).append('"');
        }

        // 分析子节点：是否包含块级子节点（元素/注释/PI）以及内联文本
        NodeList children = el.getChildNodes();
        boolean hasBlockChild = false;
        StringBuilder inlineText = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            short type = c.getNodeType();
            if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                String v = c.getNodeValue();
                if (v != null && !v.trim().isEmpty()) inlineText.append(v.trim());
            } else if (type == Node.ELEMENT_NODE || type == Node.COMMENT_NODE
                    || type == Node.PROCESSING_INSTRUCTION_NODE) {
                hasBlockChild = true;
            }
        }

        if (!hasBlockChild && inlineText.length() == 0) {
            // 空元素：<tag ... />
            sb.append(" />\n");
            return;
        }
        if (!hasBlockChild) {
            // 仅含文本：<tag ...>text</tag>
            sb.append('>').append(escapeText(inlineText.toString()))
                    .append("</").append(el.getTagName()).append(">\n");
            return;
        }

        sb.append(">\n");
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            switch (c.getNodeType()) {
                case Node.ELEMENT_NODE:
                    serializeElement(sb, c, depth + 1);
                    break;
                case Node.COMMENT_NODE: {
                    String text = c.getNodeValue() == null ? "" : c.getNodeValue().trim();
                    if (!text.isEmpty())
                        sb.append(indent(depth + 1)).append("<!--").append(text).append("-->\n");
                    break;
                }
                case Node.CDATA_SECTION_NODE: {
                    String v = c.getNodeValue();
                    if (v != null && !v.trim().isEmpty())
                        sb.append(indent(depth + 1)).append("<![CDATA[").append(v).append("]]>\n");
                    break;
                }
                case Node.PROCESSING_INSTRUCTION_NODE:
                    sb.append(indent(depth + 1)).append("<?").append(c.getNodeName())
                            .append(' ').append(c.getNodeValue()).append("?>\n");
                    break;
                default: // TEXT_NODE：非空白文本单独成行，纯空白由缩进策略接管
                    String v = c.getNodeValue();
                    if (v != null && !v.trim().isEmpty())
                        sb.append(indent(depth + 1)).append(escapeText(v.trim())).append('\n');
                    break;
            }
        }
        sb.append(indent).append("</").append(el.getTagName()).append(">\n");
    }

    private String indent(int depth) {
        StringBuilder sb = new StringBuilder(indentUnit.length() * depth);
        for (int i = 0; i < depth; i++) sb.append(indentUnit);
        return sb.toString();
    }

    private static String escapeText(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace("\"", "&quot;").replace(">", "&gt;");
    }

    /** 将当前 DOM 写回文本编辑器（整体替换，支持撤销） */
    public void writeBack() throws Exception {
        if (editor.isReadOnly()) {
            throw new IllegalStateException("当前编辑器处于只读模式，无法写回");
        }
        String xml = toXml();
        editor.startLargeBatchEditingMode();
        try {
            editor.replaceText(0, editor.length(), xml);
        } finally {
            editor.finishLargeBatchEditingMode();
        }
        editor.setSelection(0);
        editor.pushSelectionToUndoBuffer();
        editor.ensurePositionVisible(0, true);
        modified = false;
    }

    // ==================== manifest 根属性 ====================

    public String getPackage() {
        return doc.getDocumentElement().getAttribute("package");
    }

    public void setPackage(String value) {
        doc.getDocumentElement().setAttribute("package", value);
        modified = true;
    }

    public String getManifestAttr(String attr) {
        return doc.getDocumentElement().getAttribute("android:" + attr);
    }

    public void setManifestAttr(String attr, String value) {
        doc.getDocumentElement().setAttribute("android:" + attr, value);
        modified = true;
    }

    // ==================== uses-sdk ====================

    public Element getOrCreateUsesSdk() {
        Element root = doc.getDocumentElement();
        Element usesSdk = firstChild(root, "uses-sdk");
        if (usesSdk == null) {
            usesSdk = doc.createElement("uses-sdk");
            Node first = root.getFirstChild();
            if (first != null) {
                root.insertBefore(usesSdk, first);
            } else {
                root.appendChild(usesSdk);
            }
            modified = true;
        }
        return usesSdk;
    }

    public String getSdkAttr(String attr) {
        Element usesSdk = firstChild(doc.getDocumentElement(), "uses-sdk");
        if (usesSdk == null) return "";
        return usesSdk.getAttribute("android:" + attr);
    }

    public void setSdkAttr(String attr, String value) {
        if (value.isEmpty()) {
            // 值为空：仅在 uses-sdk 已存在时移除该属性，避免生成空的 <uses-sdk />
            Element usesSdk = firstChild(doc.getDocumentElement(), "uses-sdk");
            if (usesSdk != null && usesSdk.hasAttribute("android:" + attr)) {
                usesSdk.removeAttribute("android:" + attr);
                modified = true;
            }
        } else {
            getOrCreateUsesSdk().setAttribute("android:" + attr, value);
            modified = true;
        }
    }

    // ==================== application 属性 ====================

    public Element getOrCreateApplication() {
        Element root = doc.getDocumentElement();
        Element app = firstChild(root, "application");
        if (app == null) {
            app = doc.createElement("application");
            root.appendChild(app);
            modified = true;
        }
        return app;
    }

    public String getAppAttr(String attr) {
        Element app = firstChild(doc.getDocumentElement(), "application");
        if (app == null) return "";
        return app.getAttribute("android:" + attr);
    }

    public void setAppAttr(String attr, String value) {
        Element app = getOrCreateApplication();
        if (value.isEmpty()) {
            if (app.hasAttribute("android:" + attr)) {
                app.removeAttribute("android:" + attr);
                modified = true;
            }
        } else {
            app.setAttribute("android:" + attr, value);
            modified = true;
        }
    }

    // ==================== 权限管理 ====================

    /** 获取所有权限声明 */
    public List<PermissionEntry> listPermissions() {
        List<PermissionEntry> list = new ArrayList<>();
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;
            String tag = el.getTagName();
            if ("uses-permission".equals(tag) || "uses-permission-sdk-23".equals(tag)) {
                String name = el.getAttribute("android:name");
                list.add(new PermissionEntry(el, name, tag));
            }
        }
        return list;
    }

    /** 判断权限是否已存在 */
    public boolean hasPermission(String name) {
        for (PermissionEntry entry : listPermissions()) {
            if (name.equals(entry.name)) return true;
        }
        return false;
    }

    /** 添加一条权限（自动插入到 <application> 之前，保持合法结构） */
    public PermissionEntry addPermission(String name) {
        return addPermission(name, "uses-permission");
    }

    public PermissionEntry addPermission(String name, String tag) {
        Element root = doc.getDocumentElement();
        Element el = doc.createElement(tag);
        el.setAttribute("android:name", name);
        Element app = firstChild(root, "application");
        if (app != null) {
            root.insertBefore(el, app);
        } else {
            root.appendChild(el);
        }
        modified = true;
        return new PermissionEntry(el, name, tag);
    }

    /** 删除一条权限声明 */
    public void removePermission(PermissionEntry entry) {
        doc.getDocumentElement().removeChild(entry.element);
        modified = true;
    }

    // ==================== 组件 ====================

    /** 获取 application 下的组件列表 */
    public List<ComponentEntry> listComponents() {
        List<ComponentEntry> list = new ArrayList<>();
        Element app = firstChild(doc.getDocumentElement(), "application");
        if (app == null) return list;
        NodeList children = app.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            String tag = ((Element) node).getTagName();
            if ("activity".equals(tag) || "service".equals(tag) || "receiver".equals(tag)
                    || "provider".equals(tag) || "activity-alias".equals(tag)) {
                list.add(new ComponentEntry((Element) node));
            }
        }
        return list;
    }

    // ==================== 工具 ====================

    private static Element firstChild(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /**
     * 在编辑器原文中定位一段文本（如权限名、组件名），并把光标滚动到该处。
     */
    public boolean locateInEditor(String needle) {
        if (needle == null || needle.isEmpty()) return false;
        String fullText = editor.subText(0, editor.length());
        int pos = fullText.indexOf(needle);
        if (pos < 0) {
            // 尝试仅用短类名定位（如 .MainActivity -> MainActivity）
            String shortName = needle.substring(needle.lastIndexOf('.') + 1);
            if (!shortName.isEmpty()) {
                pos = fullText.indexOf(shortName);
            }
        }
        if (pos < 0) return false;
        editor.setSelection(pos, pos + needle.length());
        editor.ensurePositionVisibleInCenter(pos);
        return true;
    }
}
