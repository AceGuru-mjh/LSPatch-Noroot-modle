package com.privacyguard.noroot

import android.util.Log
import com.privacyguard.noroot.core.ConfigClient
import com.privacyguard.noroot.hooks.*
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.EnvDetector
import com.privacyguard.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        const val TAG = "LSP-PrivacyGuard"
        const val MODULE_PKG = "com.privacyguard.noroot"
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
            Log.e(TAG, "PrivacyGuard NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.privacyguard.noroot.ui.UiInitializer")
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

            Log.e(TAG, "Loading DeviceIdSpoofHook...")
            try { if (cfg.deviceIdSpoofEnabled) DeviceIdSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "DeviceIdSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ClipboardGuardHook...")
            try { if (cfg.clipboardGuardEnabled) ClipboardGuardHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ClipboardGuardHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading PermissionSpoofHook...")
            try { if (cfg.permissionSpoofEnabled) PermissionSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "PermissionSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading PmRevokeHook...")
            try { if (cfg.pmRevokeEnabled) PmRevokeHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "PmRevokeHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading LocationSpoofHook...")
            try { if (cfg.locationSpoofEnabled) LocationSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "LocationSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading SensorFakerHook...")
            try { if (cfg.sensorFakerEnabled) SensorFakerHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "SensorFakerHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AdvertisingIdHook...")
            try { if (cfg.advertisingIdBlockEnabled) AdvertisingIdHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AdvertisingIdHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading PackageVisibilitySpoofHook...")
            try { if (cfg.packageVisibilitySpoofEnabled) PackageVisibilitySpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "PackageVisibilitySpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading NetworkInfoSpoofHook...")
            try { if (cfg.networkInfoSpoofEnabled) NetworkInfoSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "NetworkInfoSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ScreenMetricsSpoofHook...")
            try { if (cfg.screenMetricsSpoofEnabled) ScreenMetricsSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ScreenMetricsSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading StoragePathSpoofHook...")
            try { if (cfg.storagePathSpoofEnabled) StoragePathSpoofHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "StoragePathSpoofHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading PrivacyPlusHook...")
            try { if (cfg.installStatusSpoofEnabled || cfg.mockLocationSystemLevelEnabled) PrivacyPlusHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "PrivacyPlusHook FAIL: ${e.message}") }

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq", "com.tencent.mobileqqi",
        "com.android.settings", "com.android.chrome", "com.mi.globalbrowser",
        "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.xunmeng.pinduoduo",
        "com.jingdong.app.mall", "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.sina.weibo"
    )

    private fun loadConfig(): PrivacyConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.privacyguard.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { PrivacyConfig(packageName = "global") }
    }
}
