package com.example.manifesteditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限知识库：常用权限的中文名、保护级别，以及分类快捷添加表。
 */
public final class PermissionCatalog {

    /** 保护级别常量 */
    public static final int LEVEL_NORMAL = 0;      // 普通权限，安装即授予
    public static final int LEVEL_DANGEROUS = 1;   // 危险权限，Android 6.0+ 需运行时申请
    public static final int LEVEL_SIGNATURE = 2;   // 签名权限，仅系统应用/同签名应用可持有
    public static final int LEVEL_SPECIAL = 3;     // 特殊权限，需跳转系统设置手动授权

    /** 单个权限信息 */
    public static class Info {
        public final String name;       // 完整权限名
        public final String chinese;    // 中文名称
        public final int level;

        Info(String name, String chinese, int level) {
            this.name = name;
            this.chinese = chinese;
            this.level = level;
        }
    }

    private static final Map<String, Info> MAP = new LinkedHashMap<>();

    private static void add(String shortName, String chinese, int level) {
        MAP.put("android.permission." + shortName, new Info("android.permission." + shortName, chinese, level));
    }

    static {
        // ===== 网络相关 =====
        add("INTERNET", "网络访问（联网）", LEVEL_NORMAL);
        add("ACCESS_NETWORK_STATE", "查看网络状态", LEVEL_NORMAL);
        add("ACCESS_WIFI_STATE", "查看 WLAN 连接状态", LEVEL_NORMAL);
        add("CHANGE_WIFI_STATE", "更改 WLAN 连接状态", LEVEL_NORMAL);
        add("CHANGE_NETWORK_STATE", "更改网络连接状态", LEVEL_NORMAL);
        add("NEARBY_WIFI_DEVICES", "发现并连接附近的 WLAN 设备", LEVEL_DANGEROUS);
        add("ACCESS_WIFI_MULTICAST_STATE", "接收 WLAN 组播报文", LEVEL_NORMAL);

        // ===== 存储 / 媒体 =====
        add("READ_EXTERNAL_STORAGE", "读取外部存储（SD 卡）", LEVEL_DANGEROUS);
        add("WRITE_EXTERNAL_STORAGE", "写入外部存储（SD 卡）", LEVEL_DANGEROUS);
        add("MANAGE_EXTERNAL_STORAGE", "所有文件访问权限", LEVEL_SPECIAL);
        add("READ_MEDIA_IMAGES", "读取相册图片", LEVEL_DANGEROUS);
        add("READ_MEDIA_VIDEO", "读取相册视频", LEVEL_DANGEROUS);
        add("READ_MEDIA_AUDIO", "读取音乐音频", LEVEL_DANGEROUS);
        add("MANAGE_MEDIA", "修改媒体文件（无需逐次确认）", LEVEL_SPECIAL);

        // ===== 位置 =====
        add("ACCESS_FINE_LOCATION", "精确定位（GPS）", LEVEL_DANGEROUS);
        add("ACCESS_COARSE_LOCATION", "大致位置（基站/WiFi 定位）", LEVEL_DANGEROUS);
        add("ACCESS_BACKGROUND_LOCATION", "后台定位", LEVEL_DANGEROUS);
        add("ACCESS_LOCATION_EXTRA_COMMANDS", "位置额外命令", LEVEL_NORMAL);
        add("UWB_RANGING", "超宽带（UWB）测距", LEVEL_DANGEROUS);

        // ===== 相机 / 麦克风 / 传感器 =====
        add("CAMERA", "拍摄照片和视频（相机）", LEVEL_DANGEROUS);
        add("RECORD_AUDIO", "录音（麦克风）", LEVEL_DANGEROUS);
        add("BODY_SENSORS", "读取身体传感器（如心率）", LEVEL_DANGEROUS);
        add("ACTIVITY_RECOGNITION", "识别身体活动（运动检测）", LEVEL_DANGEROUS);

        // ===== 电话 / 短信 =====
        add("CALL_PHONE", "直接拨打电话", LEVEL_DANGEROUS);
        add("READ_PHONE_STATE", "读取手机状态和身份（含通话状态）", LEVEL_DANGEROUS);
        add("READ_PHONE_NUMBERS", "读取手机号码", LEVEL_DANGEROUS);
        add("ANSWER_PHONE_CALLS", "接听电话", LEVEL_DANGEROUS);
        add("USE_SIP", "使用 SIP 网络电话", LEVEL_DANGEROUS);
        add("SEND_SMS", "发送短信", LEVEL_DANGEROUS);
        add("RECEIVE_SMS", "接收短信", LEVEL_DANGEROUS);
        add("READ_SMS", "读取短信内容", LEVEL_DANGEROUS);
        add("RECEIVE_MMS", "接收彩信", LEVEL_DANGEROUS);
        add("RECEIVE_WAP_PUSH", "接收 WAP 推送消息", LEVEL_DANGEROUS);

        // ===== 联系人 / 日历 =====
        add("READ_CONTACTS", "读取联系人", LEVEL_DANGEROUS);
        add("WRITE_CONTACTS", "修改联系人", LEVEL_DANGEROUS);
        add("GET_ACCOUNTS", "获取设备上的账户列表", LEVEL_DANGEROUS);
        add("READ_CALENDAR", "读取日历", LEVEL_DANGEROUS);
        add("WRITE_CALENDAR", "写入日历", LEVEL_DANGEROUS);

        // ===== 蓝牙 =====
        add("BLUETOOTH", "使用蓝牙", LEVEL_NORMAL);
        add("BLUETOOTH_ADMIN", "管理蓝牙（发现/配对设备）", LEVEL_NORMAL);
        add("BLUETOOTH_SCAN", "扫描附近蓝牙设备", LEVEL_DANGEROUS);
        add("BLUETOOTH_CONNECT", "连接已配对的蓝牙设备", LEVEL_DANGEROUS);
        add("BLUETOOTH_ADVERTISE", "让附近蓝牙设备发现本机", LEVEL_DANGEROUS);

        // ===== 系统 / 其他 =====
        add("POST_NOTIFICATIONS", "发送通知", LEVEL_DANGEROUS);
        add("FOREGROUND_SERVICE", "使用前台服务", LEVEL_NORMAL);
        add("FOREGROUND_SERVICE_DATA_SYNC", "前台服务：数据同步", LEVEL_NORMAL);
        add("FOREGROUND_SERVICE_MEDIA_PLAYBACK", "前台服务：媒体播放", LEVEL_NORMAL);
        add("FOREGROUND_SERVICE_LOCATION", "前台服务：定位", LEVEL_NORMAL);
        add("FOREGROUND_SERVICE_CAMERA", "前台服务：相机", LEVEL_NORMAL);
        add("FOREGROUND_SERVICE_MICROPHONE", "前台服务：麦克风", LEVEL_NORMAL);
        add("RECEIVE_BOOT_COMPLETED", "开机自启动", LEVEL_NORMAL);
        add("WAKE_LOCK", "保持设备唤醒（防休眠）", LEVEL_NORMAL);
        add("VIBRATE", "控制振动", LEVEL_NORMAL);
        add("FLASHLIGHT", "使用闪光灯", LEVEL_NORMAL);
        add("SET_WALLPAPER", "设置壁纸", LEVEL_NORMAL);
        add("NFC", "使用 NFC 近场通信", LEVEL_NORMAL);
        add("BROADCAST_STICKY", "发送粘性广播", LEVEL_NORMAL);
        add("KILL_BACKGROUND_PROCESSES", "结束后台进程", LEVEL_NORMAL);
        add("MODIFY_AUDIO_SETTINGS", "修改全局音频设置", LEVEL_NORMAL);
        add("DISABLE_KEYGUARD", "禁用键盘锁", LEVEL_NORMAL);
        add("EXPAND_STATUS_BAR", "展开/收起状态栏", LEVEL_NORMAL);
        add("READ_SYNC_SETTINGS", "读取同步设置", LEVEL_NORMAL);
        add("WRITE_SYNC_SETTINGS", "写入同步设置", LEVEL_NORMAL);
        add("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "请求忽略电池优化", LEVEL_SPECIAL);
        add("REQUEST_INSTALL_PACKAGES", "安装应用（申请安装包）", LEVEL_SPECIAL);
        add("SYSTEM_ALERT_WINDOW", "显示悬浮窗（在其他应用上层绘制）", LEVEL_SPECIAL);
        add("WRITE_SETTINGS", "修改系统设置", LEVEL_SPECIAL);
        add("QUERY_ALL_PACKAGES", "查询设备上的所有应用", LEVEL_NORMAL);

        // ===== 签名级 / 系统级 =====
        add("INSTALL_PACKAGES", "静默安装应用（系统级）", LEVEL_SIGNATURE);
        add("DELETE_PACKAGES", "静默删除应用（系统级）", LEVEL_SIGNATURE);
        add("DUMP", "读取系统状态信息（dump）", LEVEL_SIGNATURE);
        add("READ_LOGS", "读取系统日志", LEVEL_SIGNATURE);
        add("WRITE_SECURE_SETTINGS", "写入安全系统设置（系统级）", LEVEL_SIGNATURE);
        add("STATUS_BAR", "控制系统状态栏", LEVEL_SIGNATURE);
        add("SET_TIME", "设置系统时间", LEVEL_SIGNATURE);
        add("SET_TIME_ZONE", "设置系统时区", LEVEL_SIGNATURE);
        add("MOUNT_UNMOUNT_FILESYSTEMS", "挂载/卸载文件系统", LEVEL_SIGNATURE);
        add("MOUNT_FORMAT_FILESYSTEMS", "格式化文件系统", LEVEL_SIGNATURE);
        add("PACKAGE_USAGE_STATS", "读取应用使用统计", LEVEL_SIGNATURE);
        add("ACCESS_MOCK_LOCATION", "模拟位置来源（测试用）", LEVEL_SIGNATURE);
        add("UPDATE_DEVICE_STATS", "更新设备电量统计", LEVEL_SIGNATURE);
    }

    private PermissionCatalog() {
    }

    /** 获取权限信息，未知权限返回 null */
    public static Info get(String name) {
        return MAP.get(name);
    }

    /** 获取展示名称：已知权限显示中文名，未知权限显示权限名 */
    public static String displayName(String name) {
        Info info = MAP.get(name);
        if (info != null) {
            String shortName = name.substring(name.lastIndexOf('.') + 1);
            return info.chinese + " (" + shortName + ")";
        }
        return name;
    }

    /** 获取保护级别描述 */
    public static String levelText(int level) {
        switch (level) {
            case LEVEL_DANGEROUS: return "危险权限：Android 6.0 及以上需在运行时动态申请";
            case LEVEL_SIGNATURE: return "签名权限：仅系统应用或同签名应用可持有";
            case LEVEL_SPECIAL:   return "特殊权限：需引导用户到系统设置中手动开启";
            default:              return "普通权限：安装后自动授予";
        }
    }

    /** 获取保护级别短标签 */
    public static String levelTag(int level) {
        switch (level) {
            case LEVEL_DANGEROUS: return "[危险]";
            case LEVEL_SIGNATURE: return "[签名]";
            case LEVEL_SPECIAL:   return "[特殊]";
            default:              return "[普通]";
        }
    }

    /** 快捷添加分类：分类名 -> 权限短名列表 */
    public static List<String> categories() {
        return Arrays.asList(
                "网络基础", "存储与媒体", "定位", "相机/麦克风",
                "电话/短信", "蓝牙/NFC", "通知/前台服务", "系统工具", "悬浮窗/安装");
    }

    /** 获取某分类下的权限完整名列表 */
    public static List<String> categoryPermissions(String category) {
        List<String> result = new ArrayList<>();
        switch (category) {
            case "网络基础":
                result.add("android.permission.INTERNET");
                result.add("android.permission.ACCESS_NETWORK_STATE");
                result.add("android.permission.ACCESS_WIFI_STATE");
                result.add("android.permission.CHANGE_WIFI_STATE");
                break;
            case "存储与媒体":
                result.add("android.permission.READ_EXTERNAL_STORAGE");
                result.add("android.permission.WRITE_EXTERNAL_STORAGE");
                result.add("android.permission.MANAGE_EXTERNAL_STORAGE");
                result.add("android.permission.READ_MEDIA_IMAGES");
                result.add("android.permission.READ_MEDIA_VIDEO");
                result.add("android.permission.READ_MEDIA_AUDIO");
                break;
            case "定位":
                result.add("android.permission.ACCESS_FINE_LOCATION");
                result.add("android.permission.ACCESS_COARSE_LOCATION");
                result.add("android.permission.ACCESS_BACKGROUND_LOCATION");
                break;
            case "相机/麦克风":
                result.add("android.permission.CAMERA");
                result.add("android.permission.RECORD_AUDIO");
                break;
            case "电话/短信":
                result.add("android.permission.CALL_PHONE");
                result.add("android.permission.READ_PHONE_STATE");
                result.add("android.permission.SEND_SMS");
                result.add("android.permission.READ_SMS");
                break;
            case "蓝牙/NFC":
                result.add("android.permission.BLUETOOTH");
                result.add("android.permission.BLUETOOTH_ADMIN");
                result.add("android.permission.BLUETOOTH_SCAN");
                result.add("android.permission.BLUETOOTH_CONNECT");
                result.add("android.permission.NFC");
                break;
            case "通知/前台服务":
                result.add("android.permission.POST_NOTIFICATIONS");
                result.add("android.permission.FOREGROUND_SERVICE");
                result.add("android.permission.FOREGROUND_SERVICE_DATA_SYNC");
                break;
            case "系统工具":
                result.add("android.permission.RECEIVE_BOOT_COMPLETED");
                result.add("android.permission.WAKE_LOCK");
                result.add("android.permission.VIBRATE");
                result.add("android.permission.WRITE_SETTINGS");
                break;
            case "悬浮窗/安装":
                result.add("android.permission.SYSTEM_ALERT_WINDOW");
                result.add("android.permission.REQUEST_INSTALL_PACKAGES");
                result.add("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                break;
        }
        return Collections.unmodifiableList(result);
    }
}
