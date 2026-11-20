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
        builder.addText("4. 所有修改先保存在内存中，点「写回编辑器」写入文本（或直接点「写回并保存」一步到位）");

        builder.addHeader("写回方式");
        builder.addText("· 只改动真正变化的行：删权限只删那一行，加权限按相邻行的风格插入，改属性值只换引号内的内容");
        builder.addText("· 注释、空行、缩进、属性顺序、换行风格都会原样保留");
        builder.addText("· 结构变化过大时自动改为整篇重排，此时会有提示，不会写坏文件");

        builder.addHeader("权限管理");
        builder.addText("· 点击权限项可删除、查看详情（中文名称/保护级别）、复制权限名、定位到原文");
        builder.addText("· 通过「从分类添加」可按分类勾选常用权限（已知权限带中文说明）");
        builder.addText("· [危险] 权限在 Android 6.0+ 除清单声明外还需运行时动态申请");

        builder.addHeader("关于");
        builder.addText("Manifest 可视化编辑器 " + context.getPluginVersionName())
                .summary("基于 MT 插件 V3 API 开发");
        builder.addText("注意事项").summary("二进制 AXML（APK 内未反编译的清单）无法解析，请先用 MT 反编译成文本再编辑。");
    }
}
