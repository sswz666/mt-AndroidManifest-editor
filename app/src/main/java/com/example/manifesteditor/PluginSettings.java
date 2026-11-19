package com.example.manifesteditor;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * 插件设置界面：使用说明与关于信息。
 */
public class PluginSettings implements PluginPreference {

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        builder.title("Manifest 可视化编辑器");

        builder.addHeader("使用方法");
        builder.addText("1. 在 MT 管理器中打开 AndroidManifest.xml 文本文件");
        builder.addText("2. 点击编辑器顶部工具栏「编辑」分组中的「Manifest 编辑」按钮");
        builder.addText("3. 在弹出的面板中即可可视化查看与修改：权限管理、应用属性、版本与 SDK、组件列表");
        builder.addText("4. 所有修改先保存在内存中，点击「写回编辑器」才会写入文本，再由你自行保存文件");

        builder.addHeader("权限管理");
        builder.addText("· 点击权限项可删除、查看详情（中文名称/保护级别）、复制权限名、定位到原文");
        builder.addText("· 通过「从分类添加」可按分类勾选常用权限（已知权限带中文说明）");
        builder.addText("· [危险] 权限在 Android 6.0+ 除清单声明外还需运行时动态申请");

        builder.addHeader("关于");
        builder.addText("Manifest 可视化编辑器 " + context.getPluginVersionName())
                .summary("基于 MT 插件 V3 API 开发");
        builder.addText("注意事项").summary("写回时 XML 会重新格式化：保留注释，并自动沿用原文件的缩进风格。二进制 AXML 需先反编译为文本再编辑。");
    }
}
