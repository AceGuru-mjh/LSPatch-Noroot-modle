package com.mjh.shizukufix

import android.util.Log
import com.mjh.shizukufix.core.ConfigClient
import com.mjh.shizukufix.hooks.AutoGrantHelperHook
import com.mjh.shizukufix.hooks.HideFromSceneHook
import com.mjh.shizukufix.hooks.ScenePermissionRequesterHook
import com.mjh.shizukufix.hooks.ServiceWatchdogHook
import com.mjh.shizukufix.hooks.ShizukuGrantHook
import com.mjh.shizukufix.hooks.ShizukuListInjectorHook
import com.mjh.shizukufix.hooks.ShizukuVariantDetectorHook
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.EnvDetector
import com.mjh.shizukufix.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
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

            if (pkg == SCENE_PACKAGE) {
                Log.e(TAG, "=== Path A: Hooking Scene process ===")
                try { if (cfg.sceneFixEnabled) ScenePermissionRequesterHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ScenePermissionRequesterHook FAIL: ${e.message}") }
                try { if (cfg.pmGrantEnabled) ShizukuGrantHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuGrantHook FAIL: ${e.message}") }
                try { if (cfg.hideFromSceneEnabled) HideFromSceneHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "HideFromSceneHook FAIL: ${e.message}") }
                return
            }

            if (isShizukuTarget(pkg)) {
                Log.e(TAG, "=== Path B: Hooking Shizuku process ===")
                try { if (cfg.variantDetectEnabled) ShizukuVariantDetectorHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuVariantDetectorHook FAIL: ${e.message}") }
                try { if (cfg.listInjectorEnabled) ShizukuListInjectorHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuListInjectorHook FAIL: ${e.message}") }
                try { if (cfg.serviceWatchdogEnabled) ServiceWatchdogHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ServiceWatchdogHook FAIL: ${e.message}") }
                try { if (cfg.autoGrantHelperEnabled) AutoGrantHelperHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AutoGrantHelperHook FAIL: ${e.message}") }
                return
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isShizukuTarget(pkg: String): Boolean {
        if (pkg in DEFAULT_SHIZUKU_PACKAGES) return true
        return ShizukuVariantDetectorHook.isShizukuProcess(pkg)
    }

    private fun loadConfig(): ShizukuFixConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.mjh.shizukufix.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { ShizukuFixConfig(packageName = "global") }
    }
}
