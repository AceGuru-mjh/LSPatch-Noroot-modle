package com.videosaver.noroot.hooks

import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.LogStore
import com.videosaver.noroot.utils.LogX
import com.videosaver.noroot.utils.VideoFileSaver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 抖音无水印下载 Hook（仅应用进程内）
 *
 * 实现思路：
 *  - 抖音 / 抖音极速版的视频信息类（多候选名容错）持有 video_url / play_addr / download_addr 字段
 *  - Hook 这些字段的 getter，去除 URL 上的水印参数（playwm -> play，去除 &watermark=）
 *  - 同时 Hook 视频下载入口方法，将无水印 URL 写入文件
 *
 * 候选类名：
 *  - com.ss.android.ugc.aweme.feed.model.Aweme
 *  - com.ss.android.ugc.aweme.feed.model.VideoModel
 *  - com.ss.android.ugc.aweme.shortvideo.AwemeCreateModel
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅 Hook 应用进程内 Java 方法
 *  - 不调用系统级 API、不修改系统属性
 *  - 所有 Hook 失败静默 catch，不影响宿主正常使用
 */
object DouyinNoWatermarkHook {

    /** 抖音视频信息类候选 */
    private val AWEME_CLASS_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.feed.model.Aweme",
        "com.ss.android.ugc.aweme.feed.model.AwemeStruct",
        "com.ss.android.ugc.aweme.feed.model.VideoModel",
        "com.ss.android.ugc.aweme.shortvideo.AwemeCreateModel",
        "com.ss.android.ugc.aweme.model.VideoModel"
    )

    /** 视频下载/分享方法候选 */
    private val DOWNLOAD_METHOD_CANDIDATES = arrayOf(
        "getDownloadUrl", "getVideoUrl", "getPlayUrl", "getDownloadAddr",
        "getPlayAddr", "getShareUrl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.douyinNoWatermark) return
        LogX.i("抖音无水印下载 Hook 启动")
        try { LogStore.add("saved", "无水印下载已启用") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookAwemeUrlGetters(lpparam, cfg)
        hookVideoDownloadEntry(lpparam, cfg)
    }

    /** Hook 视频信息类的 URL getter，去除水印参数 */
    private fun hookAwemeUrlGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in AWEME_CLASS_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in DOWNLOAD_METHOD_CANDIDATES) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val raw = p.result as? String ?: return
                                    val cleaned = stripWatermark(raw)
                                    if (cleaned != raw) {
                                        p.result = cleaned
                                        LogX.d("抖音 URL 去水印: $raw -> $cleaned")
                                    }
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { /* 方法不存在，跳过 */ }
                }
            } catch (_: Throwable) { /* 类不存在，跳过 */ }
        }
    }

    /** Hook 抖音下载/分享方法触发实际保存（异步下载无水印视频） */
    private fun hookVideoDownloadEntry(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val downloadEntryCandidates = arrayOf(
            "com.ss.android.ugc.aweme.feed.ui.FeedRecommendFragment",
            "com.ss.android.ugc.aweme.share.SharePackage",
            "com.ss.android.ugc.aweme.services.video.IDownloadService"
        )
        for (clsName in downloadEntryCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // Hook 所有单参 String 方法（保存路径回调）
                try {
                    XposedHelpers.findAndHookMethod(cls, "downloadVideo",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("抖音 downloadVideo 参数已替换为无水印 URL")
                                }
                                triggerDownload(cleaned, "douyin", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "downloadVideo")
                } catch (_: Throwable) { }
                // Hook shareImpl(String) 方法
                try {
                    XposedHelpers.findAndHookMethod(cls, "shareImpl",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("抖音 shareImpl 参数已替换")
                                }
                            }
                        })
                    LogX.hookSuccess(clsName, "shareImpl")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 去除 URL 上的水印参数（playwm -> play, 删除 &watermark=、&ttwatermark= 等） */
    private fun stripWatermark(url: String): String {
        if (url.isBlank()) return url
        var u = url
        // playwm 是带水印的播放地址，play 是无水印
        u = u.replace("playwm", "play")
        // 移除常见水印参数
        u = u.replace(Regex("&?watermark=[^&]*"), "")
        u = u.replace(Regex("&?ttwatermark=[^&]*"), "")
        u = u.replace(Regex("&?wm=[^&]*"), "")
        u = u.replace(Regex("&?enable_watermark=[^&]*"), "")
        // 清理孤立的 & ?
        u = u.replace(Regex("[?&]$"), "")
        u = u.replace("?&", "?")
        return u
    }

    /** 异步下载无水印视频到本地 */
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
                    LogX.w("抖音视频下载失败 HTTP ${conn.responseCode}")
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
                LogX.w("抖音视频下载异常: ${e.message}")
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

    /** 测试入口：传入字节数组直接保存 */
    fun saveBytesDirect(bytes: ByteArray, platform: String, cfg: VideoConfig) {
        VideoFileSaver.saveBytes(
            context = null,
            bytes = bytes,
            platform = platform,
            extension = "mp4",
            customPath = cfg.customSavePath,
            autoRename = cfg.autoRenameEnabled
        )
    }

    /** 字节流辅助（用于测试或外部调用） */
    fun toInputStream(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)
}
