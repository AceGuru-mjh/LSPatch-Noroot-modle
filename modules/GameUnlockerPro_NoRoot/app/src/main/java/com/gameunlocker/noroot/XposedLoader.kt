package com.gameunlocker.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.gameunlocker.noroot.models.GameConfig

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

            try {
                Class.forName("com.gameunlocker.noroot.utils.EnvDetector")
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
                try { Class.forName("com.gameunlocker.noroot.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            val loader = lpparam.classLoader

            if (cfg.detectionHideEnabled) tryInvoke("com.gameunlocker.noroot.hooks.GameDetectionHideHook", "apply", loader, lpparam, cfg)
            if (cfg.deviceSpoofEnabled) tryInvoke("com.gameunlocker.noroot.hooks.DeviceSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.frameRateUnlockEnabled) tryInvoke("com.gameunlocker.noroot.hooks.FrameRateUnlockHook", "apply", loader, lpparam, cfg)
            if (cfg.processOptimizeEnabled) tryInvoke("com.gameunlocker.noroot.hooks.ProcessOptimizerHook", "apply", loader, lpparam, cfg)
            if (cfg.resolutionSpoofEnabled) tryInvoke("com.gameunlocker.noroot.hooks.ResolutionSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.shizukuSystemTuneEnabled) tryInvoke("com.gameunlocker.noroot.hooks.ShizukuSystemTuneHook", "apply", loader, lpparam, cfg)
            if (cfg.touchSamplingBoostEnabled) tryInvoke("com.gameunlocker.noroot.hooks.TouchSamplingBoostHook", "apply", loader, lpparam, cfg)
            if (cfg.networkLatencyOptEnabled) tryInvoke("com.gameunlocker.noroot.hooks.NetworkLatencyOptHook", "apply", loader, lpparam, cfg)
            if (cfg.audioPriorityBoostEnabled) tryInvoke("com.gameunlocker.noroot.hooks.AudioPriorityBoostHook", "apply", loader, lpparam, cfg)
            if (cfg.memoryDefragEnabled) tryInvoke("com.gameunlocker.noroot.hooks.MemoryDefragHook", "apply", loader, lpparam, cfg)

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

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, GameConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): GameConfig {
        try {
            val reader = Class.forName("com.gameunlocker.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? GameConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.gameunlocker.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? GameConfig
                ?: GameConfig()
        } catch (_: Throwable) {
            return GameConfig()
        }
    }
}
