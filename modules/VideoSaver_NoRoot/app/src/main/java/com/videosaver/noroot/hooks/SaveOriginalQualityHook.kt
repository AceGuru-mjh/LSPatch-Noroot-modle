package com.videosaver.noroot.hooks

import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】强制原画质下载 Hook
 *
 * 实现思路�?
 *  - Hook 视频 URL 拼接方法，强制使用高码率/原画�?URL
 *  - Hook 画质获取方法（getQuality / getCurrentQuality），返回最高画质常�?
 *  - Hook 画质选择对话框，自动选最高画�?
 *
 * 候选类名：
 *  - com.ss.android.ugc.aweme.video.VideoQualityManager
 *  - com.ss.android.ugc.aweme.feed.model.VideoQualityInfo
 *  - tv.danmaku.bili.player.QualityHelper
 *  - com.google.android.exoplayer2.TrackSelectionParameters
 *
 * 硬性限制（NoRoot 版）�?
 *  - �?Hook 应用进程�?Java 方法
 *  - 不修改系�?
 *  - 实验性，部分 APP 可能导致播放失败
 */
object SaveOriginalQualityHook {

    private val QUALITY_MANAGER_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.video.VideoQualityManager",
        "com.ss.android.ugc.aweme.feed.model.VideoQualityInfo",
        "com.ss.android.ugc.aweme.feed.model.AwemeQualityInfo",
        "tv.danmaku.bili.player.QualityHelper",
        "com.bilibili.lib.download.QualityManager",
        "com.kuaishou.android.video.QualityManager"
    )

    /** TrackSelectionParameters（ExoPlayer�?*/
    private val EXO_TRACK_CANDIDATES = arrayOf(
        "com.google.android.exoplayer2.TrackSelectionParameters",
        "androidx.media3.common.TrackSelectionParameters"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.saveOriginalQualityEnabled) return
        LogX.i("【实验性】强制原画质 Hook 启动")

        hookQualityManager(lpparam)
        hookExoTrackSelection(lpparam)
        hookUrlBuilder(lpparam)
    }

    /** Hook 画质管理类，强制返回最高画�?*/
    private fun hookQualityManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in QUALITY_MANAGER_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // getCurrentQuality -> 返回最大�?
                for (methodName in arrayOf("getCurrentQuality", "getQuality", "getCurrentQN", "getPlayQuality")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    // 返回值可能是 int / enum
                                    val result = p.result
                                    if (result is Int) {
                                        // 大多�?SDK �?int 值越大画质越�?
                                        if (result < 100) {
                                            p.result = 1000  // 强制超高画质
                                            LogX.d("画质强制提升: $result -> 1000 (${clsName.substringAfterLast('.')}.$methodName)")
                                        }
                                    }
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
                // getMaxQuality -> 返回最大�?
                for (methodName in arrayOf("getMaxQuality", "getMaxQN", "getHighestQuality")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    p.result = 1000
                                    LogX.d("最高画质返�?1000 (${clsName.substringAfterLast('.')}.$methodName)")
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
                // setQuality(int) -> 强制设置为最�?
                for (methodName in arrayOf("setQuality", "setPlayQuality", "setCurrentQuality")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            Int::class.javaPrimitiveType, object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    try {
                                        p.args[0] = 1000
                                        LogX.d("画质设置已强�? 1000 (${clsName.substringAfterLast('.')}.$methodName)")
                                    } catch (_: Throwable) { }
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook ExoPlayer TrackSelectionParameters，强制高码率 */
    private fun hookExoTrackSelection(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in EXO_TRACK_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // getMaxVideoBitrate -> 返回 Int.MAX_VALUE
                try {
                    XposedHelpers.findAndHookMethod(cls, "getMaxVideoBitrate", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            p.result = Int.MAX_VALUE
                            LogX.d("ExoPlayer 视频码率上限已解�?)
                        }
                    })
                    LogX.hookSuccess(clsName, "getMaxVideoBitrate")
                } catch (_: Throwable) { }
                // getMaxVideoSize -> 返回超大尺寸
                try {
                    val sizeCls = XposedHelpers.findClassIfExists(
                        "com.google.android.exoplayer2.VideoSize", lpparam.classLoader)
                    XposedHelpers.findAndHookMethod(cls, "getMaxVideoSize", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            // 不修改（VideoSize 构造复杂，避免崩溃�?
                            LogX.d("ExoPlayer 视频尺寸上限查询触发")
                        }
                    })
                    LogX.hookSuccess(clsName, "getMaxVideoSize")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** Hook URL 拼接方法，强制使用原画质 URL（去�?_720p / _540p 等参数） */
    private fun hookUrlBuilder(lpparam: XC_LoadPackage.LoadPackageParam) {
        val urlBuilderCandidates = arrayOf(
            "com.ss.android.ugc.aweme.video.VideoUrlBuilder",
            "com.bytedance.frameworks.baselibnetwork.http.cdn.URLBuilder"
        )
        for (clsName in urlBuilderCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "build", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val result = p.result as? String ?: return
                                // 去除分辨率参数（�?_720p, _540p�?
                                val cleaned = result
                                    .replace(Regex("_\\d+p"), "")
                                    .replace(Regex("&?resolution=[^&]*"), "")
                                    .replace(Regex("&?quality=[^&]*"), "")
                                if (cleaned != result) {
                                    p.result = cleaned
                                    LogX.d("URL 已强制原画质: $result -> $cleaned")
                                }
                            } catch (_: Throwable) { }
                        }
                    })
                    LogX.hookSuccess(clsName, "build")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }
}
