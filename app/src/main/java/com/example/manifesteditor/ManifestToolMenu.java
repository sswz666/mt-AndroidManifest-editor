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
            // 只弹一个对话框，避免错误提示与 toast 同时出现互相遮挡
            String detail = e.getMessage() == null ? e.toString() : e.getMessage();
            pluginUI.buildDialog()
                    .setTitle("无法读取当前清单")
                    .setMessage("解析失败：" + detail
                            + "\n\n若当前文件是二进制 AXML（APK 内未反编译的清单），"
                            + "请先用 MT 的「反编译」功能转成文本格式再使用本插件。")
                    .setPositiveButton("{ok}", null)
                    .show();
        }
    }
}
