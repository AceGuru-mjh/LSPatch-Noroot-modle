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
 * 【实验性】批量下�?Hook
 *
 * 实现思路�?
 *  - Hook 用户主页 / 合集页的视频列表加载方法
 *  - 视频列表加载完成后，提取所有视�?URL，异步批量下�?
 *  - 支持抖音/快手/小红书主页，B站收藏夹/UP主投�?
 *
 * 候选类名：
 *  - com.ss.android.ugc.aweme.profile.model.UserProfileResponse
 *  - com.kuaishou.android.profile.UserProfileResponse
 *  - com.xingin.xhs.profile.UserFeedResponse
 *  - tv.danmaku.bili.ui.video.up.UpArchiveListResponse
 *
 * 硬性限制（NoRoot 版）�?
 *  - �?Hook 应用进程�?Java 方法
 *  - 不修改系�?
 *  - 实验性，可能产生大量流量与存储占�?
 */
object BatchDownloadHook {

    private val LIST_CLASS_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.profile.model.UserProfileResponse",
        "com.ss.android.ugc.aweme.profile.model.AwemeListResponse",
        "com.kuaishou.android.profile.UserProfileResponse",
        "com.kuaishou.android.profile.FeedListResponse",
        "com.xingin.xhs.profile.UserFeedResponse",
        "com.xingin.xhs.feed.note.NoteListResponse",
        "tv.danmaku.bili.ui.video.up.UpArchiveListResponse",
        "tv.danmaku.bili.api.BiliApiResponse"
    )

    /** 视频列表方法名候�?*/
    private val LIST_METHOD_CANDIDATES = arrayOf(
        "getAwemeList", "getFeedList", "getItems", "getVideos",
        "getArchives", "getData", "getList", "getNoteList"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.batchDownloadEnabled) return
        LogX.i("【实验性】批量下�?Hook 启动")

        hookVideoListGetters(lpparam, cfg)
        hookListAppendMethod(lpparam, cfg)
    }

    /** Hook 视频 List �?getter，遍历触发批量下�?*/
    private fun hookVideoListGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in LIST_CLASS_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in LIST_METHOD_CANDIDATES) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val list = p.result as? List<*> ?: return
                                    if (list.isEmpty()) return
                                    LogX.d("检测到视频列表 size=${list.size}, 触发批量下载")
                                    processBatch(list, lpparam.packageName, cfg)
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook List.add 等追加方法，捕获流式加载的视�?*/
    private fun hookListAppendMethod(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val adapterCandidates = arrayOf(
            "com.ss.android.ugc.aweme.feed.adapter.FeedRecommendAdapter",
            "com.kuaishou.android.feed.adapter.FeedAdapter"
        )
        for (clsName in adapterCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    // 单参 add 方法
                    XposedHelpers.findAndHookMethod(cls, "add",
                        "java.lang.Object", object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val item = p.args.firstOrNull() ?: return
                                    val url = extractUrlFromItem(item) ?: return
                                    if (!isVideoUrl(url)) return
                                    LogX.d("Adapter.add 触发批量下载: $url")
                                    triggerDownload(url, "batch", cfg)
                                } catch (_: Throwable) { }
                            }
                        })
                    LogX.hookSuccess(clsName, "add(Object)")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 从视�?item 提取 URL（反射遍历常见字段） */
    private fun extractUrlFromItem(item: Any?): String? {
        if (item == null) return null
        val fields = arrayOf("videoUrl", "playUrl", "url", "downloadUrl", "mainMvUrl")
        for (fieldName in fields) {
            try {
                val f = item.javaClass.getDeclaredField(fieldName)
                f.isAccessible = true
                val v = f.get(item) as? String
                if (!v.isNullOrBlank()) return v
            } catch (_: Throwable) { }
        }
        return null
    }

    /** 遍历列表批量下载 */
    private fun processBatch(list: List<*>, packageName: String, cfg: VideoConfig) {
        val platform = when {
            packageName.contains("aweme") -> "douyin_batch"
            packageName.contains("gifmaker") || packageName.contains("kuaishou") -> "kuaishou_batch"
            packageName.contains("xhs") -> "xhs_batch"
            packageName.contains("bili") -> "bili_batch"
            else -> "batch"
        }
        // 限制最�?50 个，防止失控
        val limited = list.take(50)
        for (item in limited) {
            val url = extractUrlFromItem(item) ?: continue
            if (!isVideoUrl(url)) continue
            triggerDownload(url, platform, cfg)
        }
    }

    private fun isVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.startsWith("file://") || url.startsWith("content://")) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val adDomains = listOf("ad.toutiao", "pangolin-sdk", "googleads")
        if (adDomains.any { url.contains(it, ignoreCase = true) }) return false
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".m3u8") ||
               lower.contains(".flv") || lower.contains(".m4s") ||
               lower.contains("/video/") || lower.contains("/aweme/")
    }

    private fun triggerDownload(url: String, platform: String, cfg: VideoConfig) {
        if (url.isBlank()) return
        Thread {
            try {
                val cleaned = url.replace("playwm", "play")
                    .replace(Regex("&?watermark=[^&]*"), "")
                val conn = (URL(cleaned).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) VideoSaver/1.0")
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != 200) {
                    LogX.w("批量下载失败 HTTP ${conn.responseCode}")
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
                LogX.w("批量下载异常: ${e.message}")
            }
        }.start()
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
