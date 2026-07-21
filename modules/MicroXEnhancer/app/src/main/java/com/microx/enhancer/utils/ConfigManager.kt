package com.microx.enhancer.utils

import android.content.Context
import android.content.SharedPreferences
import com.microx.enhancer.models.MicroXConfig

/**
 * 配置管理器
 *
 * 双通道读取：
 *  1. UI 侧（模块进程）：通过 SharedPreferences 读写
 *  2. Hook 侧（目标APP进程）：通过 XSharedPreferences 读取模块 prefs（LSPosed模式）
 *     或通过 Context.getSharedPreferences 读取（LSPatch本地模式，同进程）
 *
 * 保留 KEY_* 常量供旧 Hook 代码（基于 KEY_* + isEnabled）使用，
 * 同时新增 MicroXConfig 高层 API 供 Compose UI 使用，二者通过 KEY_* 映射同步。
 *
 * LSPosed 兼容：prefs 使用 MODE_WORLD_READABLE（LSPosed 拦截并放行），失败回退 MODE_PRIVATE。
 */
object ConfigManager {

    const val PREFS_NAME = "microx_enhancer_prefs"

    private const val KEY_GLOBAL = "global_config"

    // ===== 功能开关 Key 常量（保留兼容旧 Hook 代码）=====

    const val KEY_MASTER_SWITCH = "master_switch"
    const val KEY_BYPASS_DETECTION = "bypass_detection"

    // 广告净化（高层 adBlockEnabled 控制）
    const val KEY_AD_SPLASH = "ad_splash"
    const val KEY_AD_MOMENTS = "ad_moments"
    const val KEY_AD_OFFICIAL_ACCOUNT = "ad_official_account"
    const val KEY_AD_MINI_PROGRAM = "ad_mini_program"
    const val KEY_AD_CHAT_CARD = "ad_chat_card"

    // 消息增强
    const val KEY_ANTI_RECALL = "anti_recall"
    const val KEY_ANTI_DELETE_MOMENT = "anti_delete_moment"
    const val KEY_AUTO_REPLY = "auto_reply"
    const val KEY_MESSAGE_BACKUP = "message_backup"

    // 朋友圈
    const val KEY_SIMPLIFY_MOMENTS = "simplify_moments"

    // 界面美化（高层 uiModEnabled 控制）
    const val KEY_CUSTOM_BUBBLE = "custom_bubble"
    const val KEY_CUSTOM_FONT = "custom_font"
    const val KEY_HIDE_RED_DOT = "hide_red_dot"
    const val KEY_REMOVE_TAB = "remove_tab"
    const val KEY_CHAT_BACKGROUND = "chat_background"

    // 隐私增强（高层 privacyEnabled 控制）
    const val KEY_HIDE_TYPING = "hide_typing"
    const val KEY_HIDE_READ_STATUS = "hide_read_status"
    const val KEY_UNLIMITED_FORWARD = "unlimited_forward"
    const val KEY_FORCE_ORIGINAL = "force_original"
    const val KEY_NO_WATERMARK_SAVE = "no_watermark_save"

    // 批量管理（高层 batchManageEnabled 控制）
    const val KEY_ONE_KEY_CLEAN = "one_key_clean"
    const val KEY_EXPORT_FRIENDS = "export_friends"
    const val KEY_ZOMBIE_CHECK = "zombie_check"
    const val KEY_GROUP_MANAGE = "group_manage"

    // ===== 实验性 =====
    const val KEY_VOICE_MESSAGE_EXPORT = "voice_message_export"
    const val KEY_MESSAGE_SEARCH_ENHANCE = "message_search_enhance"
    const val KEY_CUSTOM_THEME = "custom_theme"

    // ===== v1.0.6 新增 =====
    const val KEY_AUTO_RED_PACKET = "auto_red_packet"
    const val KEY_AUTO_TRANSFER = "auto_transfer"
    const val KEY_STEP_MODIFIER = "step_modifier"
    const val KEY_DISABLE_HOT_UPDATE = "disable_hot_update"
    const val KEY_MOMENT_FAKE_LIKE = "moment_fake_like"
    const val KEY_AUTO_ORIGINAL_IMAGE = "auto_original_image"

    // ===== 计数 =====
    private const val KEY_BLOCKED_COUNT = "blocked_count"

    private var prefs: SharedPreferences? = null
    private val gson = com.google.gson.Gson()

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        // 首次运行写入默认值
        if (prefs?.contains(KEY_MASTER_SWITCH) == false) {
            setDefaults()
        }
    }

    fun isInitialized(): Boolean = prefs != null

    /** 累计计数（Hook 写入 → FloatingBallService / PanelActivity 读取） */
    fun getBlockedCount(): Long = try { prefs?.getLong(KEY_BLOCKED_COUNT, 0L) ?: 0L } catch (_: Throwable) { 0L }

    fun incrementBlockedCount(delta: Long = 1L) {
        try {
            prefs?.edit()?.putLong(KEY_BLOCKED_COUNT, getBlockedCount() + delta)?.apply()
        } catch (_: Throwable) {}
    }

    fun resetBlockedCount() {
        try { prefs?.edit()?.putLong(KEY_BLOCKED_COUNT, 0L)?.apply() } catch (_: Throwable) {}
    }

       /** 读取字符串配置（兼容旧Hook代码） */
    fun getString(key: String, default: String = ""): String {
        return prefs?.getString(key, default) ?: default
    }

    /** 写入字符串配置 */
    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    /** 读取全部配置Map（兼容旧Hook代码） */
    fun getAllConfig(): Map<String, Any?> {
        return prefs?.all ?: emptyMap()
    }

    /** 设置所有功能的默认开关值（首次运行触发） */
    private fun setDefaults() {
        val cfg = MicroXConfig(packageName = "global")
        applyMicroXConfig(cfg)
    }

    /** 旧 API：检查某项 KEY 是否启用（兼容旧 Hook 代码） */
    fun isMasterEnabled(): Boolean {
        return prefs?.getBoolean(KEY_MASTER_SWITCH, true) ?: true
    }

    fun isEnabled(key: String): Boolean {
        if (!isMasterEnabled()) return false
        return prefs?.getBoolean(key, true) ?: true
    }

    fun setEnabled(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun setMasterEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_MASTER_SWITCH, value)?.apply()
    }

    /** 读取全局 MicroXConfig（UI 用） */
    fun getGlobalConfig(): MicroXConfig {
        val def = MicroXConfig(packageName = "global")
        if (!isInitialized()) return def
        // 直接从 KEY_* 字段重建高层状态
        return MicroXConfig(
            packageName = "global",
            masterEnabled = isMasterEnabled(),
            adBlockEnabled = isEnabled(KEY_AD_SPLASH),
            antiRecallEnabled = isEnabled(KEY_ANTI_RECALL),
            momentProtectEnabled = isEnabled(KEY_ANTI_DELETE_MOMENT),
            uiModEnabled = isEnabled(KEY_HIDE_RED_DOT) || isEnabled(KEY_REMOVE_TAB),
            privacyEnabled = isEnabled(KEY_HIDE_TYPING),
            batchManageEnabled = isEnabled(KEY_ONE_KEY_CLEAN),
            autoReplyEnabled = isEnabled(KEY_AUTO_REPLY),
            voiceMessageExportEnabled = isEnabled(KEY_VOICE_MESSAGE_EXPORT),
            messageSearchEnhanceEnabled = isEnabled(KEY_MESSAGE_SEARCH_ENHANCE),
            customThemeEnabled = isEnabled(KEY_CUSTOM_THEME),
            autoRedPacketEnabled = isEnabled(KEY_AUTO_RED_PACKET),
            autoTransferEnabled = isEnabled(KEY_AUTO_TRANSFER),
            stepModifierEnabled = isEnabled(KEY_STEP_MODIFIER),
            disableHotUpdateEnabled = isEnabled(KEY_DISABLE_HOT_UPDATE),
            momentFakeLikeEnabled = isEnabled(KEY_MOMENT_FAKE_LIKE),
            unlimitedForwardEnabled = isEnabled(KEY_UNLIMITED_FORWARD),
            autoOriginalImageEnabled = isEnabled(KEY_AUTO_ORIGINAL_IMAGE),
            bypassDetectionEnabled = isEnabled(KEY_BYPASS_DETECTION)
        )
    }

    /** 保存全局 MicroXConfig（UI 用）：把高层状态展开为多个 KEY_* */
    fun saveGlobalConfig(cfg: MicroXConfig) {
        if (!isInitialized()) return
        cfg.lastModified = System.currentTimeMillis()
        applyMicroXConfig(cfg)
    }

    /** 把 MicroXConfig 展开为多个 KEY_* 写入 prefs */
    private fun applyMicroXConfig(cfg: MicroXConfig) {
        prefs?.edit()?.apply {
            putBoolean(KEY_MASTER_SWITCH, cfg.masterEnabled)
            putBoolean(KEY_BYPASS_DETECTION, cfg.bypassDetectionEnabled)

            // 广告净化
            putBoolean(KEY_AD_SPLASH, cfg.adBlockEnabled)
            putBoolean(KEY_AD_MOMENTS, cfg.adBlockEnabled)
            putBoolean(KEY_AD_OFFICIAL_ACCOUNT, cfg.adBlockEnabled)
            putBoolean(KEY_AD_MINI_PROGRAM, cfg.adBlockEnabled)
            putBoolean(KEY_AD_CHAT_CARD, cfg.adBlockEnabled)

            // 消息增强
            putBoolean(KEY_ANTI_RECALL, cfg.antiRecallEnabled)
            putBoolean(KEY_ANTI_DELETE_MOMENT, cfg.momentProtectEnabled)
            putBoolean(KEY_MESSAGE_BACKUP, false)
            putBoolean(KEY_AUTO_REPLY, cfg.autoReplyEnabled)

            // 朋友圈
            putBoolean(KEY_SIMPLIFY_MOMENTS, cfg.momentProtectEnabled)

            // 界面美化
            putBoolean(KEY_CUSTOM_BUBBLE, cfg.uiModEnabled)
            putBoolean(KEY_CUSTOM_FONT, false)
            putBoolean(KEY_HIDE_RED_DOT, cfg.uiModEnabled)
            putBoolean(KEY_REMOVE_TAB, cfg.uiModEnabled)
            putBoolean(KEY_CHAT_BACKGROUND, false)

            // 隐私增强
            putBoolean(KEY_HIDE_TYPING, cfg.privacyEnabled)
            putBoolean(KEY_HIDE_READ_STATUS, cfg.privacyEnabled)
            putBoolean(KEY_UNLIMITED_FORWARD, cfg.unlimitedForwardEnabled)
            putBoolean(KEY_FORCE_ORIGINAL, false)
            putBoolean(KEY_NO_WATERMARK_SAVE, false)

            // 批量管理
            putBoolean(KEY_ONE_KEY_CLEAN, cfg.batchManageEnabled)
            putBoolean(KEY_EXPORT_FRIENDS, cfg.batchManageEnabled)
            putBoolean(KEY_ZOMBIE_CHECK, cfg.batchManageEnabled)
            putBoolean(KEY_GROUP_MANAGE, cfg.batchManageEnabled)

            // 实验性
            putBoolean(KEY_VOICE_MESSAGE_EXPORT, cfg.voiceMessageExportEnabled)
            putBoolean(KEY_MESSAGE_SEARCH_ENHANCE, cfg.messageSearchEnhanceEnabled)
            putBoolean(KEY_CUSTOM_THEME, cfg.customThemeEnabled)

            // v1.0.6 新增
            putBoolean(KEY_AUTO_RED_PACKET, cfg.autoRedPacketEnabled)
            putBoolean(KEY_AUTO_TRANSFER, cfg.autoTransferEnabled)
            putBoolean(KEY_STEP_MODIFIER, cfg.stepModifierEnabled)
            putBoolean(KEY_DISABLE_HOT_UPDATE, cfg.disableHotUpdateEnabled)
            putBoolean(KEY_MOMENT_FAKE_LIKE, cfg.momentFakeLikeEnabled)
            putBoolean(KEY_AUTO_ORIGINAL_IMAGE, cfg.autoOriginalImageEnabled)
        }?.apply()
    }


    /** 导出全部配置为 JSON 字符串 */
    fun exportConfig(): String {
        val data = mutableMapOf<String, Any?>()
        try {
            prefs?.all?.forEach { (k, v) -> data[k] = v }
        } catch (_: Throwable) {}
        return gson.toJson(data)
    }

    /** 从 JSON 字符串导入配置，返回是否成功 */
    fun importConfig(json: String): Boolean {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
            val data: Map<String, Any?> = gson.fromJson(json, type) ?: return false
            prefs?.edit()?.clear()?.apply()
            val ed = prefs?.edit()
            data.forEach { (k, v) ->
                when (v) {
                    is String -> ed?.putString(k, v)
                    is Boolean -> ed?.putBoolean(k, v)
                    is Number -> ed?.putFloat(k, v.toFloat())
                    is com.google.gson.JsonObject -> ed?.putString(k, v.toString())
                    else -> ed?.putString(k, v?.toString())
                }
            }
            ed?.apply()
            true
        } catch (e: Throwable) { false }
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        setDefaults()
    }
}
