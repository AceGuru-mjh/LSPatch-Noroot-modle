package com.privacyguard.noroot

import android.util.Log
import com.privacyguard.noroot.models.PrivacyConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.14"
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
            tryInvokeVoid("com.privacyguard.noroot.utils.CrashGuard", "init", null)

            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            tryInvoke("com.privacyguard.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.privacyguard.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (cfg.deviceIdSpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.DeviceIdSpoofHook", lpparam, cfg)
            if (cfg.clipboardGuardEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.ClipboardGuardHook", lpparam, cfg)
            if (cfg.permissionSpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.PermissionSpoofHook", lpparam, cfg)
            if (cfg.pmRevokeEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.PmRevokeHook", lpparam, cfg)
            if (cfg.locationSpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.LocationSpoofHook", lpparam, cfg)
            if (cfg.sensorFakerEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.SensorFakerHook", lpparam, cfg)
            if (cfg.advertisingIdBlockEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.AdvertisingIdHook", lpparam, cfg)
            if (cfg.packageVisibilitySpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.PackageVisibilitySpoofHook", lpparam, cfg)
            if (cfg.networkInfoSpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.NetworkInfoSpoofHook", lpparam, cfg)
            if (cfg.screenMetricsSpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.ScreenMetricsSpoofHook", lpparam, cfg)
            if (cfg.storagePathSpoofEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.StoragePathSpoofHook", lpparam, cfg)
            if (cfg.installStatusSpoofEnabled || cfg.mockLocationSystemLevelEnabled) tryInvokeHook("com.privacyguard.noroot.hooks.PrivacyPlusHook", lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            tryInvokeVoid("com.privacyguard.noroot.utils.CrashGuard", "log", "FATAL: ${e.stackTraceToString()}")
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
        try {
            val reader = Class.forName("com.privacyguard.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? PrivacyConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.privacyguard.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as PrivacyConfig
        } catch (_: Throwable) { PrivacyConfig(packageName = "global") }
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
