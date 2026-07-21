package com.mjh.shizukufix.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mjh.shizukufix.models.ShizukuFixConfig

/**
 * 配置管理�?
 *
 * 双通道读取�?
 *  1. UI 侧（模块进程）：通过 SharedPreferences 读写
 *  2. Hook 侧（目标APP进程）：通过 XSharedPreferences 读取模块 prefs（LSPosed模式�?
 *     或通过 Context.getSharedPreferences 读取（LSPatch本地模式，同进程�?
 *
 * LSPosed 兼容：prefs 使用 MODE_WORLD_READABLE（LSPosed 拦截并放行），失败回退 MODE_PRIVATE�?
 */
object ConfigManager {

    const val PREFS_NAME = "shizuku_scene_fix_prefs"
    private const val KEY_ALL = "all_app_configs"
    private const val KEY_GLOBAL = "global_config"
    private const val KEY_BLOCKED_COUNT = "blocked_count"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isInitialized(): Boolean = prefs != null

    /** 累计计数（Hook 写入 �?FloatingBallService / PanelActivity 读取�?*/
    fun getBlockedCount(): Long = try { prefs?.getLong(KEY_BLOCKED_COUNT, 0L) ?: 0L } catch (_: Throwable) { 0L }

    fun incrementBlockedCount(delta: Long = 1L) {
        try {
            prefs?.edit()?.putLong(KEY_BLOCKED_COUNT, getBlockedCount() + delta)?.apply()
        } catch (_: Throwable) {}
    }

    fun resetBlockedCount() {
        try { prefs?.edit()?.putLong(KEY_BLOCKED_COUNT, 0L)?.apply() } catch (_: Throwable) {}
    }

    // ===== 全局配置（UI总开关使用，作用于所有目标APP�?=====
    fun getGlobalConfig(): ShizukuFixConfig {
        val def = ShizukuFixConfig(packageName = "global")
        if (!isInitialized()) return def
        val json = prefs?.getString(KEY_GLOBAL, null) ?: return def
        return try { gson.fromJson(json, ShizukuFixConfig::class.java) ?: def } catch (_: Throwable) { def }
    }

    fun saveGlobalConfig(cfg: ShizukuFixConfig) {
        if (!isInitialized()) return
        cfg.lastModified = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_GLOBAL, gson.toJson(cfg))?.apply()
    }

    // ===== 单APP配置（保留接口，兼容旧逻辑�?=====
    fun getAllConfigs(): MutableMap<String, ShizukuFixConfig> {
        if (!isInitialized()) return mutableMapOf()
        val json = prefs?.getString(KEY_ALL, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, ShizukuFixConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (_: Throwable) { mutableMapOf() }
    }

    fun getConfig(pkg: String): ShizukuFixConfig {
        return getAllConfigs()[pkg] ?: getGlobalConfig()
    }

    fun saveConfig(cfg: ShizukuFixConfig) {
        val all = getAllConfigs()
        cfg.lastModified = System.currentTimeMillis()
        all[cfg.packageName] = cfg
        prefs?.edit()?.putString(KEY_ALL, gson.toJson(all))?.apply()
    }


    /** 导出全部配置�?JSON 字符�?*/
    fun exportConfig(): String {
        val data = mutableMapOf<String, Any?>()
        try {
            prefs?.all?.forEach { (k, v) -> data[k] = v }
        } catch (_: Throwable) {}
        return gson.toJson(data)
    }

    /** �?JSON 字符串导入配置，返回是否成功 */
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

}
