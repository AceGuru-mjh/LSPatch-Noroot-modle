package com.privacyguard.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.privacyguard.noroot.models.PrivacyConfig

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
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

            try {
                Class.forName("com.privacyguard.noroot.utils.EnvDetector")
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
                try { Class.forName("com.privacyguard.noroot.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            val loader = lpparam.classLoader

            if (cfg.deviceIdSpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.DeviceIdSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.clipboardGuardEnabled) tryInvoke("com.privacyguard.noroot.hooks.ClipboardGuardHook", "apply", loader, lpparam, cfg)
            if (cfg.permissionSpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.PermissionSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.pmRevokeEnabled) tryInvoke("com.privacyguard.noroot.hooks.PmRevokeHook", "apply", loader, lpparam, cfg)
            if (cfg.locationSpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.LocationSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.sensorFakerEnabled) tryInvoke("com.privacyguard.noroot.hooks.SensorFakerHook", "apply", loader, lpparam, cfg)
            if (cfg.advertisingIdBlockEnabled) tryInvoke("com.privacyguard.noroot.hooks.AdvertisingIdHook", "apply", loader, lpparam, cfg)
            if (cfg.packageVisibilitySpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.PackageVisibilitySpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.networkInfoSpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.NetworkInfoSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.screenMetricsSpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.ScreenMetricsSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.storagePathSpoofEnabled) tryInvoke("com.privacyguard.noroot.hooks.StoragePathSpoofHook", "apply", loader, lpparam, cfg)
            if (cfg.installStatusSpoofEnabled || cfg.mockLocationSystemLevelEnabled) tryInvoke("com.privacyguard.noroot.hooks.PrivacyPlusHook", "apply", loader, lpparam, cfg)

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

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, PrivacyConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): PrivacyConfig {
        try {
            val reader = Class.forName("com.privacyguard.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? PrivacyConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.privacyguard.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? PrivacyConfig
                ?: PrivacyConfig()
        } catch (_: Throwable) {
            return PrivacyConfig()
        }
    }
}
