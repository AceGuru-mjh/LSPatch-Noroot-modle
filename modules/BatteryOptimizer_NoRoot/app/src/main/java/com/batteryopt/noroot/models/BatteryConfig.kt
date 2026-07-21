package com.batteryopt.noroot.models

/**
 * 省电优化配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅应用进程内 Java 层 Hook
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不调用 Shizuku 做真Root操作
 *  - 仅优化当前 APP 自身耗电行为，无法冻结其他 APP/不改系统 doze
 */
data class BatteryConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 应用层 Hook 开关 =====
    var wakeLockEnabled: Boolean = true,
    var alarmEnabled: Boolean = true,
    var syncEnabled: Boolean = true,
    var jobEnabled: Boolean = true,
    var locationEnabled: Boolean = true,
    var animationEnabled: Boolean = false,
    var sensorEnabled: Boolean = true,

    // ===== Shizuku 系统级（adb级，通过 ShizukuHelper） =====
    var appOpsRestrictEnabled: Boolean = false,        // cmd appops 系统级后台限制
    var appOpsRestrictedPackages: List<String> = listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker"
    ),
    var appOpsRestrictions: Map<String, String> = mapOf(
        "WAKE_LOCK" to "deny",
        "RUN_IN_BACKGROUND" to "deny",
        "BOOT_COMPLETED" to "deny"
    ),

    // ===== 实验性 =====
    /** 蓝牙扫描降频（Hook BluetoothLeScanner.startScan） */
    var bluetoothScanThrottleEnabled: Boolean = false,
    /** 后台相机调用阻断（Camera2/Camera open 在后台时返回异常阻止） */
    var cameraBackgroundBlockEnabled: Boolean = false,
    /** 振动器限频（Hook Vibrator.vibrate） */
    var vibratorThrottleEnabled: Boolean = false,

    // ===== 参数 =====
    /** WakeLock 最大持有时长（秒），超过自动 release。默认 60s */
    var wakeLockMaxHoldSec: Int = 60,
    /** 重复闹钟最小间隔（分钟），默认 5 分钟 */
    var alarmMinIntervalMin: Int = 5,

    var lastModified: Long = 0L
) {
    /** WakeLock 最大持有时长（毫秒），供 Hook 直接使用 */
    val wakeLockMaxHoldMs: Long get() = wakeLockMaxHoldSec * 1000L

    /** 重复闹钟最小间隔（毫秒） */
    val alarmMinIntervalMs: Long get() = alarmMinIntervalMin * 60 * 1000L

    /** 周期同步最小间隔（毫秒），默认 30 分钟 */
    val syncMinIntervalMs: Long get() = 30 * 60 * 1000L

    /** Job 最小周期（毫秒），默认 15 分钟 */
    val jobMinPeriodMs: Long get() = 15 * 60 * 1000L

    /** 是否给非紧急 Job 追加 requireDeviceIdle 约束 */
    val jobRequireIdle: Boolean get() = true

    /** 最低定位时间间隔（毫秒），默认 30s */
    val locationMinIntervalMs: Long get() = 30_000L

    /** 是否把后台高频 GPS 降级为网络定位 */
    val locationDowngradeGps: Boolean get() = true

    /** 是否将 setExact 降级为 setWindow */
    val alarmExactDowngrade: Boolean get() = true

    /** WakeLock 是否拦截冗余 SDK 统计类 */
    val wakeLockBlockRedundant: Boolean get() = true

    /** 动画 scale 值（0=关闭，1=正常） */
    val animationScale: Float get() = 0f

    /** 传感器采样周期上限（微秒），默认 200000us = 5Hz */
    val sensorMaxRateUs: Int get() = 200_000

    /** 蓝牙扫描最小间隔（毫秒），默认 60s */
    val bluetoothScanMinIntervalMs: Long get() = 60_000L

    /** 振动器最小触发间隔（毫秒），默认 1000ms */
    val vibratorMinIntervalMs: Long get() = 1000L
}
