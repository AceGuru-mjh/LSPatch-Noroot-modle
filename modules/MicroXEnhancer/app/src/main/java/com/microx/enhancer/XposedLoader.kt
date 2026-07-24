package com.microx.enhancer

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.14"
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

        if (lpparam.processName != lpparam.packageName) return

        try {
            tryInvokeVoid("com.microx.enhancer.utils.CrashGuard", "init", null)
            val pkg = lpparam.packageName ?: return
            val processName = lpparam.processName ?: return

            if (pkg == "android") return
            if (!lpparam.isFirstApplication) return

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.microx.enhancer.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            when (pkg) {
                "com.tencent.mm" -> {
                    val isMain = tryInvokeBool("com.microx.enhancer.utils.HookHelper", "isWeChatMainProcess", processName)
                    if (isMain == true) onWeChatLoaded(lpparam)
                }
                "com.tencent.mobileqq" -> {
                    val isMain = tryInvokeBool("com.microx.enhancer.utils.HookHelper", "isQQMainProcess", processName)
                    if (isMain == true) onQQLoaded(lpparam)
                }
            }
        } catch (e: Throwable) {
            tryInvokeVoid("com.microx.enhancer.utils.CrashGuard", "log", "FATAL: ${e.stackTraceToString()}")
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun onWeChatLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "Loading WeChat hooks (integrated=${isIntegratedMode})")
        currentPkg = "com.tencent.mm"

        val hooks = listOf(
            "com.microx.enhancer.hooks.SecurityBypassHook" to "hook",
            "com.microx.enhancer.hooks.AdBlockHook" to "hook",
            "com.microx.enhancer.hooks.AntiRecallHook" to "hook",
            "com.microx.enhancer.hooks.MomentHook" to "hook",
            "com.microx.enhancer.hooks.UIModHook" to "hook",
            "com.microx.enhancer.hooks.PrivacyHook" to "hook",
            "com.microx.enhancer.hooks.BatchManagerHook" to "hook",
            "com.microx.enhancer.hooks.AutoReplyHook" to "hook",
            "com.microx.enhancer.hooks.VoiceMessageExportHook" to "hook",
            "com.microx.enhancer.hooks.MessageSearchEnhanceHook" to "hook",
            "com.microx.enhancer.hooks.CustomThemeHook" to "hook"
        )
        for ((className, methodName) in hooks) {
            tryInvokeHook0(className, methodName, lpparam)
        }
        Log.e(TAG, "===== WeChat hooks loaded =====")
    }

    private fun onQQLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "Loading QQ hooks (integrated=${isIntegratedMode})")
        currentPkg = "com.tencent.mobileqq"

        val hooks = listOf(
            "com.microx.enhancer.hooks.SecurityBypassHook" to "hook",
            "com.microx.enhancer.hooks.AdBlockHook" to "hookQQ",
            "com.microx.enhancer.hooks.AntiRecallHook" to "hookQQ",
            "com.microx.enhancer.hooks.UIModHook" to "hookQQ",
            "com.microx.enhancer.hooks.PrivacyHook" to "hookQQ",
            "com.microx.enhancer.hooks.AutoReplyHook" to "hookQQ",
            "com.microx.enhancer.hooks.CustomThemeHook" to "hookQQ"
        )
        for ((className, methodName) in hooks) {
            tryInvokeHook0(className, methodName, lpparam)
        }
        Log.e(TAG, "===== QQ hooks loaded =====")
    }

    private fun tryInvokeHook0(className: String, methodName: String, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            method?.invoke(null, lpparam)
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

    private fun tryInvokeBool(className: String, methodName: String, arg: String): Boolean? {
        return try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            method?.invoke(null, arg) as? Boolean
        } catch (_: Throwable) { null }
    }
}
