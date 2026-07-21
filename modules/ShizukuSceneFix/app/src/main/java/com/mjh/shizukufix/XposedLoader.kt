package com.mjh.shizukufix

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.mjh.shizukufix.models.ShizukuFixConfig

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

            try {
                Class.forName("com.mjh.shizukufix.utils.EnvDetector")
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
                try { Class.forName("com.mjh.shizukufix.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            val loader = lpparam.classLoader

            if (pkg == SCENE_PACKAGE) {
                Log.e(TAG, "=== Path A: Hooking Scene process ===")
                if (cfg.sceneFixEnabled) tryInvoke("com.mjh.shizukufix.hooks.ScenePermissionRequesterHook", "apply", loader, lpparam, cfg)
                if (cfg.pmGrantEnabled) tryInvoke("com.mjh.shizukufix.hooks.ShizukuGrantHook", "apply", loader, lpparam, cfg)
                if (cfg.hideFromSceneEnabled) tryInvoke("com.mjh.shizukufix.hooks.HideFromSceneHook", "apply", loader, lpparam, cfg)
                return
            }

            if (isShizukuTarget(pkg)) {
                Log.e(TAG, "=== Path B: Hooking Shizuku process ===")
                if (cfg.variantDetectEnabled) tryInvoke("com.mjh.shizukufix.hooks.ShizukuVariantDetectorHook", "apply", loader, lpparam, cfg)
                if (cfg.listInjectorEnabled) tryInvoke("com.mjh.shizukufix.hooks.ShizukuListInjectorHook", "apply", loader, lpparam, cfg)
                if (cfg.serviceWatchdogEnabled) tryInvoke("com.mjh.shizukufix.hooks.ServiceWatchdogHook", "apply", loader, lpparam, cfg)
                if (cfg.autoGrantHelperEnabled) tryInvoke("com.mjh.shizukufix.hooks.AutoGrantHelperHook", "apply", loader, lpparam, cfg)
                return
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isShizukuTarget(pkg: String): Boolean {
        if (pkg in DEFAULT_SHIZUKU_PACKAGES) return true
        return try {
            Class.forName("com.mjh.shizukufix.hooks.ShizukuVariantDetectorHook")
                .getDeclaredMethod("isShizukuProcess", String::class.java)
                .invoke(null, pkg) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, ShizukuFixConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): ShizukuFixConfig {
        try {
            val reader = Class.forName("com.mjh.shizukufix.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? ShizukuFixConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.mjh.shizukufix.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? ShizukuFixConfig
                ?: ShizukuFixConfig()
        } catch (_: Throwable) {
            return ShizukuFixConfig()
        }
    }
}
