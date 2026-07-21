package com.batteryopt.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.batteryopt.noroot.models.BatteryConfig

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
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

            try {
                Class.forName("com.batteryopt.noroot.utils.EnvDetector")
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
                try { Class.forName("com.batteryopt.noroot.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            val loader = lpparam.classLoader

            if (cfg.wakeLockEnabled) tryInvoke("com.batteryopt.noroot.hooks.WakeLockHook", "apply", loader, lpparam, cfg)
            if (cfg.alarmEnabled) tryInvoke("com.batteryopt.noroot.hooks.AlarmOptimizerHook", "apply", loader, lpparam, cfg)
            if (cfg.syncEnabled) tryInvoke("com.batteryopt.noroot.hooks.BackgroundSyncHook", "apply", loader, lpparam, cfg)
            if (cfg.appOpsRestrictEnabled) tryInvoke("com.batteryopt.noroot.hooks.AppOpsRestrictHook", "apply", loader, lpparam, cfg)
            if (cfg.jobEnabled) tryInvoke("com.batteryopt.noroot.hooks.JobSchedulerHook", "apply", loader, lpparam, cfg)
            if (cfg.locationEnabled) tryInvoke("com.batteryopt.noroot.hooks.LocationOptHook", "apply", loader, lpparam, cfg)
            if (cfg.animationEnabled) tryInvoke("com.batteryopt.noroot.hooks.AnimationOptHook", "apply", loader, lpparam, cfg)
            if (cfg.sensorEnabled) tryInvoke("com.batteryopt.noroot.hooks.SensorOptHook", "apply", loader, lpparam, cfg)
            if (cfg.bluetoothScanThrottleEnabled) tryInvoke("com.batteryopt.noroot.hooks.BluetoothScanThrottleHook", "apply", loader, lpparam, cfg)
            if (cfg.cameraBackgroundBlockEnabled) tryInvoke("com.batteryopt.noroot.hooks.CameraBackgroundBlockHook", "apply", loader, lpparam, cfg)
            if (cfg.vibratorThrottleEnabled) tryInvoke("com.batteryopt.noroot.hooks.VibratorThrottleHook", "apply", loader, lpparam, cfg)

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

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, BatteryConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): BatteryConfig {
        try {
            val reader = Class.forName("com.batteryopt.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? BatteryConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.batteryopt.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? BatteryConfig
                ?: BatteryConfig()
        } catch (_: Throwable) {
            return BatteryConfig()
        }
    }
}
