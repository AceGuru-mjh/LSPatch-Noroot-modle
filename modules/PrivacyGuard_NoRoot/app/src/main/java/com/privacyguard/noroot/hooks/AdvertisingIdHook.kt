package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 广告ID屏蔽Hook（仅应用层，无法影响系统全局）
 *
 * 硬性限制：
 *  - 仅 Hook Java 层 AdvertisingIdClient API，无法拦截 Native 层直接调用
 *  - 不修改系统 Google Play Services 配置
 *  - 部分国内 APP 使用自研广告ID，本Hook无法覆盖
 *
 * 拦截路径：
 *  1. com.google.android.gms.ads.identifier.AdvertisingIdClient.getAdvertisingIdInfo
 *  2. AdvertisingIdClient$Info.getId
 *  3. com.google.android.gms.ads.identifier.AdvertisingIdClient.Info.isLimitAdTrackingEnabled
 *
 * 返回空/伪造ID，并强制 isLimitAdTrackingEnabled = true（用户已选择退出个性化广告）
 */
object AdvertisingIdHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.advertisingIdBlockEnabled) return
        LogX.i("广告ID屏蔽启动（仅应用层）")

        hookAdvertisingIdClient(lpparam)
        hookAdvertisingIdInfo(lpparam)
    }

    /** Hook AdvertisingIdClient.getAdvertisingIdInfo 静态方法 */
    private fun hookAdvertisingIdClient(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val aicCls = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient",
                lpparam.classLoader) ?: return

            // getAdvertisingIdInfo(Context)
            try {
                XposedHelpers.findAndHookMethod(aicCls, "getAdvertisingIdInfo",
                    "android.content.Context", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            // 让返回的 Info 对象在后续 Hook 中被修改
                            LogX.d("AdvertisingIdClient.getAdvertisingIdInfo 调用拦截")
                        }
                    })
                LogX.hookSuccess("AdvertisingIdClient", "getAdvertisingIdInfo")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getAdvertisingIdInfo(Context, boolean) 重载
            try {
                XposedHelpers.findAndHookMethod(aicCls, "getAdvertisingIdInfo",
                    "android.content.Context", Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("AdvertisingIdClient.getAdvertisingIdInfo(bool) 调用拦截")
                        }
                    })
                LogX.hookSuccess("AdvertisingIdClient", "getAdvertisingIdInfo(bool)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AdvertisingIdClient", "getAdvertisingIdInfo", e)
        }
    }

    /** Hook AdvertisingIdClient.Info 类的 getId / isLimitAdTrackingEnabled */
    private fun hookAdvertisingIdInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val infoCls = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info",
                lpparam.classLoader) ?: return

            // getId() 返回空字符串
            try {
                XposedHelpers.findAndHookMethod(infoCls, "getId", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = ""
                        LogX.d("AdvertisingIdClient.Info.getId -> 空字符串")
                    }
                })
                LogX.hookSuccess("AdvertisingIdClient\$Info", "getId")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // isLimitAdTrackingEnabled() 强制返回 true
            try {
                XposedHelpers.findAndHookMethod(infoCls, "isLimitAdTrackingEnabled",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = true
                        }
                    })
                LogX.hookSuccess("AdvertisingIdClient\$Info", "isLimitAdTrackingEnabled")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AdvertisingIdClient\$Info", "id/lat", e)
        }
    }
}
