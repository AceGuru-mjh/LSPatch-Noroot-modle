package com.batteryopt.noroot

import android.util.Log
import com.batteryopt.noroot.core.ConfigClient
import com.batteryopt.noroot.hooks.*
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.EnvDetector
import com.batteryopt.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
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

            Log.e(TAG, "Loading WakeLockHook...")
            try { if (cfg.wakeLockEnabled) WakeLockHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "WakeLockHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AlarmOptimizerHook...")
            try { if (cfg.alarmEnabled) AlarmOptimizerHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AlarmOptimizerHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BackgroundSyncHook...")
            try { if (cfg.syncEnabled) BackgroundSyncHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BackgroundSyncHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AppOpsRestrictHook...")
            try { if (cfg.appOpsRestrictEnabled) AppOpsRestrictHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AppOpsRestrictHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading JobSchedulerHook...")
            try { if (cfg.jobEnabled) JobSchedulerHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "JobSchedulerHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading LocationOptHook...")
            try { if (cfg.locationEnabled) LocationOptHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "LocationOptHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AnimationOptHook...")
            try { if (cfg.animationEnabled) AnimationOptHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AnimationOptHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading SensorOptHook...")
            try { if (cfg.sensorEnabled) SensorOptHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "SensorOptHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BluetoothScanThrottleHook...")
            try { if (cfg.bluetoothScanThrottleEnabled) BluetoothScanThrottleHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BluetoothScanThrottleHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading CameraBackgroundBlockHook...")
            try { if (cfg.cameraBackgroundBlockEnabled) CameraBackgroundBlockHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "CameraBackgroundBlockHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading VibratorThrottleHook...")
            try { if (cfg.vibratorThrottleEnabled) VibratorThrottleHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "VibratorThrottleHook FAIL: ${e.message}") }

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
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.batteryopt.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { BatteryConfig(packageName = "global") }
    }
}
