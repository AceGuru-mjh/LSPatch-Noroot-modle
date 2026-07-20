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
 * 小红书无水印下载 Hook（仅应用进程内）
 *
 * 候选类名：
 *  - com.xingin.xhs.model.Note
 *  - com.xingin.xhs.video.VideoInfo
 *  - com.xingin.xhs.media.MediaItem
 *  - com.xingin.xhs.feed.note.NoteInfo
 *
 * 候选方法：
 *  - getVideoUrl / getOriginVideoUrl / getImageUrl / getCoverUrl
 *
 * 实现思路：
 *  - Hook 视频/图片 URL getter，去除 URL 末尾的水印参数（?imageView2/.../watermark）
 *  - Hook 保存按钮触发的方法，捕获原图/原视频 URL 并保存
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅 Hook 应用进程内 Java 方法
 *  - 不修改系统
 */
object XhsNoWatermarkHook {

    private val NOTE_CLASS_CANDIDATES = arrayOf(
        "com.xingin.xhs.model.Note",
        "com.xingin.xhs.video.VideoInfo",
        "com.xingin.xhs.media.MediaItem",
        "com.xingin.xhs.feed.note.NoteInfo",
        "com.xingin.xhs.note.model.NoteInfoModel"
    )

    private val URL_METHOD_CANDIDATES = arrayOf(
        "getVideoUrl", "getOriginVideoUrl", "getImageUrl", "getCoverUrl",
        "getUrl", "getOriginUrl", "getShareUrl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.xhsNoWatermark) return
        LogX.i("小红书无水印下载 Hook 启动")

        hookNoteUrlGetters(lpparam, cfg)
        hookSaveButton(lpparam, cfg)
    }

    private fun hookNoteUrlGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in NOTE_CLASS_CANDIDATES) {
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
                                        LogX.d("小红书 URL 去水印: $raw -> $cleaned")
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

    /** Hook 小红书保存按钮，触发原图/原视频下载 */
    private fun hookSaveButton(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val saveEntryCandidates = arrayOf(
            "com.xingin.xhs.editor.NoteSaveHelper",
            "com.xingin.xhs.share.NoteShareHelper",
            "com.xingin.xhs.download.NoteDownloadManager"
        )
        for (clsName in saveEntryCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "saveMedia",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("小红书 saveMedia 参数已替换为无水印 URL")
                                }
                                triggerDownload(cleaned, "xhs", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "saveMedia")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "download",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("小红书 download 参数已替换")
                                }
                                triggerDownload(cleaned, "xhs", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "download")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 小红书 URL 水印参数移除 */
    private fun stripWatermark(url: String): String {
        if (url.isBlank()) return url
        var u = url
        // 小红书图片 URL 通常带 ?imageView2/2/w/1080/format/webp 等参数
        // 视频通常带 ?cos=...&wm=...
        u = u.replace(Regex("&?wm=[^&]*"), "")
        u = u.replace(Regex("&?watermark=[^&]*"), "")
        u = u.replace("/watermark/", "/origin/")
        // 移除图片缩放参数，恢复原图
        u = u.replace(Regex("\\?imageView2/[^?]*"), "")
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
                    LogX.w("小红书媒体下载失败 HTTP ${conn.responseCode}")
                    conn.disconnect()
                    return@Thread
                }
                val ext = guessExtension(url, conn.contentType)
                conn.inputStream.use { ins ->
                    VideoFileSaver.saveStream(
                        context = null,
                        input = ins,
                        platform = platform,
                        extension = ext,
                        customPath = cfg.customSavePath,
                        autoRename = cfg.autoRenameEnabled
                    )
                }
                conn.disconnect()
            } catch (e: Throwable) {
                LogX.w("小红书媒体下载异常: ${e.message}")
            }
        }.start()
    }

    private fun guessExtension(url: String, contentType: String? = null): String {
        val lower = url.substringBefore("?").lowercase()
        return when {
            lower.endsWith(".mp4") -> "mp4"
            lower.endsWith(".m3u8") -> "m3u8"
            lower.endsWith(".webm") -> "webm"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "jpg"
            lower.endsWith(".png") -> "png"
            lower.endsWith(".webp") -> "webp"
            contentType?.contains("image/jpeg") == true -> "jpg"
            contentType?.contains("image/png") == true -> "png"
            contentType?.contains("image/webp") == true -> "webp"
            else -> "mp4"
        }
    }
}
