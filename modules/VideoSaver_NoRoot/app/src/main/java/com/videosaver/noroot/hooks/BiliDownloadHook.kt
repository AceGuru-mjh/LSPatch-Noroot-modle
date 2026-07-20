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
 * B站视频下载解锁 Hook（仅应用进程内）
 *
 * 实现思路：
 *  - Hook B站视频下载方法（downloadVideo / download 等），解锁客户端限制
 *  - Hook 视频信息类的 URL getter，返回原画质 URL
 *  - 触发异步下载保存
 *
 * 候选类名：
 *  - tv.danmaku.bili.download.VideoDownloadHelper
 *  - tv.danmaku.bili.ui.video.download.DownloadService
 *  - com.bilibili.lib.download.VideoDownloadManager
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅 Hook 应用进程内 Java 方法
 *  - 不修改系统
 */
object BiliDownloadHook {

    private val DOWNLOAD_CLASS_CANDIDATES = arrayOf(
        "tv.danmaku.bili.download.VideoDownloadHelper",
        "tv.danmaku.bili.ui.video.download.DownloadService",
        "com.bilibili.lib.download.VideoDownloadManager",
        "tv.danmaku.bili.ui.video.download.VideoDownloadActivity",
        "tv.danmaku.bili.player.video.DownloadHelper"
    )

    private val URL_GETTER_CANDIDATES = arrayOf(
        "getPlayUrl", "getDownloadUrl", "getOriginalUrl", "getDurl",
        "getDashUrl", "getVideoUrl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.biliDownload) return
        LogX.i("B站视频下载解锁 Hook 启动")

        hookDownloadEntry(lpparam, cfg)
        hookUrlGetters(lpparam, cfg)
        hookQualityUnlock(lpparam, cfg)
    }

    /** Hook 下载入口方法 */
    private fun hookDownloadEntry(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in DOWNLOAD_CLASS_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // 单 String 参方法（URL）
                try {
                    XposedHelpers.findAndHookMethod(cls, "download",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                LogX.d("B站 download 触发: $url")
                                triggerDownload(url, "bili", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "download")
                } catch (_: Throwable) { }
                // 双参方法（avid + cid）
                try {
                    XposedHelpers.findAndHookMethod(cls, "downloadVideo",
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val avid = p.args.getOrNull(0) as? Int ?: return
                                val cid = p.args.getOrNull(1) as? Int ?: return
                                LogX.d("B站 downloadVideo avid=$avid cid=$cid")
                                // 通过 API 拿真实播放地址
                                fetchPlayUrlAndDownload(avid, cid, cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "downloadVideo(avid,cid)")
                } catch (_: Throwable) { }
                // String 参方法（URL）
                try {
                    XposedHelpers.findAndHookMethod(cls, "startDownload",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                LogX.d("B站 startDownload 触发: $url")
                                triggerDownload(url, "bili", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "startDownload")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** Hook URL getter，返回原画质 URL */
    private fun hookUrlGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val urlHolderCandidates = arrayOf(
            "tv.danmaku.bili.player.video.PlayUrl",
            "com.bilibili.lib.download.model.VideoUrl",
            "tv.danmaku.bili.ui.video.VideoInfo"
        )
        for (clsName in urlHolderCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in URL_GETTER_CANDIDATES) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val raw = p.result as? String ?: return
                                    LogX.d("B站 $methodName 返回: $raw")
                                    // 不修改 URL，仅触发下载（保护体验）
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook 画质限制方法，强制返回高画质可用 */
    private fun hookQualityUnlock(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val qualityHolderCandidates = arrayOf(
            "tv.danmaku.bili.player.QualityHelper",
            "com.bilibili.lib.download.QualityManager"
        )
        for (clsName in qualityHolderCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "isQualityAvailable",
                        Int::class.javaPrimitiveType, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                // 强制返回 true，解锁高画质
                                p.result = true
                                LogX.d("B站画质解锁: qn=${p.args.firstOrNull()}")
                            }
                        })
                    LogX.hookSuccess(clsName, "isQualityAvailable")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "getMaxQuality", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            // 返回最高画质 127 = 8K 超高清
                            p.result = 127
                            LogX.d("B站画质上限解锁到 127")
                        }
                    })
                    LogX.hookSuccess(clsName, "getMaxQuality")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 通过 B站 API 获取播放地址并下载 */
    private fun fetchPlayUrlAndDownload(avid: Int, cid: Int, cfg: VideoConfig) {
        if (avid <= 0 || cid <= 0) return
        Thread {
            try {
                val apiUrl = "https://api.bilibili.com/x/player/playurl?avid=$avid&cid=$cid&qn=80&fnval=16"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) VideoSaver/1.0")
                }
                if (conn.responseCode != 200) {
                    LogX.w("B站 playurl API 失败 HTTP ${conn.responseCode}")
                    conn.disconnect()
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                // 简单解析 durl[0].url
                val urlRegex = Regex("\"url\"\\s*:\\s*\"(https?:[^\"]+)\"")
                val firstUrl = urlRegex.find(body)?.groupValues?.getOrNull(1)
                if (firstUrl.isNullOrBlank()) {
                    LogX.w("B站 playurl 未找到 url 字段")
                    return@Thread
                }
                val cleaned = firstUrl.replace("\\u002F", "/")
                triggerDownload(cleaned, "bili", cfg)
            } catch (e: Throwable) {
                LogX.w("B站 playurl 获取异常: ${e.message}")
            }
        }.start()
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
                    setRequestProperty("Referer", "https://www.bilibili.com/")
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != 200) {
                    LogX.w("B站视频下载失败 HTTP ${conn.responseCode}")
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
                LogX.w("B站视频下载异常: ${e.message}")
            }
        }.start()
    }

    private fun guessExtension(url: String): String {
        val lower = url.substringBefore("?").lowercase()
        return when {
            lower.endsWith(".mp4") -> "mp4"
            lower.endsWith(".flv") -> "flv"
            lower.endsWith(".m4s") -> "m4s"
            lower.endsWith(".m3u8") -> "m3u8"
            else -> "mp4"
        }
    }
}
