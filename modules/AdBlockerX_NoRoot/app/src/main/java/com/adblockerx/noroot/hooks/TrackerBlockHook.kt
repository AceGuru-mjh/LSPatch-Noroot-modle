package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.ConfigManager
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】追踪 SDK 拦截 Hook
 *
 * 拦截策略：
 *  - 反射查找常见追踪/统计 SDK 的上报方法（Umeng/TalkingData/Flurry/Bugly等）
 *  - Hook 上报方法直接 return，阻断数据上报
 *  - 类不存在则跳过，绝不抛出异常
 *
 * 边界声明：
 *  - 仅作用于本 APP 进程内的 SDK 调用
 *  - SDK 类名被混淆时自动跳过
 */
object TrackerBlockHook {

    /** 追踪 SDK 上报方法候选（类名 → 方法名列表） */
    private val TRACKER_METHODS: List<Pair<String, List<String>>> = listOf(
        // Umeng
        "com.umeng.analytics.MobclickAgent" to listOf("onEvent", "onEventValue", "onProfileSignIn", "onProfileSignOff", "reportError"),
        "com.umeng.commonsdk.UMConfigure" to listOf("init", "getGid"),

        // TalkingData
        "com.tendcloud.tenddata.TCAgent" to listOf("onEvent", "onPageStart", "onPageEnd", "setReportUncaughtException"),

        // Flurry
        "com.flurry.android.FlurryAgent" to listOf("onEvent", "onPageView", "onError", "logEvent"),

        // Bugly (腾讯)
        "com.tencent.bugly.crashreport.CrashReport" to listOf("postCatchedException", "reportException"),

        // Bugly Beta
        "com.tencent.bugly.beta.Beta" to listOf("checkUpgrade", "uploadPatch"),

        // Baidu Mtj
        "com.baidu.mobstat.StatService" to listOf("onEvent", "onPageStart", "onPageEnd", "traceEvent"),

        // 360 阿拉丁
        "com.qihoo.mobl.conn.ConnectionService" to listOf("reportEvent"),

        // TalkingData AppAnalytics
        "com.tendcloud.appcpa.TalkingDataSDK" to listOf("onEvent", "onPageBegin", "onPageEnd"),
        // Sensors Data
        "com.sensorsdata.analytics.android.sdk.SensorsDataAPI" to listOf("track", "profileSet", "profileIncrement"),

        // GrowingIO
        "com.growingio.android.sdk.collection.GrowingIO" to listOf("track", "setUserId", "setVisitor"),

        // 神策
        "com.sensorsdata.analytics.android.sdk.SAConfigOptions" to listOf("enableLog"),

        // Google Firebase Analytics
        "com.google.firebase.analytics.FirebaseAnalytics" to listOf("logEvent", "setUserId", "setUserProperty"),

        // Google Analytics (旧版)
        "com.google.android.gms.analytics.Tracker" to listOf("send", "setScreenName"),

        // AppsFlyer
        "com.appsflyer.AppsFlyerLib" to listOf("trackEvent", "trackAppLaunch", "setCustomerId"),

        // Adjust
        "com.adjust.sdk.Adjust" to listOf("trackEvent", "trackAdRevenue", "setOfflineMode"),

        // OneSignal
        "com.onesignal.OneSignal" to listOf("sendTag", "sendOutcome", "setExternalUserId"),

        // Branch
        "io.branch.referral.Branch" to listOf("userCompletedAction", "sendCommerceEvent")
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.trackerBlockEnabled) return
        LogX.i("【实验性】TrackerBlockHook 启动（应用进程内，多候选类名容错）")

        var hooked = 0
        for ((className, methods) in TRACKER_METHODS) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            for (methodName in methods) {
                if (hookAllOverloads(clazz, methodName)) hooked++
            }
        }
        LogX.i("TrackerBlockHook 完成：已拦截 ${hooked} 个上报方法")
    }

    /** Hook 类的所有同名方法重载 */
    private fun hookAllOverloads(clazz: Class<*>, methodName: String): Boolean {
        return try {
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            var success = false
            for (m in methods) {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try { ConfigManager.incrementBlockedCount(1) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                            LogX.d("[Tracker] 拦截上报: ${clazz.name}.$methodName")
                            val ret = m.returnType
                            val result = when (ret) {
                                Void.TYPE -> null
                                Boolean::class.javaPrimitiveType -> java.lang.Boolean.FALSE
                                Int::class.javaPrimitiveType -> 0 as Any
                                Long::class.javaPrimitiveType -> 0L as Any
                                Float::class.javaPrimitiveType -> 0f as Any
                                Double::class.javaPrimitiveType -> 0.0 as Any
                                else -> null
                            }
                            p.setResult(result)
                        }
                    })
                    success = true
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
            success
        } catch (e: Throwable) {
            LogX.d("[Tracker] Hook 异常: ${clazz.name}.$methodName - ${e.message}")
            false
        }
    }
}
