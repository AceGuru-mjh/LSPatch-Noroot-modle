package com.videosaver.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.videosaver.noroot.models.VideoConfig

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
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
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            try {
                Class.forName("com.videosaver.noroot.utils.EnvDetector")
                    .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                    .invoke(null, lpparam)
            } catch (_: Throwable) { }

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) {
                try { Class.forName("com.videosaver.noroot.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            val loader = lpparam.classLoader

            if (cfg.douyinNoWatermark) tryInvoke("com.videosaver.noroot.hooks.DouyinNoWatermarkHook", "apply", loader, lpparam, cfg)
            if (cfg.kuaishouNoWatermark) tryInvoke("com.videosaver.noroot.hooks.KuaishouNoWatermarkHook", "apply", loader, lpparam, cfg)
            if (cfg.xhsNoWatermark) tryInvoke("com.videosaver.noroot.hooks.XhsNoWatermarkHook", "apply", loader, lpparam, cfg)
            if (cfg.biliDownload) tryInvoke("com.videosaver.noroot.hooks.BiliDownloadHook", "apply", loader, lpparam, cfg)
            if (cfg.shizukuCaptureEnabled) tryInvoke("com.videosaver.noroot.hooks.ShizukuCaptureHook", "apply", loader, lpparam, cfg)
            if (cfg.autoDownloadEnabled) tryInvoke("com.videosaver.noroot.hooks.AutoDownloadHook", "apply", loader, lpparam, cfg)
            if (cfg.removeAdsEnabled) tryInvoke("com.videosaver.noroot.hooks.RemoveVideoAdsHook", "apply", loader, lpparam, cfg)
            if (cfg.saveOriginalQualityEnabled) tryInvoke("com.videosaver.noroot.hooks.SaveOriginalQualityHook", "apply", loader, lpparam, cfg)
            if (cfg.batchDownloadEnabled) tryInvoke("com.videosaver.noroot.hooks.BatchDownloadHook", "apply", loader, lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
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

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, VideoConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): VideoConfig {
        try {
            val reader = Class.forName("com.videosaver.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? VideoConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.videosaver.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? VideoConfig
                ?: VideoConfig()
        } catch (_: Throwable) {
            return VideoConfig()
        }
    }
}
