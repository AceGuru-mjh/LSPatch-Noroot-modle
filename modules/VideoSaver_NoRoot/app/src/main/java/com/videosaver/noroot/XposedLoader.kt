package com.videosaver.noroot

import android.util.Log
import com.videosaver.noroot.models.VideoConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.15"
        const val TAG = "LSP-VideoSaver"
        const val MODULE_PKG = "com.videosaver.noroot"
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
            Log.e(TAG, "VideoSaver NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.videosaver.noroot.ui.UiInitializer")
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
            tryInvokeVoid("com.videosaver.noroot.utils.CrashGuard", "init", null)

            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            tryInvoke("com.videosaver.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.videosaver.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (cfg.douyinNoWatermark) tryInvokeHook("com.videosaver.noroot.hooks.DouyinNoWatermarkHook", lpparam, cfg)
            if (cfg.kuaishouNoWatermark) tryInvokeHook("com.videosaver.noroot.hooks.KuaishouNoWatermarkHook", lpparam, cfg)
            if (cfg.xhsNoWatermark) tryInvokeHook("com.videosaver.noroot.hooks.XhsNoWatermarkHook", lpparam, cfg)
            if (cfg.biliDownload) tryInvokeHook("com.videosaver.noroot.hooks.BiliDownloadHook", lpparam, cfg)
            if (cfg.shizukuCaptureEnabled) tryInvokeHook("com.videosaver.noroot.hooks.ShizukuCaptureHook", lpparam, cfg)
            if (cfg.autoDownloadEnabled) tryInvokeHook("com.videosaver.noroot.hooks.AutoDownloadHook", lpparam, cfg)
            if (cfg.removeAdsEnabled) tryInvokeHook("com.videosaver.noroot.hooks.RemoveVideoAdsHook", lpparam, cfg)
            if (cfg.saveOriginalQualityEnabled) tryInvokeHook("com.videosaver.noroot.hooks.SaveOriginalQualityHook", lpparam, cfg)
            if (cfg.batchDownloadEnabled) tryInvokeHook("com.videosaver.noroot.hooks.BatchDownloadHook", lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            tryInvokeVoid("com.videosaver.noroot.utils.CrashGuard", "log", "FATAL: ${e.stackTraceToString()}")
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite",
        "com.smile.gifmaker", "com.kuaishou.nebula",
        "com.xingin.xhs", "com.xingin.xhscircle",
        "tv.danmaku.bili", "com.tencent.qqlive",
        "com.ss.android.article.video", "com.hihonor.cloudmusic"
    )

    private fun loadConfig(): VideoConfig {
        try {
            val reader = Class.forName("com.videosaver.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? VideoConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.videosaver.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as VideoConfig
        } catch (_: Throwable) { VideoConfig(packageName = "global") }
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

    private fun tryInvokeVoid(className: String, methodName: String, arg: Any?) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            method?.invoke(null, arg)
        } catch (_: Throwable) { }
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
