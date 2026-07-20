package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】通用去广告 Hook
 *
 * 目标：Hook 主流广告 SDK 让广告加载失败/不展示。
 *
 * 候选 SDK：
 *  1. 穿山甲（CSJ）：com.bytedance.sdk.openadsdk.*
 *  2. 优量汇（GDT/腾讯）：com.qq.e.ads.*
 *  3. 百度（Baidu）：com.baidu.mobads.*
 *  4. 快手（Ks）：com.kwad.sdk.*
 *  5. Mintegral：com.mbridge.msdk.*
 *
 * 硬性限制：
 *  - 仅 Hook SDK 的加载/展示方法，不修改服务端广告下发
 *  - 部分 APP 自研广告位本 Hook 不覆盖
 *  - 可能导致 APP 内购推荐位/活动入口异常（实验性，默认关闭）
 */
object RemoveAdsHook {

    // ===== 穿山甲候选类 =====
    private val CSJ_CANDIDATES = listOf(
        "com.bytedance.sdk.openadsdk.TTAdManager",
        "com.bytedance.sdk.openadsdk.core.TTAdManagerImpl",
        "com.bytedance.sdk.openadsdk.adapter.TTAdManagerAdapter",
        "com.bytedance.sdk.openadsdk.AdSdk"
    )
    // ===== 优量汇候选类 =====
    private val GDT_CANDIDATES = listOf(
        "com.qq.e.ads.ADClient",
        "com.qq.e.comm.managers.GDTADManager",
        "com.qq.e.ads.banner.BannerManager",
        "com.qq.e.ads.interstitial.InterstitialManager"
    )
    // ===== 百度候选类 =====
    private val BAIDU_CANDIDATES = listOf(
        "com.baidu.mobads.AdManager",
        "com.baidu.mobads.AdView",
        "com.baidu.mobads.InterstitialAd",
        "com.baidu.mobads.SplashAd"
    )
    // ===== 快手候选类 =====
    private val KS_CANDIDATES = listOf(
        "com.kwad.sdk.api.KsAdManager",
        "com.kwad.sdk.controller.AdManager",
        "com.kwad.sdk.admanager.KsAdClient"
    )
    // ===== Mintegral 候选类 =====
    private val MTG_CANDIDATES = listOf(
        "com.mbridge.msdk.MBridges",
        "com.mbridge.msdk.out.MBridgeAdManager",
        "com.mbridge.msdk.api.MBridgeAd"
    )

    /** 通用方法名：广告加载/展示相关 */
    private val LOAD_METHODS = listOf(
        "loadAd", "loadAds", "loadBanner", "loadInterstitial", "loadRewardVideo",
        "loadNative", "loadSplash", "loadFeed", "showAd", "showBanner"
    )
    /** 通用方法名：广告是否就绪 */
    private val READY_METHODS = listOf(
        "isReady", "isAdReady", "hasAd", "isLoaded", "isAvailable"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.removeAdsEnabled) return
        LogX.i("【实验性】通用去广告启动（仅应用层）")

        hookCsjAds(lpparam)
        hookGdtAds(lpparam)
        hookBaiduAds(lpparam)
        hookKsAds(lpparam)
        hookMtgAds(lpparam)
    }

    /** 穿山甲广告 SDK Hook */
    private fun hookCsjAds(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in CSJ_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LOAD_METHODS) {
                tryHookNoOp(cls, clsName, m)
            }
            for (m in READY_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, false)
            }
        }
        // Hook TTAdNative 回调，让广告加载失败
        val nativeCandidates = listOf(
            "com.bytedance.sdk.openadsdk.TTAdNative",
            "com.bytedance.sdk.openadsdk.adapter.TTAdNativeAdapter"
        )
        for (clsName in nativeCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            tryHookNoOp(cls, clsName, "onNativeLoad")
            tryHookNoOp(cls, clsName, "onFeedLoad")
        }
    }

    /** 优量汇广告 SDK Hook */
    private fun hookGdtAds(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in GDT_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LOAD_METHODS) {
                tryHookNoOp(cls, clsName, m)
            }
            for (m in READY_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, false)
            }
        }
    }

    /** 百度广告 SDK Hook */
    private fun hookBaiduAds(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in BAIDU_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LOAD_METHODS) {
                tryHookNoOp(cls, clsName, m)
            }
            for (m in READY_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, false)
            }
        }
    }

    /** 快手广告 SDK Hook */
    private fun hookKsAds(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in KS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LOAD_METHODS) {
                tryHookNoOp(cls, clsName, m)
            }
            for (m in READY_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, false)
            }
        }
    }

    /** Mintegral 广告 SDK Hook */
    private fun hookMtgAds(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in MTG_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LOAD_METHODS) {
                tryHookNoOp(cls, clsName, m)
            }
            for (m in READY_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, false)
            }
        }
    }

    /** Hook 一个方法让其空操作（不执行原方法，直接返回） */
    private fun tryHookNoOp(cls: Class<*>, clsName: String, method: String): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    // 拦截原方法，返回 null（让广告加载不执行）
                    p.result = null
                }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }

    /** Hook 无参 boolean 方法返回指定值 */
    private fun tryHookBooleanReturning(cls: Class<*>, clsName: String, method: String, value: Boolean): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = value }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }
}
