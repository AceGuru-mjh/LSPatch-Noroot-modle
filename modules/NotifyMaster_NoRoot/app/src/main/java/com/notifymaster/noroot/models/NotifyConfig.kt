package com.notifymaster.noroot.models

/**
 * 通知管理配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅 Hook 应用进程内通知调用，不修改系统通知服务
 *  - 不 Hook system_server / NotificationManagerService
 *  - 不调用 Shizuku 做系统级通知策略修改
 *  - 通知历史仅保存在内存中，进程重启后消失
 */
data class NotifyConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 基础功能 =====
    var notifyFilterEnabled: Boolean = false,        // 通知过滤（关键词命中不显示）
    var antiRecallNotifyEnabled: Boolean = false,    // 防通知撤回
    var notifyHistoryEnabled: Boolean = false,       // 通知历史（内存）
    var notifyBeautifyEnabled: Boolean = false,      // 通知美化（图标/颜色/标题）

    // ===== 实验性 =====
    var batchNotifyEnabled: Boolean = false,         // 通知分组（同类合并为组）
    var priorityOverrideEnabled: Boolean = false,    // 通知优先级覆盖（强制 IMPORTANT）
    var silentNotifyEnabled: Boolean = false,        // 指定 APP 通知静默（不响铃不震动）

    // ===== Shizuku 增强 =====
    var shizukuNotifyCmdEnabled: Boolean = false,  // Shizuku 通知命令增强（cmd/dumpsys/settings）

    // ===== 参数 =====
    var filterKeywords: MutableList<String> = mutableListOf(
        "广告", "推广", "推广链接", "营销", "限时抢购"
    ),
    var targetApps: MutableList<String> = mutableListOf(
        "com.tencent.mm", "com.tencent.mobileqq", "com.eg.android.AlipayGphone",
        "com.taobao.taobao", "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.netease.cloudmusic",
        "com.tencent.wmusic", "com.sina.weibo", "com.zhihu.android",
        "com.baidu.searchbox", "com.ss.android.article.news"
    ),

    // ===== 美化参数 =====
    var beautifyColor: Int = 0xFF00695C.toInt(),     // 美化主色（青色）
    var beautifyTitlePrefix: String = "",            // 通知标题前缀
    var beautifyOverrideIcon: Boolean = false,        // 是否覆盖通知图标

    // ===== 优先级覆盖参数 =====
    var priorityOverrideLevel: Int = 2,              // 0=LOW 1=DEFAULT 2=HIGH 3=MAX

    // ===== 静默通知参数 =====
    var silentTargetApps: MutableList<String> = mutableListOf(),  // 静默生效的APP列表

    // ===== 通知分组参数 =====
    var batchGroupKey: String = "notifymaster_group",  // 分组 key
    var batchMaxCount: Int = 5,                          // 单组最大条数

    // ===== 通知智能分组（实验性）=====
    var notificationGroupingEnabled: Boolean = false,
    var groupingWindowMs: Long = 5000L,
    var maxUngroupedCount: Int = 3,

    // ===== VIP 白名单（实验性）=====
    var vipWhitelistEnabled: Boolean = false,
    var vipContactNames: List<String> = emptyList(),
    var vipContactApps: List<String> = emptyList(),

    // ===== 定时免打扰（实验性）=====
    var scheduleDndEnabled: Boolean = false,
    var weekdayStartHour: Int = 22,
    var weekdayEndHour: Int = 7,
    var weekendStartHour: Int = 23,
    var weekendEndHour: Int = 9,

    var lastModified: Long = 0L
)
