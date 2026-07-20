package com.adblockerx.noroot.hooks

import android.view.View
import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 广告 SDK View 隐藏 Hook
 *
 * 拦截策略：
 *  - 反射查找常见广告 SDK 的广告 View 类（GDT/百度/字节/小米/AdMob 等）
 *  - Hook 构造方法（构造完成后立即 setVisibility GONE）
 *  - Hook setVisibility 方法：当尝试设为 VISIBLE 时强制改为 GONE
 *  - 类不存在则跳过，绝不抛出异常
 */
object AdViewHideHook {

    private val AD_VIEW_CLASS_CANDIDATES = listOf(
        // 腾讯 GDT / AMS
        "com.qq.e.ads.nativ.NativeExpressADView",
        "com.qq.e.ads.banner.BannerView",
        "com.qq.e.ads.interstitial.InterstitialAD",
        "com.qq.e.comm.plugin.splash.SplashAdView",
        "com.tencent.gdtad.api.AdView",
        "com.tencent.mobileqq.splashad.SplashADView",

        // 百度联盟
        "com.baidu.mobads.api.BaiduAdView",
        "com.baidu.mobads.banner.BannerAdView",
        "com.baidu.mobads.interstitial.InterstitialAd",

        // 字节穿山甲
        "com.bytedance.sdk.openadsdk.adapter.view.TTNativeAdView",
        "com.bytedance.sdk.openadsdk.core.widget.SplashAdView",
        "com.bytedance.sdk.openadsdk.core.widget.BannerAdView",
        "com.bytedance.sdk.openadsdk.core.widget.RewardVideoAd",
        "com.bykv.vk.openvk.adapter.view.TTNativeAdView",

        // AdMob / Google Mobile Ads
        "com.google.android.gms.ads.AdView",
        "com.google.android.gms.ads.InterstitialAd",
        "com.google.android.gms.ads.formats.NativeAdView",

        // 快手
        "com.kwad.sdk.api.KsAdView",
        "com.kwad.sdk.content.KsContentAdView",

        // 通用候选名
        "com.admobile.sdk.AdView",
        "com.anythink.sdk.AdView"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.adViewHideEnabled) return
        LogX.i("AdViewHideHook 启动（应用进程内，多候选类名容错）")

        var hooked = 0
        for (className in AD_VIEW_CLASS_CANDIDATES) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            if (hookConstructor(clazz)) hooked++
            if (hookSetVisibility(clazz)) hooked++
        }
        LogX.i("AdViewHideHook 完成：命中 ${hooked} 个广告 View 类")
    }

    private fun hookConstructor(clazz: Class<*>): Boolean {
        return try {
            val constructors = clazz.declaredConstructors
            for (c in constructors) {
                try {
                    XposedBridge.hookMethod(c, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val view = p.thisObject as? View ?: return
                            try {
                                view.visibility = View.GONE
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                    LogX.d("[AdViewHide] Hook 构造: ${clazz.name}")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
            constructors.isNotEmpty()
        } catch (e: Throwable) {
            LogX.d("[AdViewHide] Hook 构造异常: ${clazz.name} - ${e.message}")
            false
        }
    }

    private fun hookSetVisibility(clazz: Class<*>): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(clazz, "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val v = p.args.getOrNull(0) as? Int ?: return
                        if (v == View.VISIBLE) {
                            p.args[0] = View.GONE
                            LogX.d("[AdViewHide] 强制 GONE: ${clazz.name}")
                        }
                    }
                })
            true
        } catch (_: Throwable) {
            false
        }
    }
}
