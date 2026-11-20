package com.example.manifesteditor;

import android.view.Gravity;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;

import bin.mt.plugin.api.ui.PluginEditText;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.PluginView;
import bin.mt.plugin.api.ui.builder.PluginRootLayoutBuilder;
import bin.mt.plugin.api.ui.builder.PluginSwitchButtonBuilder;
import bin.mt.plugin.api.ui.dialog.PluginDialog;
import bin.mt.plugin.api.util.ResultCallback;

/**
 * 可视化编辑器 UI：主面板 + 各功能子对话框。
 */
public class ManifestUI {

    private final ManifestSession session;
    private final PluginUI ui;
    private PluginDialog mainDialog;

    public ManifestUI(ManifestSession session) {
        this.session = session;
        this.ui = session.ui;
    }

    // ==================== 主面板 ====================

    public void open() {
        showMainDialog();
    }

    private void showMainDialog() {
        List<ManifestSession.PermissionEntry> perms = session.listPermissions();
        List<ManifestSession.ComponentEntry> comps = session.listComponents();
        int activities = 0, services = 0, receivers = 0, providers = 0;
        for (ManifestSession.ComponentEntry c : comps) {
            switch (c.tag) {
                case "activity": case "activity-alias": activities++; break;
                case "service": services++; break;
                case "receiver": receivers++; break;
                case "provider": providers++; break;
            }
        }

        String packageName = session.getPackage();
        String versionName = session.getManifestAttr("versionName");
        String versionCode = session.getManifestAttr("versionCode");
        String minSdk = session.getSdkAttr("minSdkVersion");
        String targetSdk = session.getSdkAttr("targetSdkVersion");

        PluginView view = ui.buildVerticalLayout()
                .addTextView().text("应用信息").bold().paddingBottomDp(4)
                .addTextView().text("包名：    " + (packageName.isEmpty() ? "(未声明)" : packageName)).textSize(13)
                .addTextView().text("版本：    " + (versionName.isEmpty() ? "-" : versionName)
                        + " (" + (versionCode.isEmpty() ? "-" : versionCode) + ")").textSize(13)
                .addTextView().text("SDK：     minSdk " + (minSdk.isEmpty() ? "-" : minSdk)
                        + " / targetSdk " + (targetSdk.isEmpty() ? "-" : targetSdk)).textSize(13)
                .addTextView().text("权限：    共 " + perms.size() + " 项权限声明").textSize(13)
                .addTextView().text("组件：    Activity " + activities + " · Service " + services
                        + " · Receiver " + receivers + " · Provider " + providers).textSize(13)
                .addView().height(1).widthMatchParent().marginTopDp(8).backgroundColor(ui.colorDivider())
                .addHorizontalLayout().marginTopDp(8).children(row -> row
                        .addButton("perms").text("权限管理").width(0).layoutWeight(1)
                        .addButton("attrs").text("应用属性").width(0).layoutWeight(1))
                .addHorizontalLayout().marginTopDp(8).children(row -> row
                        .addButton("sdk").text("版本与 SDK").width(0).layoutWeight(1)
                        .addButton("comps").text("组件列表").width(0).layoutWeight(1))
                .addView().height(1).widthMatchParent().marginTopDp(8).backgroundColor(ui.colorDivider())
                .addHorizontalLayout().marginTopDp(8).children(row -> row
                        .addButton("writeBack").text("写回编辑器").width(0).layoutWeight(1)
                        .addButton("reload").text("重新读取").width(0).layoutWeight(1))
                .addButton("save").text("写回并保存").widthMatchParent().marginTopDp(8)
                .build();

        view.requireViewById("perms").setOnClickListener(v -> showPermissionDialog());
        view.requireViewById("attrs").setOnClickListener(v -> showAppAttrsDialog());
        view.requireViewById("sdk").setOnClickListener(v -> showSdkDialog());
        view.requireViewById("comps").setOnClickListener(v -> showComponentsDialog());
        view.requireViewById("writeBack").setOnClickListener(v -> doWriteBack());
        view.requireViewById("reload").setOnClickListener(v -> reload());
        view.requireViewById("save").setOnClickListener(v -> doSave());

        mainDialog = ui.buildDialog()
                .setTitle("Manifest 可视化编辑器")
                .setView(view)
                .setPositiveButton("{close}", (dialog, which) -> {
                    if (session.modified) {
                        ui.showToastL("注意：还有修改没有写回编辑器，已丢弃");
                    }
                })
                .show();
    }

    private void doWriteBack() {
        try {
            if (!session.modified) {
                ui.showToast("没有待写回的修改");
                return;
            }
            session.writeBack();
            if (session.lastWriteBackWasFullRewrite) {
                ui.showToastL("已写回（检测到结构变化较大，本次为整篇重排），点击顶部保存按钮即可保存");
            } else {
                ui.showToastL("已写回编辑器（只改动了变动的行，原排版保留），点击顶部保存按钮即可保存");
            }
            if (mainDialog != null) mainDialog.dismiss();
        } catch (IllegalStateException e) {
            ui.showToastL(e.getMessage() == null ? "当前状态无法写回" : e.getMessage());
        } catch (Exception e) {
            ui.showErrorMessage(e);
        }
    }

    private void doSave() {
        try {
            // 有未写回的修改时先写回，否则"保存"保存的是没有改动的内容
            if (session.modified) session.writeBack();
        } catch (IllegalStateException e) {
            ui.showToastL(e.getMessage() == null ? "当前状态无法写回" : e.getMessage());
            return;
        } catch (Exception e) {
            ui.showErrorMessage(e);
            return;
        }
        session.editor.save(new ResultCallback() {
            @Override
            public void onSuccess() {
                ui.showToast("保存成功");
            }

            @Override
            public void onFailure(String message) {
                ui.showToast("保存失败: " + message);
            }
        });
    }

    private void reload() {
        if (session.modified) {
            // 重新读取会丢弃内存中的修改，先确认一下
            ui.buildDialog()
                    .setTitle("放弃未写回的修改？")
                    .setMessage("还有修改没有写回编辑器，重新读取会丢弃这些修改。")
                    .setPositiveButton("放弃并重新读取", (dialog, which) -> doReload())
                    .setNegativeButton("{cancel}", null)
                    .show();
            return;
        }
        doReload();
    }

    private void doReload() {
        try {
            String text = session.editor.subText(0, session.editor.length());
            session.doc = ManifestSession.parse(text);
            session.setOriginalText(text);
            session.modified = false;
            if (mainDialog != null) mainDialog.dismiss();
            showMainDialog();
            ui.showToast("已重新读取");
        } catch (Exception e) {
            ui.showErrorMessage(e);
        }
    }

    // ==================== 权限管理 ====================

    private void showPermissionDialog() {
        List<ManifestSession.PermissionEntry> perms = session.listPermissions();
        if (perms.isEmpty()) {
            ui.buildDialog()
                    .setTitle("权限管理")
                    .setMessage("当前清单中没有声明任何权限。\n\n点击下方「添加权限」快捷添加常用权限。")
                    .setNeutralButton("添加权限", (dialog, which) -> showAddPermissionDialog())
                    .setPositiveButton("{close}", null)
                    .show();
            return;
        }
        CharSequence[] items = new CharSequence[perms.size()];
        for (int i = 0; i < perms.size(); i++) {
            ManifestSession.PermissionEntry p = perms.get(i);
            PermissionCatalog.Info info = PermissionCatalog.get(p.name);
            String tag = info != null ? PermissionCatalog.levelTag(info.level) : "[未知]";
            String extra = "";
            String maxSdk = p.element.getAttribute("android:maxSdkVersion");
            if (!maxSdk.isEmpty()) extra = " · ≤" + maxSdk;
            if ("uses-permission-sdk-23".equals(p.tag)) extra += " · 23+";
            // 单行紧凑显示，完整权限名在详情页查看
            items[i] = tag + " " + PermissionCatalog.displayName(p.name) + extra;
        }

        ui.buildDialog()
                .setTitle("权限管理（共 " + perms.size() + " 项）")
                .setItems(items, (dialog, which) -> showPermissionActionDialog(perms.get(which)))
                .setNeutralButton("添加权限", (dialog, which) -> showAddPermissionDialog())
                .setPositiveButton("{close}", null)
                .show();
    }

    private void showPermissionActionDialog(ManifestSession.PermissionEntry entry) {
        CharSequence[] actions = new CharSequence[]{"❌ 删除此权限", "ℹ️ 查看权限详情", "📋 复制权限名", "📍 定位到原文"};
        ui.buildDialog()
                .setTitle(PermissionCatalog.displayName(entry.name))
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            session.removePermission(entry);
                            ui.showToastL("已删除该权限声明，点击主面板「写回编辑器」生效");
                            showPermissionDialog(); // 刷新列表
                            break;
                        case 1:
                            showPermissionDetail(entry);
                            break;
                        case 2:
                            session.ui.getContext().setClipboardText(entry.name, "已复制权限名");
                            showPermissionDialog(); // 返回列表
                            break;
                        case 3:
                            locateAndClose("\"" + entry.name + "\"", entry.name);
                            break;
                    }
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void showPermissionDetail(ManifestSession.PermissionEntry entry) {
        PermissionCatalog.Info info = PermissionCatalog.get(entry.name);
        String name = entry.name;
        String docUrl = officialDocUrl(name);
        String msg;
        if (info != null) {
            msg = "权限名称：" + info.chinese + "\n\n"
                    + "英文全名：" + name + "\n\n"
                    + "保护级别：" + PermissionCatalog.levelTag(info.level) + "\n"
                    + PermissionCatalog.levelText(info.level);
        } else {
            msg = "英文全名：" + name + "\n\n"
                    + "该权限不在已知权限库中，可能来自第三方库或自定义权限声明。";
        }
        String maxSdk = entry.element.getAttribute("android:maxSdkVersion");
        if (!maxSdk.isEmpty()) {
            msg += "\n\nmaxSdkVersion = " + maxSdk + "（仅在该 API 级别以下生效）";
        }
        if ("uses-permission-sdk-23".equals(entry.tag)) {
            msg += "\n\n该元素为 uses-permission-sdk-23，仅 Android 6.0 (API 23) 及以上生效。";
        }
        msg += docUrl != null
                ? "\n\n在线文档：" + docUrl
                : "\n\n该权限没有官方文档页，可点下方按钮搜索其用途。";

        final String url = docUrl != null ? docUrl : searchUrl(name);
        ui.buildDialog()
                .setTitle("权限详情")
                .setMessage(msg)
                .setPositiveButton("{close}", null)
                .setNeutralButton(docUrl != null ? "打开文档" : "搜索用途", (dialog, which) ->
                        session.ui.getContext().openBrowser(url))
                .show();
    }

    /**
     * 官方文档地址。Android 开发者文档把全部权限放在同一个页面，
     * 每个权限用锚点定位（…/Manifest.permission#CAMERA），
     * 不能写成 Manifest.permission.CAMERA（会 404）。
     * 使用国内可直连的 google.cn 镜像。
     */
    private static String officialDocUrl(String permissionName) {
        if (permissionName.startsWith("android.permission.")) {
            String shortName = permissionName.substring("android.permission.".length());
            return "https://developer.android.google.cn/reference/android/Manifest.permission?hl=zh-cn#" + shortName;
        }
        return null;
    }

    /** 非系统权限没有官方文档，退化为搜索引擎检索 */
    private static String searchUrl(String permissionName) {
        return "https://cn.bing.com/search?q=" + android.net.Uri.encode(permissionName + " Android permission");
    }

    // ==================== 添加权限 ====================

    private void showAddPermissionDialog() {
        PluginView view = ui.buildVerticalLayout()
                .addTextView("count").text("当前共 " + session.listPermissions().size() + " 项权限").textSize(13)
                .addEditText("input").hint("输入权限全名，如 android.permission.CAMERA")
                        .minLines(1).maxLines(1).marginTopDp(8)
                .addHorizontalLayout().marginTopDp(8).gravity(Gravity.CENTER_VERTICAL).children(row -> row
                        .addButton("fromCatalog").text("从分类添加").width(0).layoutWeight(1)
                        .addButton("add").text("添加").width(0).layoutWeight(1))
                .addTextView().text("提示：危险权限（Android 6.0+）除清单声明外，还需在代码中运行时动态申请才会真正授权。")
                        .textSize(12).textColor(ui.colorTextSecondary()).marginTopDp(8)
                .build();

        view.requireViewById("fromCatalog").setOnClickListener(v -> showCategoryPickDialog());
        view.requireViewById("add").setOnClickListener(v -> {
            PluginView root = v.getRootView();
            PluginEditText input = root.requireViewById("input");
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                ui.showToast("请输入权限名");
                return;
            }
            if (name.matches(".*\\s.*")) {
                ui.showToast("权限名不能包含空格");
                return;
            }
            if (!name.contains(".")) {
                name = "android.permission." + name;
            }
            if (session.hasPermission(name)) {
                ui.showToastL("该权限已存在，无需重复添加");
                return;
            }
            session.addPermission(name);
            // 刷新计数并清空输入，方便连续添加
            root.<bin.mt.plugin.api.ui.PluginTextView>requireViewById("count")
                    .setText("当前共 " + session.listPermissions().size() + " 项权限");
            input.setText("");
            ui.showToastL("已添加 " + PermissionCatalog.displayName(name) + "，点击主面板「写回编辑器」生效");
        });

        ui.buildDialog()
                .setTitle("添加权限")
                .setView(view)
                .setNegativeButton("{close}", null)
                .show();
    }

    private void showCategoryPickDialog() {
        List<String> categories = PermissionCatalog.categories();
        ui.buildDialog()
                .setTitle("选择权限分类")
                .setItems(categories.toArray(new CharSequence[0]), (dialog, which) ->
                        showCategoryPermDialog(categories.get(which)))
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void showCategoryPermDialog(String category) {
        List<String> names = PermissionCatalog.categoryPermissions(category);
        CharSequence[] items = new CharSequence[names.size()];
        boolean[] checked = new boolean[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            checked[i] = session.hasPermission(name);
            PermissionCatalog.Info info = PermissionCatalog.get(name);
            String tag = info != null ? PermissionCatalog.levelTag(info.level) : "[未知]";
            items[i] = tag + " " + PermissionCatalog.displayName(name);
        }

        ui.buildDialog()
                .setTitle(category + "（勾选 = 添加，取消 = 删除）")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("应用更改", (dialog, which) -> {
                    int added = 0, removed = 0;
                    for (int i = 0; i < names.size(); i++) {
                        String name = names.get(i);
                        boolean present = session.hasPermission(name);
                        if (checked[i] && !present) {
                            session.addPermission(name);
                            added++;
                        } else if (!checked[i] && present) {
                            for (ManifestSession.PermissionEntry entry : session.listPermissions()) {
                                if (name.equals(entry.name)) {
                                    session.removePermission(entry);
                                    removed++;
                                    break;
                                }
                            }
                        }
                    }
                    if (added == 0 && removed == 0) {
                        ui.showToastL("没有变化，未做任何修改");
                    } else {
                        ui.showToastL("已添加 " + added + " 项，移除 " + removed + " 项，点击主面板「写回编辑器」生效");
                    }
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    // ==================== 应用属性 ====================

    private void showAppAttrsDialog() {
        PluginRootLayoutBuilder b = ui.buildVerticalLayout();
        b.addTextView().text("基础属性").bold().paddingBottomDp(4);
        b.addHorizontalLayout().children(row -> row
                .addTextView("l1").text("名称 label")
                .addEditText("label").text(session.getAppAttr("label")).hint("@string/app_name").minLines(1).maxLines(1));
        b.addHorizontalLayout().marginTopDp(6).children(row -> row
                .addTextView("l2").text("图标 icon")
                .addEditText("icon").text(session.getAppAttr("icon")).hint("@mipmap/ic_launcher").minLines(1).maxLines(1));
        b.addHorizontalLayout().marginTopDp(6).children(row -> row
                .addTextView("l3").text("主题 theme")
                .addEditText("theme").text(session.getAppAttr("theme")).hint("@style/AppTheme").minLines(1).maxLines(1));
        b.addView().height(1).widthMatchParent().marginTopDp(8).backgroundColor(ui.colorDivider());
        b.addTextView().text("常用开关（未声明时使用系统默认值，切换后立即生效）").bold().marginTopDp(8).paddingBottomDp(4);

        addAttrSwitch(b, "allowBackup", "允许备份应用数据", true);
        addAttrSwitch(b, "debuggable", "可调试模式（发布版务必关闭）", false);
        addAttrSwitch(b, "hardwareAccelerated", "硬件加速", true);
        addAttrSwitch(b, "supportsRtl", "支持从右到左布局", false);
        addAttrSwitch(b, "usesCleartextTraffic", "允许明文 HTTP 流量", false);
        addAttrSwitch(b, "requestLegacyExternalStorage", "使用传统外部存储（Android 10/11 兼容）", false);

        b.addButton("apply").text("应用基础属性").widthMatchParent().marginTopDp(12);
        b.addTextView().text("提示：开关切换会立即写入内存；输入框清空后点「应用基础属性」表示删除该属性。最后回主面板点「写回编辑器」生效。")
                .textSize(12).textColor(ui.colorTextSecondary()).marginTopDp(8);

        PluginView view = b.build();

        PluginView root = view;
        view.requireViewById("apply").setOnClickListener(v -> {
            String label = root.<PluginEditText>requireViewById("label").getText().toString().trim();
            String icon = root.<PluginEditText>requireViewById("icon").getText().toString().trim();
            String theme = root.<PluginEditText>requireViewById("theme").getText().toString().trim();
            // 清空输入框 = 删除该属性
            session.setAppAttr("label", label);
            session.setAppAttr("icon", icon);
            session.setAppAttr("theme", theme);
            ui.showToastL("基础属性已应用，回到主面板点「写回编辑器」或「写回并保存」生效");
        });

        // 统一标签宽度
        view.unifyWidth("l1", "l2", "l3");

        ui.buildDialog()
                .setTitle("应用属性")
                .setView(view)
                .setNegativeButton("{close}", null)
                .show();
    }

    private void addAttrSwitch(PluginRootLayoutBuilder parent, String attr, String label, boolean defaultValue) {
        PluginSwitchButtonBuilder swb = parent.addSwitchButton("sw_" + attr)
                .text(label + "（android:" + attr + "）")
                .widthMatchParent();
        String value = session.getAppAttr(attr);
        boolean checked = value.isEmpty() ? defaultValue : "true".equals(value);
        if (checked) swb.check();
        // 对话框显示时系统可能为已勾选的开关补发一次同样值的回调，
        // 用「与当前值比较」过滤这类重复事件，既不弹多余 toast，也不会漏掉用户真实操作。
        final String[] lastValue = {checked ? "true" : "false"};
        swb.onCheckedChange((buttonView, isChecked) -> {
            String now = isChecked ? "true" : "false";
            if (now.equals(lastValue[0])) return;
            lastValue[0] = now;
            session.setAppAttr(attr, now);
        });
    }

    // ==================== 版本与 SDK ====================

    private void showSdkDialog() {
        PluginView view = ui.buildVerticalLayout()
                .addTextView().text("应用版本（manifest 根属性）").bold().paddingBottomDp(4)
                .addHorizontalLayout().children(row -> row
                        .addTextView("l1").text("包名")
                        .addEditText("pkg").text(session.getPackage()).hint("com.example.app").minLines(1).maxLines(1))
                .addHorizontalLayout().marginTopDp(6).children(row -> row
                        .addTextView("l2").text("版本号")
                        .addEditText("vcode").text(session.getManifestAttr("versionCode")).hint("1").minLines(1).maxLines(1))
                .addHorizontalLayout().marginTopDp(6).children(row -> row
                        .addTextView("l3").text("版本名")
                        .addEditText("vname").text(session.getManifestAttr("versionName")).hint("1.0.0").minLines(1).maxLines(1))
                .addView().height(1).widthMatchParent().marginTopDp(8).backgroundColor(ui.colorDivider())
                .addTextView().text("SDK 版本（uses-sdk）").bold().marginTopDp(8).paddingBottomDp(4)
                .addHorizontalLayout().children(row -> row
                        .addTextView("l4").text("minSdk")
                        .addEditText("minsdk").text(session.getSdkAttr("minSdkVersion")).hint("21").minLines(1).maxLines(1))
                .addHorizontalLayout().marginTopDp(6).children(row -> row
                        .addTextView("l5").text("targetSdk")
                        .addEditText("targetsdk").text(session.getSdkAttr("targetSdkVersion")).hint("34").minLines(1).maxLines(1))
                .addButton("apply").text("应用更改").widthMatchParent().marginTopDp(12)
                .addTextView().text("注意：由 Gradle 构建时，uses-sdk 中的值会被 build.gradle 覆盖；\npackage 属性在 AGP 7.3+ 建议由 namespace 声明。")
                        .textSize(12).textColor(ui.colorTextSecondary()).marginTopDp(8)
                .build();

        view.unifyWidth("l1", "l2", "l3", "l4", "l5");

        PluginView root = view;
        view.requireViewById("apply").setOnClickListener(v -> {
            String pkg = root.<PluginEditText>requireViewById("pkg").getText().toString().trim();
            String vcode = root.<PluginEditText>requireViewById("vcode").getText().toString().trim();
            String vname = root.<PluginEditText>requireViewById("vname").getText().toString().trim();
            String minsdk = root.<PluginEditText>requireViewById("minsdk").getText().toString().trim();
            String targetsdk = root.<PluginEditText>requireViewById("targetsdk").getText().toString().trim();
            if (!vcode.isEmpty() && !vcode.matches("\\d+")) {
                ui.showToast("版本号 versionCode 必须是数字");
                return;
            }
            if (!pkg.isEmpty()) session.setPackage(pkg);
            if (!vcode.isEmpty()) session.setManifestAttr("versionCode", vcode);
            if (!vname.isEmpty()) session.setManifestAttr("versionName", vname);
            session.setSdkAttr("minSdkVersion", minsdk);
            session.setSdkAttr("targetSdkVersion", targetsdk);
            ui.showToastL("已应用更改，点击主面板「写回编辑器」生效");
        });

        ui.buildDialog()
                .setTitle("版本与 SDK")
                .setView(view)
                .setNegativeButton("{close}", null)
                .show();
    }

    // ==================== 组件列表 ====================

    private void showComponentsDialog() {
        List<ManifestSession.ComponentEntry> comps = session.listComponents();
        if (comps.isEmpty()) {
            ui.buildDialog()
                    .setTitle("组件列表")
                    .setMessage("<application> 中没有声明任何组件。")
                    .setPositiveButton("{close}", null)
                    .show();
            return;
        }
        CharSequence[] items = new CharSequence[comps.size()];
        for (int i = 0; i < comps.size(); i++) {
            ManifestSession.ComponentEntry c = comps.get(i);
            String tagLabel;
            switch (c.tag) {
                case "activity": tagLabel = "Activity"; break;
                case "activity-alias": tagLabel = "Activity别名"; break;
                case "service": tagLabel = "Service"; break;
                case "receiver": tagLabel = "Receiver"; break;
                case "provider": tagLabel = "Provider"; break;
                default: tagLabel = c.tag;
            }
            String name = c.name.isEmpty() ? "(未命名)" : c.name;
            String launcher = isLauncherActivity(c.element) ? " [桌面入口]" : "";
            String exported = c.hasExported ? (c.exported ? " · 已导出" : " · 非导出") : "";
            items[i] = tagLabel + launcher + exported + "\n" + name;
        }

        ui.buildDialog()
                .setTitle("组件列表（共 " + comps.size() + " 个）")
                .setItems(items, (dialog, which) -> {
                    ManifestSession.ComponentEntry c = comps.get(which);
                    if (!c.name.isEmpty()) {
                        locateAndClose("\"" + c.name + "\"", c.name);
                    } else {
                        ui.showToast("该组件未声明 android:name");
                    }
                })
                .setPositiveButton("{close}", null)
                .show();
    }

    /** 判断组件是否声明了 MAIN + LAUNCHER 的 intent-filter（桌面入口） */
    private boolean isLauncherActivity(Element element) {
        return hasIntentFilter(element, "android.intent.action.MAIN", "android.intent.category.LAUNCHER");
    }

    private boolean hasIntentFilter(Element element, String action, String category) {
        NodeList filters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            org.w3c.dom.Node filter = filters.item(i);
            boolean hasAction = false, hasCategory = false;
            NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                if (!(child instanceof Element)) continue;
                String name = ((Element) child).getAttribute("android:name");
                if ("action".equals(child.getNodeName()) && action.equals(name)) hasAction = true;
                if ("category".equals(child.getNodeName()) && category.equals(name)) hasCategory = true;
            }
            if (hasAction && hasCategory) return true;
        }
        return false;
    }

    // ==================== 工具 ====================

    /** 定位到编辑器原文，并关闭主面板让用户直接看到定位结果 */
    private void locateAndClose(String primaryNeedle, String fallbackNeedle) {
        boolean ok = session.locateInEditor(primaryNeedle);
        if (!ok) {
            ok = session.locateInEditor(fallbackNeedle);
        }
        // 定位失败时保留面板，方便用户换个操作，而不是把面板关掉
        if (ok) {
            if (mainDialog != null) mainDialog.dismiss();
            if (session.modified) {
                ui.showToastL("已定位到原文；注意还有修改没有写回，已丢弃");
            } else {
                ui.showToast("已在原文中定位");
            }
        } else {
            ui.showToast("未能在编辑器原文中找到该内容");
        }
    }
}
