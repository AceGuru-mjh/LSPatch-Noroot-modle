package com.gameunlocker.noroot

import android.util.Log
import com.gameunlocker.noroot.models.GameConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.15"
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

            tryInvoke("com.gameunlocker.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.gameunlocker.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (cfg.detectionHideEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.GameDetectionHideHook", lpparam, cfg)
            if (cfg.deviceSpoofEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.DeviceSpoofHook", lpparam, cfg)
            if (cfg.frameRateUnlockEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.FrameRateUnlockHook", lpparam, cfg)
            if (cfg.processOptimizeEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.ProcessOptimizerHook", lpparam, cfg)
            if (cfg.resolutionSpoofEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.ResolutionSpoofHook", lpparam, cfg)
            if (cfg.shizukuSystemTuneEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.ShizukuSystemTuneHook", lpparam, cfg)
            if (cfg.touchSamplingBoostEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.TouchSamplingBoostHook", lpparam, cfg)
            if (cfg.networkLatencyOptEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.NetworkLatencyOptHook", lpparam, cfg)
            if (cfg.audioPriorityBoostEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.AudioPriorityBoostHook", lpparam, cfg)
            if (cfg.memoryDefragEnabled) tryInvokeHook("com.gameunlocker.noroot.hooks.MemoryDefragHook", lpparam, cfg)

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
        try {
            val reader = Class.forName("com.gameunlocker.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? GameConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.gameunlocker.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as GameConfig
        } catch (_: Throwable) { GameConfig(packageName = "global") }
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
