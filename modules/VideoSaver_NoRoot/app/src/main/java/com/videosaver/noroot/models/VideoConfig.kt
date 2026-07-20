package com.videosaver.noroot.models

/**
 * 短视频去水印下载配置（免 Root 版）
 *
 * 硬性限制（NoRoot 版严格遵守）：
 *  - 仅 Hook 应用进程内下载方法，不修改系统
 *  - 不调用 Shizuku 做真 Root 操作
 *  - 不写 /system /sys /proc 系统节点
 *  - 不 Hook system_server
 *  - 配置仅在目标 APP 进程内生效
 *
 * 字段说明：
 *  - masterEnabled：总开关，关闭后所有 Hook 不生效
 *  - 抖音/快手/小红书/B站：各平台无水印下载开关
 *  - customSavePath：自定义保存路径（如 /sdcard/Download/VideoSaver/）
 *  - 实验性：自动下载/去广告/原画质/批量下载
 */
data class VideoConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 各平台无水印下载 =====
    var douyinNoWatermark: Boolean = true,
    var kuaishouNoWatermark: Boolean = true,
    var xhsNoWatermark: Boolean = true,
    var biliDownload: Boolean = true,

    // ===== 保存路径 =====
    var customSavePath: String = "/sdcard/Download/VideoSaver/",
    var autoRenameEnabled: Boolean = true,           // 自动重命名（平台_时间戳.mp4）

    // ===== 实验性 =====
    var autoDownloadEnabled: Boolean = false,         // 自动下载（播放视频时自动触发保存）
    var removeAdsEnabled: Boolean = false,            // 去视频广告
    var saveOriginalQualityEnabled: Boolean = false,  // 强制原画质下载
    var batchDownloadEnabled: Boolean = false,        // 批量下载（用户主页/合集）

    // ===== 元数据 =====
    var lastModified: Long = 0L
)
