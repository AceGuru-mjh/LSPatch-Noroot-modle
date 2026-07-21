package com.audioboost.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.audioboost.noroot.models.AudioConfig

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        const val TAG = "LSP-AudioBoost"
        const val MODULE_PKG = "com.audioboost.noroot"
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
            Log.e(TAG, "AudioBoost NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.audioboost.noroot.ui.UiInitializer")
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
                Class.forName("com.audioboost.noroot.utils.EnvDetector")
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
                try { Class.forName("com.audioboost.noroot.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            val loader = lpparam.classLoader

            if (cfg.tinymixEnabled) tryInvoke("com.audioboost.noroot.hooks.TinymixBridgeHook", "apply", loader, lpparam, cfg)
            if (cfg.volumeBoostEnabled) tryInvoke("com.audioboost.noroot.hooks.VolumeBoostHook", "apply", loader, lpparam, cfg)
            if (cfg.bassBoostEnabled) tryInvoke("com.audioboost.noroot.hooks.BassBoostHook", "apply", loader, lpparam, cfg)
            if (cfg.equalizerEnabled) tryInvoke("com.audioboost.noroot.hooks.EqualizerHook", "apply", loader, lpparam, cfg)
            if (cfg.speakerBoostEnabled) tryInvoke("com.audioboost.noroot.hooks.SpeakerBoostHook", "apply", loader, lpparam, cfg)
            if (cfg.micBoostEnabled) tryInvoke("com.audioboost.noroot.hooks.MicBoostHook", "apply", loader, lpparam, cfg)
            if (cfg.audioQualityEnhanceEnabled) tryInvoke("com.audioboost.noroot.hooks.AudioQualityEnhanceHook", "apply", loader, lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android",
        "com.kuwo.player", "com.netease.cloudmusic.player", "com.spotify.music",
        "com.google.android.apps.youtube.music", "com.tencent.mm",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.miui.player", "com.hihonor.cloudmusic"
    )

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, AudioConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): AudioConfig {
        try {
            val reader = Class.forName("com.audioboost.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? AudioConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.audioboost.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? AudioConfig
                ?: AudioConfig()
        } catch (_: Throwable) {
            return AudioConfig()
        }
    }
}
