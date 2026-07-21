package com.stepmod.noroot

import android.util.Log
import com.stepmod.noroot.core.ConfigClient
import com.stepmod.noroot.hooks.*
import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.EnvDetector
import com.stepmod.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
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
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

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

            Log.e(TAG, "Loading StepSensorHook...")
            try { if (cfg.stepModifyEnabled) StepSensorHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "StepSensorHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading StepReportHook...")
            try { if (cfg.stepModifyEnabled) StepReportHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "StepReportHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading StepCounterHook...")
            try { if (cfg.stepModifyEnabled) StepCounterHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "StepCounterHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ContentProviderInjectHook...")
            try { if (cfg.contentProviderInjectEnabled) ContentProviderInjectHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ContentProviderInjectHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading SensorBlockHook...")
            try { if (cfg.sensorBlockEnabled) SensorBlockHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "SensorBlockHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading MultiAppSyncHook...")
            try { if (cfg.multiAppSyncEnabled) MultiAppSyncHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "MultiAppSyncHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading StepHistoryFakeHook...")
            try { if (cfg.stepHistoryFakeEnabled) StepHistoryFakeHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "StepHistoryFakeHook FAIL: ${e.message}") }

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
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
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.stepmod.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { StepConfig(packageName = "global") }
    }
}
