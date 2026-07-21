package com.adblockerx.noroot

import android.util.Log
import com.adblockerx.noroot.hooks.AdViewHideHook
import com.adblockerx.noroot.hooks.AdClosePlusHook
import com.adblockerx.noroot.hooks.CookieCleanHook
import com.adblockerx.noroot.hooks.HostsFilterHook
import com.adblockerx.noroot.hooks.IntentInterceptorHook
import com.adblockerx.noroot.hooks.OkHttpAdHook
import com.adblockerx.noroot.hooks.RedirectBlockHook
import com.adblockerx.noroot.hooks.ShizukuDnsHook
import com.adblockerx.noroot.hooks.TrackerBlockHook
import com.adblockerx.noroot.hooks.URLConnectionAdHook
import com.adblockerx.noroot.hooks.WebViewAdHook
import com.adblockerx.noroot.utils.AntiDetectionHelper
import com.adblockerx.noroot.utils.EnvDetector
import com.adblockerx.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        const val TAG = "LSP-AdBlockerX"
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
        Log.e(TAG, "handleLoadPackage entered: pkg=${lpparam.packageName}")

        if (lpparam.processName != lpparam.packageName) return

        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            EnvDetector.detect(lpparam)

            val cfg = loadConfig()
            if (!cfg.masterEnabled) {
                Log.e(TAG, "Master disabled, skipping hooks")
                return
            }

            Log.e(TAG, "Loading HostsFilterHook...")
            try { if (cfg.hostsFilterEnabled) HostsFilterHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "HostsFilterHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading WebViewAdHook...")
            try { if (cfg.webviewAdEnabled) WebViewAdHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "WebViewAdHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading OkHttpAdHook...")
            try { if (cfg.okHttpAdEnabled) OkHttpAdHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "OkHttpAdHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading URLConnectionAdHook...")
            try { if (cfg.urlConnectionAdEnabled) URLConnectionAdHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "URLConnectionAdHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AdViewHideHook...")
            try { if (cfg.adViewHideEnabled) AdViewHideHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AdViewHideHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading TrackerBlockHook...")
            try { if (cfg.trackerBlockEnabled) TrackerBlockHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "TrackerBlockHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading CookieCleanHook...")
            try { if (cfg.cookieCleanEnabled) CookieCleanHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "CookieCleanHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading RedirectBlockHook...")
            try { if (cfg.redirectBlockEnabled) RedirectBlockHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "RedirectBlockHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading IntentInterceptorHook...")
            try { if (cfg.intentInterceptorEnabled) IntentInterceptorHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "IntentInterceptorHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AdClosePlusHook...")
            try { if (cfg.screenshotUnlockEnabled || cfg.shakeAdBlockEnabled || cfg.vpnDetectBypassEnabled) AdClosePlusHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AdClosePlusHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ShizukuDnsHook...")
            try { if (cfg.dnsAdBlockEnabled) ShizukuDnsHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuDnsHook FAIL: ${e.message}") }

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
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.adblockerx.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { com.adblockerx.noroot.models.AdBlockConfig() }
    }
}
