package com.gameunlocker.noroot

import android.util.Log
import com.gameunlocker.noroot.core.ConfigClient
import com.gameunlocker.noroot.hooks.*
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.EnvDetector
import com.gameunlocker.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        const val TAG = "LSP-GameUnlocker"
        const val MODULE_PKG = "com.gameunlocker.noroot"
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
            Log.e(TAG, "GameUnlockerPro NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.gameunlocker.noroot.ui.UiInitializer")
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
            if (!isTargetGame(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            EnvDetector.detect(lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) ConfigClient.readMasterSwitch(ctx) else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            Log.e(TAG, "Loading GameDetectionHideHook...")
            try { if (cfg.detectionHideEnabled) GameDetectionHideHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "GameDetectionHideHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading DeviceSpoofHook...")
            try { if (cfg.deviceSpoofEnabled) DeviceSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "DeviceSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading FrameRateUnlockHook...")
            try { if (cfg.frameRateUnlockEnabled) FrameRateUnlockHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "FrameRateUnlockHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ProcessOptimizerHook...")
            try { if (cfg.processOptimizeEnabled) ProcessOptimizerHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ProcessOptimizerHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ResolutionSpoofHook...")
            try { if (cfg.resolutionSpoofEnabled) ResolutionSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ResolutionSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ShizukuSystemTuneHook...")
            try { if (cfg.shizukuSystemTuneEnabled) ShizukuSystemTuneHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuSystemTuneHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading TouchSamplingBoostHook...")
            try { if (cfg.touchSamplingBoostEnabled) TouchSamplingBoostHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "TouchSamplingBoostHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading NetworkLatencyOptHook...")
            try { if (cfg.networkLatencyOptEnabled) NetworkLatencyOptHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "NetworkLatencyOptHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AudioPriorityBoostHook...")
            try { if (cfg.audioPriorityBoostEnabled) AudioPriorityBoostHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AudioPriorityBoostHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading MemoryDefragHook...")
            try { if (cfg.memoryDefragEnabled) MemoryDefragHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "MemoryDefragHook FAIL: ${e.message}") }

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetGame(pkg: String) = pkg in listOf(
        "com.tencent.tmgp.sgame", "com.miHoYo.Yuanshen", "com.miHoYo.GenshinImpact",
        "com.tencent.tmgp.pubgmhd", "com.tencent.ig", "com.miHoYo.hkrpg",
        "com.tencent.tmgp.cod", "com.activision.callofduty.shooter",
        "com.tencent.tmgp.gnyx", "com.gameblackmyth.mobile",
        "com.miHoYo.ZenlessZoneZero", "com.kurogame.kjq"
    )

    private fun loadConfig(): GameConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.gameunlocker.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { GameConfig(packageName = "global") }
    }
}
