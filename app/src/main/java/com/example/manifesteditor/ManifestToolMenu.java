package com.example.manifesteditor;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.editor.BaseTextEditorToolMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 文本编辑器工具菜单入口：
 * 在编辑器顶部工具栏「编辑」分组中添加「Manifest 编辑」按钮。
 * 仅在打开 AndroidManifest.xml 时显示。
 */
public class ManifestToolMenu extends BaseTextEditorToolMenu {

    @NonNull
    @Override
    public String name() {
        return "Manifest 编辑";
    }

    @NonNull
    @Override
    public Drawable icon() {
        return MaterialIcons.get("code");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        // 文件名为 AndroidManifest.xml（或以 Manifest.xml 结尾），或当前语法为 AXML 时显示
        String fileName = editor.getFileName();
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.equals("androidmanifest.xml") || lower.endsWith("manifest.xml")) {
                return true;
            }
        }
        String syntax = editor.getSyntaxName();
        return syntax != null && syntax.equalsIgnoreCase("AXML");
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        try {
            String text = editor.subText(0, editor.length());
            ManifestSession session = new ManifestSession(pluginUI, editor, ManifestSession.parse(text));
            session.indentUnit = ManifestSession.detectIndent(text);
            new ManifestUI(session).open();
        } catch (Exception e) {
            pluginUI.showErrorMessage(e);
            pluginUI.showToastL("解析失败：请确认当前文件是文本格式的 AndroidManifest.xml（二进制 AXML 请先反编译）");
        }
    }
}
