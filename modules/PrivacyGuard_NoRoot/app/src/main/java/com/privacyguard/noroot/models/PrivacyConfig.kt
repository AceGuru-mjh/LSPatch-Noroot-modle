package com.privacyguard.noroot.models

/**
 * 隐私保护配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅应用进程内 Java 层 Hook
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不调用 Shizuku 做真Root操作（adb级Shizuku可选，用于轻量命令）
 *  - 伪造值仅在当前进程生命周期内稳定
 */
data class PrivacyConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,
    var deviceIdSpoofEnabled: Boolean = true,
    var clipboardGuardEnabled: Boolean = true,
    var clipboardBlockRead: Boolean = false,
    var permissionSpoofEnabled: Boolean = false,
    var locationSpoofEnabled: Boolean = false,
    var sensorFakerEnabled: Boolean = false,
    var advertisingIdBlockEnabled: Boolean = true,

    // ===== 实验性 =====
    var packageVisibilitySpoofEnabled: Boolean = false,
    var networkInfoSpoofEnabled: Boolean = false,
    var screenMetricsSpoofEnabled: Boolean = false,
    var storagePathSpoofEnabled: Boolean = false,

    // ===== v1.0.6 新增（对标 HideMyAndroid/伪造安装模块） =====
    var installStatusSpoofEnabled: Boolean = false,   // 应用安装状态伪造
    var mockLocationSystemLevelEnabled: Boolean = false, // 系统级Mock位置(仅Root)
    var profileSwitchEnabled: Boolean = false,          // 配置文件化(按场景切换)

    // ===== 参数 =====
    var spoofLatitude: Double = 31.2304,
    var spoofLongitude: Double = 121.4737,
    var sensorNoiseMode: Int = 2,          // 0=静态 其他=加噪
    var deniedPermissions: MutableList<String> = mutableListOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_CALENDAR"
    ),

    var lastModified: Long = 0L
)
