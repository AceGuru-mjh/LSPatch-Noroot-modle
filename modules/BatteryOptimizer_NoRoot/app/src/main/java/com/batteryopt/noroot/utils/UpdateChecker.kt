package com.batteryopt.noroot.utils

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 热更新检查器（增强版�?
 *
 * 新增能力�?
 *  - 忽略指定版本（用户不想更新的版本不再提示�?
 *  - 自动检查偏好开关（持久化）
 *  - 上次检查时间记录（避免频繁请求 GitHub API�?
 *  - 下载缓存清理
 *  - 上次缓存�?UpdateInfo（避免重复网络请求）
 *
 * 数据�? https://api.github.com/repos/AceGuru-mjh/LSPatch-Noroot-modle/releases/latest
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO = "AceGuru-mjh/LSPatch-Noroot-modle"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_IGNORED_VERSION = "ignored_version"
    private const val KEY_AUTO_CHECK = "auto_check"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_LAST_UPDATE_INFO = "last_update_info"
    private const val MIN_CHECK_INTERVAL_MS = 5 * 60 * 1000L  // 最�?分钟间隔

    data class UpdateInfo(
        val latestVersion: String,
        val tagName: String,
        val releaseUrl: String,
        val releaseNotes: String,
        val publishDate: String,
        val apkAssets: List<ApkAsset>,
        val hasUpdate: Boolean,
        val currentVersion: String,
        val isIgnored: Boolean
    )

    data class ApkAsset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long
    )

    private var cachedInfo: UpdateInfo? = null
    private var cachedContext: Context? = null

    /** 初始化（�?Application.onCreate 调用，存 Context 用于 prefs�?*/
    fun init(context: Context) {
        cachedContext = context.applicationContext
    }

    private fun prefs() = cachedContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 检查更新（同步，需子线程调用） */
    fun checkUpdate(currentVersion: String, force: Boolean = false): UpdateInfo? {
        // 非强制时�?分钟内不重复请求
        if (!force) {
            val last = prefs()?.getLong(KEY_LAST_CHECK_TIME, 0L) ?: 0L
            if (System.currentTimeMillis() - last < MIN_CHECK_INTERVAL_MS) {
                Log.d(TAG, "距上次检查不�?分钟，返回缓�?)
                return cachedInfo ?: loadCachedInfo(currentVersion)
            }
        }
        val result = fetchFromGithub(currentVersion)
        if (result != null) {
            cachedInfo = result
            prefs()?.edit()?.apply {
                putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                putString(KEY_LAST_UPDATE_INFO, serializeInfo(result))
                apply()
            }
        }
        return result
    }

    private fun fetchFromGithub(currentVersion: String): UpdateInfo? {
        return try {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LSP-Model-UpdateChecker")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API 返回 ${conn.responseCode}")
                return null
            }
            val raw = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            parseRelease(raw, currentVersion)
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失�? ${e.message}")
            null
        }
    }

    private fun parseRelease(json: String, currentVersion: String): UpdateInfo? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val tagName = root.get("tag_name")?.asString ?: return null
            val latestVersion = tagName.removePrefix("v").trim()
            val releaseUrl = root.get("html_url")?.asString ?: ""
            val releaseNotes = root.get("body")?.asString ?: ""
            val publishDate = root.get("published_at")?.asString ?: ""

            val assets = mutableListOf<ApkAsset>()
            root.getAsJsonArray("assets")?.forEach { el ->
                val a = el.asJsonObject
                val name = a.get("name")?.asString ?: return@forEach
                if (name.endsWith(".apk")) {
                    assets.add(ApkAsset(
                        name = name,
                        downloadUrl = a.get("browser_download_url")?.asString ?: "",
                        sizeBytes = a.get("size")?.asLong ?: 0L
                    ))
                }
            }

            val ignored = getIgnoredVersion()
            UpdateInfo(
                latestVersion = latestVersion,
                tagName = tagName,
                releaseUrl = releaseUrl,
                releaseNotes = releaseNotes,
                publishDate = publishDate,
                apkAssets = assets,
                hasUpdate = compareVersion(latestVersion, currentVersion) > 0,
                currentVersion = currentVersion,
                isIgnored = latestVersion == ignored
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析 Release 失败: ${e.message}")
            null
        }
    }

    /** 版本号比�? >0 表示 a 更新 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    /** 找匹�?APK */
    fun findMatchingApk(info: UpdateInfo, moduleName: String): ApkAsset? {
        return info.apkAssets.firstOrNull { it.name.startsWith(moduleName, ignoreCase = true) }
            ?: info.apkAssets.firstOrNull()
    }

    // ===== 忽略版本 =====
    fun getIgnoredVersion(): String? = prefs()?.getString(KEY_IGNORED_VERSION, null)
    fun ignoreVersion(version: String) {
        prefs()?.edit()?.putString(KEY_IGNORED_VERSION, version)?.apply()
    }
    fun clearIgnored() {
        prefs()?.edit()?.remove(KEY_IGNORED_VERSION)?.apply()
    }

    // ===== 自动检查开�?=====
    fun isAutoCheckEnabled(): Boolean = prefs()?.getBoolean(KEY_AUTO_CHECK, true) ?: true
    fun setAutoCheck(enabled: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_AUTO_CHECK, enabled)?.apply()
    }

    // ===== 缓存管理 =====
    /** 清理已下载的 APK 缓存 */
    fun clearDownloadCache(context: Context): Long {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += f.length()
            f.delete()
        }
        return size
    }

    /** 获取下载缓存大小 */
    fun getDownloadCacheSize(context: Context): Long {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    // ===== 缓存序列化（简单实现） =====
    private fun serializeInfo(info: UpdateInfo): String {
        // �?JSON 简单序列化核心字段
        return """{"latestVersion":"${info.latestVersion}","tagName":"${info.tagName}","releaseUrl":"${info.releaseUrl}","publishDate":"${info.publishDate}","hasUpdate":${info.hasUpdate}}"""
    }

    private fun loadCachedInfo(currentVersion: String): UpdateInfo? {
        val json = prefs()?.getString(KEY_LAST_UPDATE_INFO, null) ?: return null
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            UpdateInfo(
                latestVersion = obj.get("latestVersion")?.asString ?: return null,
                tagName = obj.get("tagName")?.asString ?: "",
                releaseUrl = obj.get("releaseUrl")?.asString ?: "",
                releaseNotes = "",
                publishDate = obj.get("publishDate")?.asString ?: "",
                apkAssets = emptyList(),
                hasUpdate = obj.get("hasUpdate")?.asBoolean ?: false,
                currentVersion = currentVersion,
                isIgnored = obj.get("latestVersion")?.asString == getIgnoredVersion()
            )
        } catch (_: Exception) { null }
    }
}
