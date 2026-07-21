package com.microx.enhancer

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        const val TAG = "LSP-MicroX"
        const val MODULE_PKG = "com.microx.enhancer"
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
            Log.e(TAG, "MicroX Enhancer v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.microx.enhancer.ui.UiInitializer")
                    .getDeclaredMethod("initAllUi", android.content.Context::class.java)
                    .invoke(null, Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null))
            } catch (t: Throwable) {
                Log.e(TAG, "UI init failed: ${t.message}")
            }
            return
        }

        try {
            val pkg = lpparam.packageName ?: return
            val processName = lpparam.processName ?: return

            if (pkg == "android") return

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) {
                try { Class.forName("com.microx.enhancer.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val loader = lpparam.classLoader
            val hHelper = Class.forName("com.microx.enhancer.utils.HookHelper")

            when (pkg) {
                "com.tencent.mm" -> {
                    val isMain = hHelper.getDeclaredMethod("isWeChatMainProcess", String::class.java).invoke(null, processName) as? Boolean ?: false
                    if (isMain) onWeChatLoaded(lpparam, loader)
                }
                "com.tencent.mobileqq" -> {
                    val isMain = hHelper.getDeclaredMethod("isQQMainProcess", String::class.java).invoke(null, processName) as? Boolean ?: false
                    if (isMain) onQQLoaded(lpparam, loader)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun onWeChatLoaded(lpparam: XC_LoadPackage.LoadPackageParam, loader: ClassLoader) {
        Log.e(TAG, "Loading WeChat hooks (integrated=${isIntegratedMode})")
        currentPkg = "com.tencent.mm"

        tryInvokeNoCfg("com.microx.enhancer.hooks.SecurityBypassHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.AdBlockHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.AntiRecallHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.MomentHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.UIModHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.PrivacyHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.BatchManagerHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.AutoReplyHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.VoiceMessageExportHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.MessageSearchEnhanceHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.CustomThemeHook", "hook", loader, lpparam)

        Log.e(TAG, "===== WeChat hooks loaded =====")
    }

    private fun onQQLoaded(lpparam: XC_LoadPackage.LoadPackageParam, loader: ClassLoader) {
        Log.e(TAG, "Loading QQ hooks (integrated=${isIntegratedMode})")
        currentPkg = "com.tencent.mobileqq"

        tryInvokeNoCfg("com.microx.enhancer.hooks.SecurityBypassHook", "hook", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.AdBlockHook", "hookQQ", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.AntiRecallHook", "hookQQ", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.UIModHook", "hookQQ", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.PrivacyHook", "hookQQ", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.AutoReplyHook", "hookQQ", loader, lpparam)
        tryInvokeNoCfg("com.microx.enhancer.hooks.CustomThemeHook", "hookQQ", loader, lpparam)

        Log.e(TAG, "===== QQ hooks loaded =====")
    }

    private fun tryInvokeNoCfg(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }
}
