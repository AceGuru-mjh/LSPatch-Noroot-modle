package com.adblockerx.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.15"
        const val TAG = "LSP-AdBlockerX"
        const val MODULE_PKG = "com.adblockerx.noroot"
        var currentPkg: String? = null
        var isIntegratedMode: Boolean = false
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        isIntegratedMode = try {
            Class.forName("org.lsposed.lspatch.LSPatch")
            false
        } catch (_: Throwable) {
            true
        }

        if (isIntegratedMode) {
            Log.e(TAG, "Integrated mode: UI stripped, hooks only")
        } else {
            Log.e(TAG, "AdBlockerX NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.adblockerx.noroot.ui.UiInitializer")
                    .getDeclaredMethod("initAllUi", android.content.Context::class.java)
                    .invoke(null, Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null))
            } catch (t: Throwable) {
                Log.e(TAG, "UI init failed: ${t.message}")
            }
            return
        }

        if (lpparam.processName != lpparam.packageName) return

        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            tryInvoke("com.adblockerx.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.adblockerx.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (cfg.hostsFilterEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.HostsFilterHook", lpparam, cfg)
            if (cfg.webviewAdEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.WebViewAdHook", lpparam, cfg)
            if (cfg.okHttpAdEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.OkHttpAdHook", lpparam, cfg)
            if (cfg.urlConnectionAdEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.URLConnectionAdHook", lpparam, cfg)
            if (cfg.adViewHideEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.AdViewHideHook", lpparam, cfg)
            if (cfg.trackerBlockEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.TrackerBlockHook", lpparam, cfg)
            if (cfg.cookieCleanEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.CookieCleanHook", lpparam, cfg)
            if (cfg.redirectBlockEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.RedirectBlockHook", lpparam, cfg)
            if (cfg.intentInterceptorEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.IntentInterceptorHook", lpparam, cfg)
            if (cfg.screenshotUnlockEnabled || cfg.shakeAdBlockEnabled || cfg.vpnDetectBypassEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.AdClosePlusHook", lpparam, cfg)
            if (cfg.dnsAdBlockEnabled) tryInvokeHook("com.adblockerx.noroot.hooks.ShizukuDnsHook", lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.android.chrome",
        "com.mi.globalbrowser",
        "com.huawei.browser",
        "com.sec.android.app.sbrowser",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone",
        "com.zhihu.android",
        "com.netease.cloudmusic",
        "com.tencent.wmusic"
    )

    private fun loadConfig(): com.adblockerx.noroot.models.AdBlockConfig {
        try {
            val reader = Class.forName("com.adblockerx.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? com.adblockerx.noroot.models.AdBlockConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.adblockerx.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as com.adblockerx.noroot.models.AdBlockConfig
        } catch (_: Throwable) { com.adblockerx.noroot.models.AdBlockConfig() }
    }

    private fun tryInvoke(className: String, methodName: String, vararg args: Any?) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == args.size }
            method?.invoke(null, *args)
        } catch (e: Throwable) {
            Log.e(TAG, "${className.substringAfterLast('.')}#$methodName FAIL: ${e.message}")
        }
    }

    private fun <T> tryInvokeCtx(className: String, methodName: String, ctx: android.content.Context): T? {
        return try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            method?.invoke(null, ctx) as? T
        } catch (_: Throwable) { null }
    }

    private fun tryInvokeHook(className: String, lpparam: XC_LoadPackage.LoadPackageParam, cfg: Any?) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.parameterCount == 2 }
            method?.invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "${className.substringAfterLast('.')} FAIL: ${e.message}")
        }
    }
}
