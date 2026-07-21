package com.vipunlock.noroot.models

/**
 * VIP 解锁配置（免Root版）
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅 Hook 应用进程内 VIP 状态查询方法，不修改系统
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不调用 Shizuku 做真Root操作（adb级Shizuku可选，仅用于轻量命令）
 *  - 解锁效果依赖 APP 把 VIP 状态缓存在本地，强制停止后可能失效
 *
 * 字段说明：
 *  - masterEnabled: 总开关
 *  - xxxVipEnabled: 各 APP 单独 VIP 解锁开关
 *  - universalVipTryEnabled: 通用 VIP 尝试（Hook isVip/isPremium/getVipLevel 等通用方法名）
 *  - removeAdsEnabled: 实验性去广告（穿山甲/GDT/百度）
 *  - bypassVerifyEnabled: 实验性绕过签名/完整性校验
 */
data class VipConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 音乐类 VIP =====
    var netEaseVipEnabled: Boolean = true,        // 网易云音乐 黑胶VIP
    var qqMusicVipEnabled: Boolean = true,        // QQ音乐 绿钻
    var kugouVipEnabled: Boolean = true,          // 酷狗音乐 豪华VIP
    var kuwoVipEnabled: Boolean = true,           // 酷我音乐 SVIP

    // ===== 视频类 VIP =====
    var iqiyiVipEnabled: Boolean = true,          // 爱奇艺 黄金会员
    var youkuVipEnabled: Boolean = true,          // 优酷 VIP会员
    var tencentVideoVipEnabled: Boolean = true,   // 腾讯视频 SVIP
    var biliVipEnabled: Boolean = true,           // B站 大会员

    // ===== 阅读/资讯类 VIP =====
    var ximalayaVipEnabled: Boolean = true,       // 喜马拉雅 VIP
    var toutiaoVipEnabled: Boolean = false,       // 今日头条 关键功能解锁
    var zhihuVipEnabled: Boolean = true,          // 知乎 盐选会员

    // ===== 工具类 VIP =====
    var baiduNetdiskVipEnabled: Boolean = true,   // 百度网盘 SVIP
    var wpsVipEnabled: Boolean = true,            // WPS 超级会员
    var wereadVipEnabled: Boolean = true,         // 微信读书 无限卡

    // ===== Shizuku 增强 =====
    var shizukuVipDbEnabled: Boolean = false,      // Shizuku sqlite3/content/pm grant VIP 数据库增强

    // ===== 实验性 =====
    var universalVipTryEnabled: Boolean = false,  // 通用 VIP 尝试
    var removeAdsEnabled: Boolean = false,        // 通用去广告 SDK
    var bypassVerifyEnabled: Boolean = false,     // 绕过签名/完整性校验

    var lastModified: Long = 0L
)
