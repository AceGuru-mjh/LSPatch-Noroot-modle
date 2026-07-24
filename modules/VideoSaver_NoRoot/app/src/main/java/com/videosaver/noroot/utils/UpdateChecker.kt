package com.videosaver.noroot.utils

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO = "AceGuru-mjh/LSPatch-Noroot-modle"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_IGNORED_VERSION = "ignored_version"
    private const val KEY_AUTO_CHECK = "auto_check"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_LAST_UPDATE_INFO = "last_update_info"
    private const val MIN_CHECK_INTERVAL_MS = 5 * 60 * 1000L

    private val API_MIRRORS = listOf(
        "https://api.github.com/repos/$REPO/releases/latest",
        "https://ghproxy.com/https://api.github.com/repos/$REPO/releases/latest",
        "https://mirror.ghproxy.com/https://api.github.com/repos/$REPO/releases/latest",
        "https://gh.con.sh/https://api.github.com/repos/$REPO/releases/latest",
        "https://api.github.do/https://api.github.com/repos/$REPO/releases/latest"
    )

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

    fun init(context: Context) {
        cachedContext = context.applicationContext
    }

    private fun prefs() = cachedContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun checkUpdate(currentVersion: String, force: Boolean = false): UpdateInfo? {
        if (!force) {
            val last = prefs()?.getLong(KEY_LAST_CHECK_TIME, 0L) ?: 0L
            if (System.currentTimeMillis() - last < MIN_CHECK_INTERVAL_MS) {
                Log.d(TAG, "距上次检查不足5分钟，返回缓存")
                return cachedInfo ?: loadCachedInfo(currentVersion)
            }
        }
        val result = fetchWithMirrors(currentVersion)
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

    private fun fetchWithMirrors(currentVersion: String): UpdateInfo? {
        for ((idx, url) in API_MIRRORS.withIndex()) {
            val isDirect = idx == 0
            Log.d(TAG, "尝试源 ${idx + 1}/${API_MIRRORS.size}${if (isDirect) " (直连)" else " (镜像)"}")
            val result = tryFetch(url, currentVersion, timeout = if (isDirect) 8000 else 12000)
            if (result != null) {
                Log.i(TAG, "源 ${idx + 1} 成功${if (!isDirect) " (镜像)" else ""}")
                return result
            }
            Log.w(TAG, "源 ${idx + 1} 失败，尝试下一个...")
        }
        Log.e(TAG, "所有更新源均不可达")
        return null
    }

    private fun tryFetch(urlStr: String, currentVersion: String, timeout: Int): UpdateInfo? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeout
                readTimeout = timeout + 5000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LSP-Model-UpdateChecker")
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "HTTP ${conn.responseCode}: $urlStr")
                conn.disconnect()
                return null
            }
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
            conn.disconnect()
            parseRelease(raw, currentVersion)
        } catch (e: java.net.SocketTimeoutException) {
            null
        } catch (e: java.net.ConnectException) {
            null
        } catch (e: java.io.IOException) {
            null
        } catch (e: Exception) {
            Log.e(TAG, "异常: ${e.message}")
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
            Log.e(TAG, "解析失败: ${e.message}")
            null
        }
    }

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

    fun findMatchingApk(info: UpdateInfo, moduleName: String): ApkAsset? {
        return info.apkAssets.firstOrNull { it.name.startsWith(moduleName, ignoreCase = true) }
            ?: info.apkAssets.firstOrNull()
    }

    fun getIgnoredVersion(): String? = prefs()?.getString(KEY_IGNORED_VERSION, null)
    fun ignoreVersion(version: String) {
        prefs()?.edit()?.putString(KEY_IGNORED_VERSION, version)?.apply()
    }
    fun clearIgnored() {
        prefs()?.edit()?.remove(KEY_IGNORED_VERSION)?.apply()
    }

    fun isAutoCheckEnabled(): Boolean = prefs()?.getBoolean(KEY_AUTO_CHECK, true) ?: true
    fun setAutoCheck(enabled: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_AUTO_CHECK, enabled)?.apply()
    }

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

    fun getDownloadCacheSize(context: Context): Long {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun serializeInfo(info: UpdateInfo): String {
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
