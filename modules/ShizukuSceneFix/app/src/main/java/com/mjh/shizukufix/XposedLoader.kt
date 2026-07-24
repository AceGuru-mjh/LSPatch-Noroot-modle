package com.mjh.shizukufix

import android.util.Log
import com.mjh.shizukufix.models.ShizukuFixConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.15"
        const val TAG = "LSP-ShizukuFix"
        const val MODULE_PKG = "com.mjh.shizukufix"
        const val SCENE_PACKAGE = "com.omarea.vtools"
        val DEFAULT_SHIZUKU_PACKAGES = setOf("moe.shizuku.privileged.api", "rikka.shizuku.manager")
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
            Log.e(TAG, "ShizukuSceneFix v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.mjh.shizukufix.ui.UiInitializer")
                    .getDeclaredMethod("initAllUi", android.content.Context::class.java)
                    .invoke(null, Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null))
            } catch (t: Throwable) {
                Log.e(TAG, "UI init failed: ${t.message}")
            }
            return
        }

        val pkg = lpparam.packageName ?: return

        if (lpparam.processName != lpparam.packageName) return

        try {
            tryInvokeVoid("com.mjh.shizukufix.utils.CrashGuard", "init", null)
            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            tryInvoke("com.mjh.shizukufix.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.mjh.shizukufix.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()

            if (pkg == SCENE_PACKAGE) {
                Log.e(TAG, "=== Path A: Hooking Scene process ===")
                if (cfg.sceneFixEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.ScenePermissionRequesterHook", lpparam, cfg)
                if (cfg.pmGrantEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.ShizukuGrantHook", lpparam, cfg)
                if (cfg.hideFromSceneEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.HideFromSceneHook", lpparam, cfg)
                return
            }

            if (isShizukuTarget(pkg)) {
                Log.e(TAG, "=== Path B: Hooking Shizuku process ===")
                if (cfg.variantDetectEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.ShizukuVariantDetectorHook", lpparam, cfg)
                if (cfg.listInjectorEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.ShizukuListInjectorHook", lpparam, cfg)
                if (cfg.serviceWatchdogEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.ServiceWatchdogHook", lpparam, cfg)
                if (cfg.autoGrantHelperEnabled) tryInvokeHook("com.mjh.shizukufix.hooks.AutoGrantHelperHook", lpparam, cfg)
                return
            }
        } catch (e: Throwable) {
            tryInvokeVoid("com.mjh.shizukufix.utils.CrashGuard", "log", "FATAL: ${e.stackTraceToString()}")
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isShizukuTarget(pkg: String): Boolean {
        if (pkg in DEFAULT_SHIZUKU_PACKAGES) return true
        return try {
            val clazz = Class.forName("com.mjh.shizukufix.hooks.ShizukuVariantDetectorHook")
            val method = clazz.declaredMethods.firstOrNull { it.name == "isShizukuProcess" && it.parameterCount == 1 }
            method?.invoke(null, pkg) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun loadConfig(): ShizukuFixConfig {
        try {
            val reader = Class.forName("com.mjh.shizukufix.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? ShizukuFixConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.mjh.shizukufix.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as ShizukuFixConfig
        } catch (_: Throwable) { ShizukuFixConfig(packageName = "global") }
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
