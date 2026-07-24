package com.batteryopt.noroot

import android.util.Log
import com.batteryopt.noroot.models.BatteryConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.14"
        const val TAG = "LSP-BatteryOpt"
        const val MODULE_PKG = "com.batteryopt.noroot"
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
            Log.e(TAG, "BatteryOptimizer NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.batteryopt.noroot.ui.UiInitializer")
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

            tryInvoke("com.batteryopt.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.batteryopt.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (cfg.wakeLockEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.WakeLockHook", lpparam, cfg)
            if (cfg.alarmEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.AlarmOptimizerHook", lpparam, cfg)
            if (cfg.syncEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.BackgroundSyncHook", lpparam, cfg)
            if (cfg.appOpsRestrictEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.AppOpsRestrictHook", lpparam, cfg)
            if (cfg.jobEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.JobSchedulerHook", lpparam, cfg)
            if (cfg.locationEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.LocationOptHook", lpparam, cfg)
            if (cfg.animationEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.AnimationOptHook", lpparam, cfg)
            if (cfg.sensorEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.SensorOptHook", lpparam, cfg)
            if (cfg.bluetoothScanThrottleEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.BluetoothScanThrottleHook", lpparam, cfg)
            if (cfg.cameraBackgroundBlockEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.CameraBackgroundBlockHook", lpparam, cfg)
            if (cfg.vibratorThrottleEnabled) tryInvokeHook("com.batteryopt.noroot.hooks.VibratorThrottleHook", lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.taobao.taobao", "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo", "com.eg.android.AlipayGphone",
        "com.netease.cloudmusic", "com.tencent.wmusic",
        "com.zhihu.android", "com.sina.weibo",
        "com.netease.mail", "com.tencent.androidqqmail"
    )

    private fun loadConfig(): BatteryConfig {
        try {
            val reader = Class.forName("com.batteryopt.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? BatteryConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.batteryopt.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as BatteryConfig
        } catch (_: Throwable) { BatteryConfig(packageName = "global") }
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
