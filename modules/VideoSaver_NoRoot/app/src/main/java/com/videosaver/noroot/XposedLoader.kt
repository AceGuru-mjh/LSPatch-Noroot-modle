package com.videosaver.noroot

import android.util.Log
import com.videosaver.noroot.hooks.*
import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.EnvDetector
import com.videosaver.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        const val TAG = "LSP-VideoSaver"
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

            Log.e(TAG, "Loading DouyinNoWatermarkHook...")
            try { if (cfg.douyinNoWatermark) DouyinNoWatermarkHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "DouyinNoWatermarkHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading KuaishouNoWatermarkHook...")
            try { if (cfg.kuaishouNoWatermark) KuaishouNoWatermarkHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "KuaishouNoWatermarkHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading XhsNoWatermarkHook...")
            try { if (cfg.xhsNoWatermark) XhsNoWatermarkHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "XhsNoWatermarkHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BiliDownloadHook...")
            try { if (cfg.biliDownload) BiliDownloadHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BiliDownloadHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ShizukuCaptureHook...")
            try { if (cfg.shizukuCaptureEnabled) ShizukuCaptureHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuCaptureHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AutoDownloadHook...")
            try { if (cfg.autoDownloadEnabled) AutoDownloadHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AutoDownloadHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading RemoveVideoAdsHook...")
            try { if (cfg.removeAdsEnabled) RemoveVideoAdsHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "RemoveVideoAdsHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading SaveOriginalQualityHook...")
            try { if (cfg.saveOriginalQualityEnabled) SaveOriginalQualityHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "SaveOriginalQualityHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BatchDownloadHook...")
            try { if (cfg.batchDownloadEnabled) BatchDownloadHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BatchDownloadHook FAIL: ${e.message}") }

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

    private fun loadConfig(): VideoConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.videosaver.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { VideoConfig(packageName = "global") }
    }
}
