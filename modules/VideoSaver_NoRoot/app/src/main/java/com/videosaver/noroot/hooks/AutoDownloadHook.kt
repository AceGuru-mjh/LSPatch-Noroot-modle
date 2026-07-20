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
 * 【实验性】自动下载 Hook
 *
 * 实现思路：
 *  - Hook 视频/直播播放器的 setDataSource / prepareAsync / start 方法
 *  - 当播放器加载视频时，自动触发异步下载保存
 *  - 通过当前播放 URL 提取并去水印
 *
 * 候选类名（多播放器适配）：
 *  - android.media.MediaPlayer
 *  - tv.danmaku.ijk.media.player.IjkMediaPlayer
 *  - com.google.android.exoplayer2.ExoPlayer
 *  - com.google.android.exoplayer2.MediaItem
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅 Hook 应用进程内 Java 方法
 *  - 不修改系统
 *  - 自动下载会增加流量消耗，默认关闭
 *
 * 注意：标记为实验性，部分 APP 可能误触发大量下载
 */
object AutoDownloadHook {

    private val PLAYER_CLASS_CANDIDATES = arrayOf(
        "android.media.MediaPlayer",
        "tv.danmaku.ijk.media.player.IjkMediaPlayer",
        "tv.danmaku.ijk.media.player.AbstractMediaPlayer",
        "com.google.android.exoplayer2.ExoPlayerImpl",
        "com.google.android.exoplayer2.SimpleExoPlayer"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.autoDownloadEnabled) return
        LogX.i("【实验性】自动下载 Hook 启动")

        hookMediaPlayerSetDataSource(lpparam, cfg)
        hookExoPlayerMediaItem(lpparam, cfg)
        hookIjkSetDataSource(lpparam, cfg)
    }

    /** Hook MediaPlayer.setDataSource(String) */
    private fun hookMediaPlayerSetDataSource(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.MediaPlayer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setDataSource",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val url = p.args.firstOrNull() as? String ?: return
                            if (!isVideoUrl(url)) return
                            LogX.d("MediaPlayer.setDataSource 触发自动下载: $url")
                            triggerAutoDownload(url, "auto", cfg)
                        }
                    })
                LogX.hookSuccess("MediaPlayer", "setDataSource(String)")
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    /** Hook ExoPlayer MediaItem.fromUri */
    private fun hookExoPlayerMediaItem(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val candidates = arrayOf(
            "com.google.android.exoplayer2.MediaItem",
            "androidx.media3.common.MediaItem"
        )
        for (clsName in candidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "fromUri",
                        "android.net.Uri", object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val uri = p.args.firstOrNull() ?: return
                                    val url = uri.toString()
                                    if (!isVideoUrl(url)) return
                                    LogX.d("ExoPlayer.fromUri 触发自动下载: $url")
                                    triggerAutoDownload(url, "auto", cfg)
                                } catch (_: Throwable) { }
                            }
                        })
                    LogX.hookSuccess(clsName, "fromUri")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** Hook IjkMediaPlayer.setDataSource */
    private fun hookIjkSetDataSource(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in PLAYER_CLASS_CANDIDATES) {
            if (clsName == "android.media.MediaPlayer") continue
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "setDataSource",
                        String::class.java, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                if (!isVideoUrl(url)) return
                                LogX.d("${cls.simpleName}.setDataSource 触发自动下载: $url")
                                triggerAutoDownload(url, "auto", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "setDataSource")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 判断 URL 是否为视频 */
    private fun isVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        // 排除本地文件
        if (url.startsWith("file://") || url.startsWith("content://")) return false
        // 排除广告域名
        val adDomains = listOf("ad.toutiao", "pangolin-sdk", "adview", "googleads")
        if (adDomains.any { url.contains(it, ignoreCase = true) }) return false
        // 排除非 http(s)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        // 判断扩展名或路径
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".m3u8") ||
               lower.contains(".flv") || lower.contains(".m4s") ||
               lower.contains("/video/") || lower.contains("/aweme/") ||
               lower.contains("/play/") || lower.contains("video")
    }

    /** 去水印并触发下载 */
    private fun triggerAutoDownload(url: String, platform: String, cfg: VideoConfig) {
        val cleaned = stripWatermarkGeneric(url)
        Thread {
            try {
                val conn = (URL(cleaned).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) VideoSaver/1.0")
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != 200) {
                    LogX.w("自动下载失败 HTTP ${conn.responseCode}")
                    conn.disconnect()
                    return@Thread
                }
                conn.inputStream.use { ins ->
                    VideoFileSaver.saveStream(
                        context = null,
                        input = ins,
                        platform = platform,
                        extension = guessExtension(cleaned),
                        customPath = cfg.customSavePath,
                        autoRename = cfg.autoRenameEnabled
                    )
                }
                conn.disconnect()
            } catch (e: Throwable) {
                LogX.w("自动下载异常: ${e.message}")
            }
        }.start()
    }

    private fun stripWatermarkGeneric(url: String): String {
        var u = url
        u = u.replace("playwm", "play")
        u = u.replace(Regex("&?watermark=[^&]*"), "")
        u = u.replace(Regex("&?wm=[^&]*"), "")
        u = u.replace("/watermark/", "/origin/")
        return u
    }

    private fun guessExtension(url: String): String {
        val lower = url.substringBefore("?").lowercase()
        return when {
            lower.endsWith(".mp4") -> "mp4"
            lower.endsWith(".m3u8") -> "m3u8"
            lower.endsWith(".flv") -> "flv"
            lower.endsWith(".m4s") -> "m4s"
            else -> "mp4"
        }
    }
}
