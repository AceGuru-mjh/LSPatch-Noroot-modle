package com.stepmod.noroot.models

/**
 * 步数修改配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅应用进程内 Java 层 Hook
 *  - 不修改系统传感器服务（system_server）
 *  - 不写 /sys /proc 内核节点（系统级步数注入由 Root 版承担）
 *  - 伪造步数仅在当前进程生命周期内有效，进程重启后从配置重新读取
 */
data class StepConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,           // 总开关
    var stepModifyEnabled: Boolean = true,       // 步数修改总开关（基础功能开关）

    // ===== 步数参数 =====
    var customSteps: Int = 10000,                // 目标步数
    var randomFluctuation: Int = 200,            // 随机波动±N步，避免固定值被识别

    // ===== 目标APP列表（用于运行时判断是否启用） =====
    var targetAppList: MutableList<String> = mutableListOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.tencent.tim",
        "com.xiaomi.hm.health",
        "com.huawei.health",
        "com.codoon.gps",
        "com.joyrun.gps",
        "com.keepfitness",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.netease.cloudmusic",
        "com.tencent.wmusic",
        "com.taobao.taobao",
        "com.jingdong.app.mall"
    ),

    // ===== 实验性功能 =====
    var sensorBlockEnabled: Boolean = false,         // 完全阻断步数传感器注册
    var multiAppSyncEnabled: Boolean = false,        // 多APP步数同步
    var stepHistoryFakeEnabled: Boolean = false,     // 步数历史伪造

    var lastModified: Long = 0L
)
