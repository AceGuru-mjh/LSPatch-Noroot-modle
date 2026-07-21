package com.adblockerx.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.adblockerx.noroot.models.AdBlockConfig

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        const val TAG = "LSP-AdBlockerX"
        const val MODULE_PKG = "com.adblockerx.noroot"
        var currentPkg: String? = null
        var ctx: android.content.Context? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        Log.e(TAG, "AdBlockerX NoRoot v$VERSION initZygote")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 非主进程直接跳过
        if (lpparam.processName != lpparam.packageName) return

        try {
            // ===== 模块自身进程: 反射加载UI =====
            if (lpparam.packageName == MODULE_PKG) {
                Log.e(TAG, "Module own process - loading UI via reflection")
                initConfig(lpparam)
                try {
                    Class.forName("com.adblockerx.noroot.ui.UiInitializer")
                        .getDeclaredMethod("initAllUi", android.content.Context::class.java)
                        .invoke(null, ctx)
                } catch (t: Throwable) {
                    Log.e(TAG, "UI init failed: ${t.message}")
                }
                return
            }

            // ===== 宿主进程: 纯Hook(反射加载, 绝不import hook类) =====
            val pkg = lpparam.packageName ?: return
            if (pkg == "android") return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            initConfig(lpparam)
            Log.e(TAG, "Host process: loading hooks for $pkg (reflection mode)")

            val cfg = loadConfig()
            if (!cfg.masterEnabled) return

            // 全部用反射调用, 宿主进程不 import 任何 hook 类
            val loader = lpparam.classLoader

            tryInvoke("com.adblockerx.noroot.hooks.HostsFilterHook", "apply", loader, lpparam, cfg)
            if (cfg.hostsFilterEnabled) tryInvoke("com.adblockerx.noroot.hooks.HostsFilterHook", "apply", loader, lpparam, cfg)

            if (cfg.webviewAdEnabled) tryInvoke("com.adblockerx.noroot.hooks.WebViewAdHook", "apply", loader, lpparam, cfg)
            if (cfg.okHttpAdEnabled) tryInvoke("com.adblockerx.noroot.hooks.OkHttpAdHook", "apply", loader, lpparam, cfg)
            if (cfg.urlConnectionAdEnabled) tryInvoke("com.adblockerx.noroot.hooks.URLConnectionAdHook", "apply", loader, lpparam, cfg)
            if (cfg.adViewHideEnabled) tryInvoke("com.adblockerx.noroot.hooks.AdViewHideHook", "apply", loader, lpparam, cfg)

            if (cfg.trackerBlockEnabled) tryInvoke("com.adblockerx.noroot.hooks.TrackerBlockHook", "apply", loader, lpparam, cfg)
            if (cfg.cookieCleanEnabled) tryInvoke("com.adblockerx.noroot.hooks.CookieCleanHook", "apply", loader, lpparam, cfg)
            if (cfg.redirectBlockEnabled) tryInvoke("com.adblockerx.noroot.hooks.RedirectBlockHook", "apply", loader, lpparam, cfg)
            if (cfg.intentInterceptorEnabled) tryInvoke("com.adblockerx.noroot.hooks.IntentInterceptorHook", "apply", loader, lpparam, cfg)

            if (cfg.screenshotUnlockEnabled || cfg.shakeAdBlockEnabled || cfg.vpnDetectBypassEnabled)
                tryInvoke("com.adblockerx.noroot.hooks.AdClosePlusHook", "apply", loader, lpparam, cfg)

            if (cfg.dnsAdBlockEnabled)
                tryInvoke("com.adblockerx.noroot.hooks.ShizukuDnsHook", "apply", loader, lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    /** 反射调用 hook 的 apply 方法, 绝不 import hook 类 */
    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, AdBlockConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.android.chrome", "com.mi.globalbrowser", "com.huawei.browser",
        "com.sec.android.app.sbrowser", "com.tencent.mm", "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.taobao.taobao",
        "com.jingdong.app.mall", "com.xunmeng.pinduoduo", "com.eg.android.AlipayGphone",
        "com.zhihu.android", "com.netease.cloudmusic", "com.tencent.wmusic"
    )

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? android.app.Application
            if (app != null) {
                ctx = app
                try {
                    Class.forName("com.adblockerx.noroot.utils.ConfigManager")
                        .getDeclaredMethod("init", android.content.Context::class.java)
                        .invoke(null, app)
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }

    private fun loadConfig(): AdBlockConfig {
        try {
            val reader = Class.forName("com.adblockerx.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? AdBlockConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.adblockerx.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? AdBlockConfig
                ?: AdBlockConfig()
        } catch (_: Throwable) {
            return AdBlockConfig()
        }
    }
}
