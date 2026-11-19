# Manifest 可视化编辑器 — MT 管理器插件

在 MT 管理器的文本编辑器中查看/编辑 `AndroidManifest.xml` 时，点击工具栏「编辑」分组中的 **「Manifest 编辑」** 按钮，即可打开可视化面板，集中查看与修改清单内容，核心是**权限的可视化管理**。

## 功能一览

| 功能 | 说明 |
|---|---|
| **概览面板** | 包名、版本、minSdk/targetSdk、权限数量、四大组件数量一目了然 |
| **权限管理** | 列出全部 `uses-permission` / `uses-permission-sdk-23` 声明，已知权限显示**中文名称**和**保护级别标签**（[危险]/[签名]/[特殊]/[普通]） |
| **快捷操作** | 点击任一权限 → 删除 / 查看详情（中文说明 + 在线文档）/ 复制权限名 / **定位到原文** |
| **快捷添加** | 按分类勾选常用权限（网络、存储、定位、相机、电话短信、蓝牙 NFC、通知、悬浮窗安装等 9 类），勾选即添加、取消即删除；也支持手动输入任意权限名 |
| **应用属性** | label / icon / theme 编辑框 + allowBackup、debuggable、硬件加速、supportsRtl、明文流量等常用开关（开关切换即生效到内存中的清单） |
| **版本与 SDK** | 修改 package、versionCode、versionName、minSdkVersion、targetSdkVersion |
| **组件列表** | 查看 Activity / Service / Receiver / Provider / 别名，标记桌面入口（MAIN+LAUNCHER），点击可定位到原文 |
| **写回编辑器** | 所有修改先存内存，点击写回后整体替换编辑器文本（支持 MT 撤销），再由用户保存文件 |

## 安装

### 方式一：安装打包好的 .mtp
1. 把 `app/build/outputs/mt-plugin/` 下的 `.mtp` 文件拷贝到手机；
2. 用 MT 管理器打开该文件 → 点击「安装」；
3. 重启 MT 管理器生效。

> 安装/运行插件需要 **MT 管理器 2.26.3+** 和 **VIP** 权限。

### 方式二：自己从源码打包
```bash
# 需要 JDK 17+，首次运行会自动下载依赖
./gradlew app:packageReleaseMtp
# 产物位于 app/build/outputs/mt-plugin/
```

用 Android Studio 打开项目后，直接 Run `app` 模块也可以把插件推送到设备（仅限自己测试，testOnly 包不能分发）。

## 使用流程

1. 打开 APK / 安装包中反编译出的 `AndroidManifest.xml`（纯文本即可，MT 自带的 AXML 高亮界面直接支持）；
2. 点击工具栏「编辑」→「Manifest 编辑」；
3. 例如要删掉一项危险权限：**权限管理** → 点该权限 → **删除此权限** → 返回主面板 → **写回编辑器** → 顶部保存；
4. 例如要加网络权限：**权限管理** → **添加权限** → **从分类添加** → 勾选「网络访问」→ 应用更改 → 写回编辑器。

## 目录结构

```
app/src/main/java/com/example/manifesteditor/
├── ManifestToolMenu.java   # 编辑器工具栏入口（仅 AndroidManifest.xml 显示）
├── ManifestUI.java         # 全部可视化对话框（主面板/权限/属性/SDK/组件）
├── ManifestSession.java    # DOM 解析、修改、序列化、写回编辑器
├── PermissionCatalog.java  # 权限知识库：中文翻译、保护级别、分类快捷表
└── PluginSettings.java     # 插件设置页（使用说明）
```

## 注意事项

- 写回时 XML 会**重新格式化**：自动检测并沿用原文件的缩进风格（2/4 空格或 Tab），XML 声明独立一行，注释原样保留（v1.1 起）；
- 二进制 AXML（未反编译的 APK 内清单）无法解析，会提示错误——请先用 MT 反编译再编辑；
- [危险] 权限仅是清单声明，Android 6.0+ 真正授权还需应用代码运行时申请，本插件详情页已注明；
- 由 Gradle 构建的应用，`uses-sdk` 与 `package` 会被构建脚本覆盖，属正常现象。

## 更新记录

### v1.1
- **修复写回后格式被压缩**：弃用 Android 系统 Transformer（它会丢掉缩进、把声明和根标签挤在一行），改为自绘格式化序列化器，并自动检测原文件缩进风格（2 空格 / 4 空格 / Tab）；
- **权限列表改为单行紧凑显示**（`[级别] 中文名 (SHORT)`），完整权限名在详情页查看；
- **修复「应用属性」弹多个 toast**：开关按钮在对话框首次显示时会被系统补发回调，已过滤初始回调并移除开关的即时 toast。

### v1.2
- **修复权限详情「打开文档」跳转 404**：Android 官方文档把全部权限放在同一页、用锚点定位，正确地址是 `reference/android/Manifest.permission?hl=zh-cn#CAMERA`（原实现拼成 `Manifest.permission.CAMERA`，必然 404）；同时改用国内可直连的 `developer.android.google.cn` 镜像并带中文语言参数；
- 非 `android.permission.*` 的权限（第三方库/自定义）不再拼官方链接，详情页改为「搜索用途」（Bing 检索）；
- 修复「添加权限」成功后计数不刷新、输入框不清空的问题，现在可连续添加；
- 修复打开文件失败时错误对话框与 toast 同时弹出的问题，改为单个说明对话框（含二进制 AXML 提示）；
- 修复「应用属性」开关过滤逻辑：原 600ms 时间窗口会误吞刚打开对话框时的真实点击，改为与当前值比较；
- 版本与 SDK 页清空 minSdk/targetSdk 时不再生成空的 `<uses-sdk />`；
- 修正两处权限等级标注（MANAGE_MEDIA 为 signature|appop、PACKAGE_USAGE_STATS 为 appop 特殊权限）；
- 组件列表「exported」改为中文；设置页版本号改为动态读取，不再写死。
