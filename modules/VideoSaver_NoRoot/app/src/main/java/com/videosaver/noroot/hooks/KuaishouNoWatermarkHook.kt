package com.videosaver.noroot.hooks

import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.LogX
import com.videosaver.noroot.utils.VideoFileSaver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.HttpURLConnection
import java.net.URL

/**
 * 快手无水印下载 Hook（仅应用进程内）
 *
 * 候选类名：
 *  - com.kuaishou.android.model.feed.VideoFeedInfo
 *  - com.yxcorp.gifshow.feed.model.FeedInfo
 *  - com.yxcorp.gifshow.video.KSVideoPlayerItem
 *
 * 候选方法：
 *  - getVideoUrl / getPlayUrl / getCoverUrl / getShareUrl
 *
 * 实现思路：
 *  - Hook 视频 URL getter，去除 photoId 上的水印后缀
 *  - Hook 视频下载入口方法，触发异步下载保存
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅 Hook 应用进程内 Java 方法
 *  - 不调用系统级 API
 */
object KuaishouNoWatermarkHook {

    private val FEED_CLASS_CANDIDATES = arrayOf(
        "com.kuaishou.android.model.feed.VideoFeedInfo",
        "com.kuaishou.android.model.feed.PhotoModel",
        "com.yxcorp.gifshow.feed.model.FeedInfo",
        "com.yxcorp.gifshow.video.KSVideoPlayerItem",
        "com.yxcorp.gifshow.entity.QPhoto"
    )

    private val URL_METHOD_CANDIDATES = arrayOf(
        "getVideoUrl", "getPlayUrl", "getCoverUrl", "getShareUrl",
        "getUrl", "getMainMvUrl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.kuaishouNoWatermark) return
        LogX.i("快手无水印下载 Hook 启动")

        hookFeedUrlGetters(lpparam, cfg)
        hookDownloadEntry(lpparam, cfg)
    }

    private fun hookFeedUrlGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in FEED_CLASS_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in URL_METHOD_CANDIDATES) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val raw = p.result as? String ?: return
                                    val cleaned = stripWatermark(raw)
                                    if (cleaned != raw) {
                                        p.result = cleaned
                                        LogX.d("快手 URL 去水印: $raw -> $cleaned")
                                    }
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookDownloadEntry(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val downloadEntryCandidates = arrayOf(
            "com.yxcorp.gifshow.download.DownloadManager",
            "com.yxcorp.gifshow.util.DownloadUtils",
            "com.kuaishou.android.download.VideoDownloadHelper"
        )
        for (clsName in downloadEntryCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "download",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("快手 download 参数已替换为无水印 URL")
                                }
                                triggerDownload(cleaned, "kuaishou", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "download")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "startDownload",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("快手 startDownload 参数已替换")
                                }
                                triggerDownload(cleaned, "kuaishou", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "startDownload")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 快手 URL 水印参数移除 */
    private fun stripWatermark(url: String): String {
        if (url.isBlank()) return url
        var u = url
        // 快手带水印 URL 通常含 /watermark/ 路径段
        u = u.replace("/watermark/", "/origin/")
        // 移除 watermark 参数
        u = u.replace(Regex("&?watermark=[^&]*"), "")
        u = u.replace(Regex("&?wm=[^&]*"), "")
        u = u.replace(Regex("&?nw=[^&]*"), "")
        u = u.replace(Regex("[?&]$"), "")
        u = u.replace("?&", "?")
        return u
    }

    private fun triggerDownload(url: String, platform: String, cfg: VideoConfig) {
        if (url.isBlank()) return
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) VideoSaver/1.0")
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != 200) {
                    LogX.w("快手视频下载失败 HTTP ${conn.responseCode}")
                    conn.disconnect()
                    return@Thread
                }
                conn.inputStream.use { ins ->
                    VideoFileSaver.saveStream(
                        context = null,
                        input = ins,
                        platform = platform,
                        extension = guessExtension(url),
                        customPath = cfg.customSavePath,
                        autoRename = cfg.autoRenameEnabled
                    )
                }
                conn.disconnect()
            } catch (e: Throwable) {
                LogX.w("快手视频下载异常: ${e.message}")
            }
        }.start()
    }

    private fun guessExtension(url: String): String {
        val lower = url.substringBefore("?").lowercase()
        return when {
            lower.endsWith(".mp4") -> "mp4"
            lower.endsWith(".m3u8") -> "m3u8"
            lower.endsWith(".webm") -> "webm"
            else -> "mp4"
        }
    }
}
