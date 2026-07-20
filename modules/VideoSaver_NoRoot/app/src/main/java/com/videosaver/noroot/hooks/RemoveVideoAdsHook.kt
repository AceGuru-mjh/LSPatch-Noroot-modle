package com.videosaver.noroot.hooks

import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】去视频广告 Hook（应用层）
 *
 * 实现思路：
 *  - Hook 视频 SDK 的广告加载方法，阻断广告 URL 拉取
 *  - Hook 广告 View 显示方法，hide 广告 View
 *  - Hook 广告请求 URL，返回空响应
 *
 * 候选类名（多平台广告 SDK）：
 *  - com.bytedance.sdk.openadsdk.AdSlot
 *  - com.qq.e.ads.* （腾讯优量汇）
 *  - com.baidu.mobads.* （百度广告）
 *  - com.kwad.sdk.* （快手广告）
 *  - com.ss.android.downloadlib.*
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅 Hook 应用进程内 Java 方法
 *  - 不修改 hosts 文件（需 Root 版 GlobalVideoAdBlockHook）
 *  - 实验性，部分 APP 可能误屏蔽正常视频
 */
object RemoveVideoAdsHook {

    /** 字节广告 SDK 候选 */
    private val BYTEDANCE_AD_CANDIDATES = arrayOf(
        "com.bytedance.sdk.openadsdk.AdSlot",
        "com.bytedance.sdk.openadsdk.TTAdManager",
        "com.bytedance.sdk.openadsdk.core.AdManager",
        "com.ss.android.ad.landing.LandingPageHelper",
        "com.ss.android.downloadlib.DownloadService"
    )

    /** 快手广告 SDK 候选 */
    private val KUAISHOU_AD_CANDIDATES = arrayOf(
        "com.kwad.sdk.api.KsAdSDK",
        "com.kwad.sdk.core.admodel.AdInfo",
        "com.kwad.components.ad.interstitial.InterstitialAd"
    )

    /** 腾讯广告 SDK 候选 */
    private val TENCENT_AD_CANDIDATES = arrayOf(
        "com.qq.e.ads.InterstitialAD",
        "com.qq.e.comm.adevent.ADListener",
        "com.qq.e.ads.cfg.AdRequest"
    )

    /** 通用广告 View 候选 */
    private val AD_VIEW_CANDIDATES = arrayOf(
        "com.bytedance.sdk.openadsdk.widget.TTRewardVideoAd",
        "com.kwad.components.ad.interstitial.InterstitialAdView",
        "com.qq.e.ads.InterstitialADView"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.removeAdsEnabled) return
        LogX.i("【实验性】去视频广告 Hook 启动")

        hookBytedanceAd(lpparam)
        hookKuaishouAd(lpparam)
        hookTencentAd(lpparam)
        hookAdViewShow(lpparam)
        hookAdUrlRequest(lpparam)
    }

    /** Hook 字节广告 SDK */
    private fun hookBytedanceAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in BYTEDANCE_AD_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // 拦截广告加载方法
                for (methodName in arrayOf("loadAd", "loadFeedAd", "loadRewardVideoAd", "loadInterstitialAd")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("字节广告方法已阻断: $methodName")
                                    // 不调用原始方法，直接返回
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook 快手广告 SDK */
    private fun hookKuaishouAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in KUAISHOU_AD_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("loadAd", "loadInterstitialAd", "loadRewardVideoAd")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("快手广告方法已阻断: $methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook 腾讯广告 SDK */
    private fun hookTencentAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in TENCENT_AD_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("loadAd", "loadInterstitial")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("腾讯广告方法已阻断: $methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook 广告 View 显示方法，强制隐藏 */
    private fun hookAdViewShow(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in AD_VIEW_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("show", "showAd", "showInterstitial", "showRewardVideo")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("广告 View 显示已阻断: ${clsName.substringAfterLast('.')}.$methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    /** Hook URL 请求方法，过滤广告域名 */
    private fun hookAdUrlRequest(lpparam: XC_LoadPackage.LoadPackageParam) {
        val urlRequestClassCandidates = arrayOf(
            "java.net.URL",
            "okhttp3.Request\$Builder",
            "com.android.okhttp.internal.http.HttpEngine"
        )
        // 仅 Hook URL.openConnection(String) — 这个方法签名不存在
        // 实际通过 URLConnection.openConnection() 间接实现
        try {
            val urlCls = XposedHelpers.findClassIfExists("java.net.URL", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(urlCls, "openConnection",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val urlObj = p.thisObject
                                val field = urlObj.javaClass.getDeclaredField("host")
                                field.isAccessible = true
                                val host = field.get(urlObj) as? String ?: return
                                if (isAdDomain(host)) {
                                    LogX.d("广告域名请求已阻断: $host")
                                    // 抛异常阻断请求
                                    p.result = null
                                }
                            } catch (_: Throwable) { }
                        }
                    })
                LogX.hookSuccess("URL", "openConnection")
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    /** 常见广告域名判断 */
    private val AD_DOMAINS = listOf(
        "pangolin-sdk-toutiao", "ad.toutiao.com", "is.snssdk.com",
        "ad.toutiao.com", "dsp.toutiao.com", "pglstatp-toutiao.com",
        "ad.toutiaocloud.com", "adsame.cn", "googleads.g.doubleclick.net",
        "ad.toutiao.com", "cm.adkmob.com", "ad-cn.joydog.com",
        "i.l.inmobicdn.cn", "sdk.e.qq.com"
    )

    private fun isAdDomain(host: String): Boolean {
        if (host.isBlank()) return false
        return AD_DOMAINS.any { host.contains(it, ignoreCase = true) }
    }
}
