package com.gameunlocker.noroot.models

/**
 * 单个游戏配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅在目标游戏进程内做 Java 层 Hook
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不修改内核温控节点、不修改 CPU/GPU 调频策略
 *  - 不调用 Shizuku 做真 Root 操作（adb 级 Shizuku 仅用于轻量刷新率设置）
 *
 * 字段说明：
 *  - masterEnabled: 全局总开关（关闭则跳过所有 Hook）
 *  - 基础功能：机型伪装 / 帧率解锁 / 环境隐藏 / 进程优化 / 分辨率伪装
 *  - 实验性功能：触摸采样提升 / 网络延迟优化 / 音频优先级 / 内存整理
 */
data class GameConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // 机型伪装
    var deviceSpoofEnabled: Boolean = true,
    var selectedDeviceProfileId: String = "xiaomi15",
    var customDeviceProfile: DeviceProfile? = null,

    // 帧率解锁
    var frameRateUnlockEnabled: Boolean = true,
    var targetFps: Int = 120,

    // 环境隐藏
    var detectionHideEnabled: Boolean = true,
    var hideShizuku: Boolean = true,
    var hideXposed: Boolean = true,
    var hideLspatch: Boolean = true,

    // 进程优化（提升渲染线程优先级 + Hook PowerManager 热状态）
    var processOptimizeEnabled: Boolean = true,

    // 分辨率伪装
    var resolutionSpoofEnabled: Boolean = false,
    var spoofWidth: Int = 2560,
    var spoofHeight: Int = 1440,
    var spoofDpi: Int = 560,

    // ===== 实验性功能 =====
    var touchSamplingBoostEnabled: Boolean = false,
    var networkLatencyOptEnabled: Boolean = false,
    var audioPriorityBoostEnabled: Boolean = false,
    var memoryDefragEnabled: Boolean = false,

    var lastModified: Long = 0L
)
