package com.microx.enhancer.models

/**
 * MicroX 增强配置（高层数据模型，供 Compose UI 使用）
 *
 * 注意：本模型仅用于 UI 状态管理。实际 Hook 读取由 ConfigManager（KEY_* 常量）负责，
 * UI 通过 MicroXConfig ↔ ConfigManager 双向同步保持一致。
 *
 * 高层开关映射规则（一个高层开关可能对应多个底层 KEY_*）：
 *  - adBlockEnabled        -> KEY_AD_SPLASH / KEY_AD_MOMENTS / KEY_AD_OFFICIAL_ACCOUNT /
 *                              KEY_AD_MINI_PROGRAM / KEY_AD_CHAT_CARD
 *  - antiRecallEnabled     -> KEY_ANTI_RECALL
 *  - momentProtectEnabled  -> KEY_ANTI_DELETE_MOMENT / KEY_SIMPLIFY_MOMENTS
 *  - uiModEnabled          -> KEY_CUSTOM_BUBBLE / KEY_CUSTOM_FONT / KEY_HIDE_RED_DOT /
 *                              KEY_REMOVE_TAB / KEY_CHAT_BACKGROUND
 *  - privacyEnabled        -> KEY_HIDE_TYPING / KEY_HIDE_READ_STATUS / KEY_UNLIMITED_FORWARD /
 *                              KEY_FORCE_ORIGINAL / KEY_NO_WATERMARK_SAVE
 *  - batchManageEnabled    -> KEY_ONE_KEY_CLEAN / KEY_EXPORT_FRIENDS / KEY_ZOMBIE_CHECK /
 *                              KEY_GROUP_MANAGE
 *  - autoReplyEnabled      -> KEY_AUTO_REPLY
 *
 * 实验性：
 *  - voiceMessageExportEnabled    -> KEY_VOICE_MESSAGE_EXPORT（新增）
 *  - messageSearchEnhanceEnabled  -> KEY_MESSAGE_SEARCH_ENHANCE（新增）
 *  - customThemeEnabled           -> KEY_CUSTOM_THEME（新增）
 */
data class MicroXConfig(
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 通用功能 =====
    var adBlockEnabled: Boolean = true,
    var antiRecallEnabled: Boolean = true,
    var momentProtectEnabled: Boolean = true,
    var uiModEnabled: Boolean = false,
    var privacyEnabled: Boolean = true,
    var batchManageEnabled: Boolean = false,
    var autoReplyEnabled: Boolean = false,

    // ===== 实验性 =====
    var voiceMessageExportEnabled: Boolean = false,
    var messageSearchEnhanceEnabled: Boolean = false,
    var customThemeEnabled: Boolean = false,

    // ===== v1.0.6 新增（对标 NewMiko/FkWeChat） =====
    var autoRedPacketEnabled: Boolean = false,       // 自动抢红包
    var autoTransferEnabled: Boolean = false,         // 自动收取转账
    var stepModifierEnabled: Boolean = false,         // 运动步数修改
    var disableHotUpdateEnabled: Boolean = false,     // 禁用微信热更新
    var momentFakeLikeEnabled: Boolean = false,       // 朋友圈伪集赞
    var unlimitedForwardEnabled: Boolean = false,     // 去除9人转发限制
    var autoOriginalImageEnabled: Boolean = false,    // 自动发送原图

    // ===== Shizuku 系统级（adb级，通过 ShizukuHelper） =====
    var shizukuDbAccessEnabled: Boolean = false,     // sqlite3 数据库直读（消息/语音导出）
    var wechatDbPath: String = "",                   // 微信数据库路径（空则默认 EnMicroMsg.db）

    // ===== 适配辅助 =====
    /** 绕过微信/QQ安全检测（保留兼容旧 KEY_BYPASS_DETECTION） */
    var bypassDetectionEnabled: Boolean = true,

    var lastModified: Long = 0L
)
