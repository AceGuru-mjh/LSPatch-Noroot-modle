package com.microx.enhancer.utils

import com.microx.enhancer.models.MicroXConfig
import com.google.gson.Gson

/**
 * Hook 侧配置读取器
 *
 * 优先使用 XSharedPreferences 直接读取模块 prefs 文件（LSPosed 模式，跨进程）。
 * 失败时回退到 Context-based ConfigManager（LSPatch 本地模式，同进程）。
 *
 * 注意：本读取器只读 KEY_* 布尔字段（兼容旧 Hook 代码使用 isEnabled(KEY_*) 模式）。
 * 高层 MicroXConfig 仅用于 UI；Hook 侧不需要构造 MicroXConfig 对象。
 */
object HookConfigReader {

    private const val MODULE_PKG = "com.microx.enhancer"
    private val gson = Gson()

    /**
     * 通过 XSharedPreferences 读取全局配置 JSON
     * @return 配置；读取失败返回 null
     */
    fun readGlobalJson(): String? {
        return try {
            val xsp = de.robv.android.xposed.XSharedPreferences(MODULE_PKG, ConfigManager.PREFS_NAME)
            xsp.makeWorldReadable()
            xsp.getString("global_config", null)
        } catch (_: Throwable) { null }
    }

    /**
     * 通过 XSharedPreferences 直接读取布尔字段
     * @return 配置；读取失败返回 null
     */
    fun readBoolean(key: String, default: Boolean): Boolean? {
        return try {
            val xsp = de.robv.android.xposed.XSharedPreferences(MODULE_PKG, ConfigManager.PREFS_NAME)
            xsp.makeWorldReadable()
            if (xsp.contains(key)) xsp.getBoolean(key, default) else null
        } catch (_: Throwable) { null }
    }

    /** 兼容方法：返回一个 MicroXConfig（基于 XSharedPreferences 重建） */
    fun readGlobal(): MicroXConfig? {
        return try {
            val xsp = de.robv.android.xposed.XSharedPreferences(MODULE_PKG, ConfigManager.PREFS_NAME)
            xsp.makeWorldReadable()
            val json = xsp.getString("global_config", null) ?: return null
            gson.fromJson(json, MicroXConfig::class.java)
        } catch (_: Throwable) { null }
    }
}
