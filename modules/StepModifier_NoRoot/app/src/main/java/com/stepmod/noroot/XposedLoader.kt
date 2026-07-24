package com.stepmod.noroot

import android.util.Log
import com.stepmod.noroot.models.StepConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.15"
        const val TAG = "LSP-StepModifier"
        const val MODULE_PKG = "com.stepmod.noroot"
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
            Log.e(TAG, "StepModifier NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.stepmod.noroot.ui.UiInitializer")
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
            tryInvokeVoid("com.stepmod.noroot.utils.CrashGuard", "init", null)

            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            tryInvoke("com.stepmod.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.stepmod.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (cfg.stepModifyEnabled) tryInvokeHook("com.stepmod.noroot.hooks.StepSensorHook", lpparam, cfg)
            if (cfg.stepModifyEnabled) tryInvokeHook("com.stepmod.noroot.hooks.StepReportHook", lpparam, cfg)
            if (cfg.stepModifyEnabled) tryInvokeHook("com.stepmod.noroot.hooks.StepCounterHook", lpparam, cfg)
            if (cfg.contentProviderInjectEnabled) tryInvokeHook("com.stepmod.noroot.hooks.ContentProviderInjectHook", lpparam, cfg)
            if (cfg.sensorBlockEnabled) tryInvokeHook("com.stepmod.noroot.hooks.SensorBlockHook", lpparam, cfg)
            if (cfg.multiAppSyncEnabled) tryInvokeHook("com.stepmod.noroot.hooks.MultiAppSyncHook", lpparam, cfg)
            if (cfg.stepHistoryFakeEnabled) tryInvokeHook("com.stepmod.noroot.hooks.StepHistoryFakeHook", lpparam, cfg)
            if (cfg.scheduleStepEnabled) tryInvokeHook("com.stepmod.noroot.hooks.ScheduleStepHook", lpparam, cfg)
            if (cfg.calorieCalcEnabled) tryInvokeHook("com.stepmod.noroot.hooks.CalorieCalculatorHook", lpparam, cfg)
            if (cfg.competitionModeEnabled) tryInvokeHook("com.stepmod.noroot.hooks.CompetitionModeHook", lpparam, cfg)
            if (cfg.antiDetectionEnabled) tryInvokeHook("com.stepmod.noroot.hooks.AntiDetectionStepHook", lpparam, cfg)
            if (cfg.gpxRouteEnabled) tryInvokeHook("com.stepmod.noroot.hooks.GpxRouteInjectHook", lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            tryInvokeVoid("com.stepmod.noroot.utils.CrashGuard", "log", "FATAL: ${e.stackTraceToString()}")
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.eg.android.AlipayGphone", "com.tencent.mm", "com.tencent.mobileqq",
        "com.tencent.tim", "com.xiaomi.hm.health", "com.huawei.health",
        "com.codoon.gps", "com.joyrun.gps", "com.keepfitness",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.netease.cloudmusic", "com.tencent.wmusic",
        "com.taobao.taobao", "com.jingdong.app.mall"
    )

    private fun loadConfig(): StepConfig {
        try {
            val reader = Class.forName("com.stepmod.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? StepConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.stepmod.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as StepConfig
        } catch (_: Throwable) { StepConfig(packageName = "global") }
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
