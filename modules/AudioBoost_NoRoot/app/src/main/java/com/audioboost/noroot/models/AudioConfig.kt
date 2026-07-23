package com.audioboost.noroot.models

/**
 * 音量增强配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅 Hook 应用进程内音频 API（AudioTrack / MediaPlayer / AudioEffect / AudioManager）
 *  - 不修改系统音量服务（AudioFlinger / AudioPolicyManagerService）
 *  - 不写 /sys/class/audio 等系统节点
 *  - 不调用 Shizuku 做真Root操作（仅可选用 adb 级 Shizuku 做 dumpsys 等只读查询）
 *  - 增强效果仅在当前应用进程生命周期内有效
 */
data class AudioConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,
    var volumeBoostEnabled: Boolean = true,           // 音量增强
    var bassBoostEnabled: Boolean = false,            // 低音增强
    var equalizerEnabled: Boolean = false,            // 均衡器

    // ===== Shizuku 硬件级（adb级，通过 ShizukuHelper） =====
    var tinymixEnabled: Boolean = false,              // tinymix 硬件音频桥接
    var tinymixControls: Map<String, Int> = mapOf(
        "Speaker Volume" to 20,
        "Headphone Volume" to 15,
        "ADC1 Volume" to 12,
        "Bass Boost" to 1
    ),

    // ===== 实验性 =====
    var speakerBoostEnabled: Boolean = false,         // 扬声器增强（突破应用层音量上限）
    var micBoostEnabled: Boolean = false,             // 麦克风增益增强
    var audioQualityEnhanceEnabled: Boolean = false,  // 音质增强（提升采样率/位深）

    // ===== 参数 =====
    var boostLevel: Int = 150,          // 音量增益百分比，范围 100~300（100=原音量，150=放大1.5倍）
    var bassLevel: Int = 50,            // 低音强度百分比，范围 0~100
    var eqBands: MutableList<Int> = mutableListOf(  // 5段均衡器频段增益（毫贝尔 mb），范围 -1500~+1500
        0, 0, 0, 0, 0
    ),
    var micBoostLevel: Int = 150,       // 麦克风增益百分比，范围 100~300
    var targetSampleRate: Int = 48000,  // 目标采样率，默认 48000Hz
    var targetBitDepth: Int = 16,       // 目标位深，默认 16bit
    var speakerBoostMax: Int = 15,      // 扬声器突破音量上限的额外刻度

    // ===== Per-App 音量 =====
    var perAppVolumeEnabled: Boolean = false,
    var volumeProfiles: List<String> = emptyList(), // JSON array of {pkg, volume}

    // ===== 耳机自动切换 =====
    var headphoneAutoSwitchEnabled: Boolean = false,
    var headphoneBoostLevel: Int = 20,

    // ===== 定时静音 =====
    var scheduledMuteEnabled: Boolean = false,
    var nightStartHour: Int = 22,
    var nightEndHour: Int = 7,
    var nightVolumePercent: Int = 30,

    // ===== 蓝牙设备独立EQ =====
    var btDeviceEqEnabled: Boolean = false,
    var btDeviceProfiles: List<String> = emptyList(), // JSON array

    var lastModified: Long = 0L
)
