package com.audioboost.noroot

import android.util.Log
import com.audioboost.noroot.hooks.*
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.EnvDetector
import com.audioboost.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        const val TAG = "LSP-AudioBoost"
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
        Log.e(TAG, "handleLoadPackage entered: pkg=${lpparam.packageName}")

        if (lpparam.processName != lpparam.packageName) return

        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            EnvDetector.detect(lpparam)

            val cfg = loadConfig()
            if (!cfg.masterEnabled) {
                Log.e(TAG, "Master disabled, skipping hooks")
                return
            }

            Log.e(TAG, "Loading TinymixBridgeHook...")
            try { if (cfg.tinymixEnabled) TinymixBridgeHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "TinymixBridgeHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading VolumeBoostHook...")
            try { if (cfg.volumeBoostEnabled) VolumeBoostHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "VolumeBoostHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BassBoostHook...")
            try { if (cfg.bassBoostEnabled) BassBoostHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BassBoostHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading EqualizerHook...")
            try { if (cfg.equalizerEnabled) EqualizerHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "EqualizerHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading SpeakerBoostHook...")
            try { if (cfg.speakerBoostEnabled) SpeakerBoostHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "SpeakerBoostHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading MicBoostHook...")
            try { if (cfg.micBoostEnabled) MicBoostHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "MicBoostHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AudioQualityEnhanceHook...")
            try { if (cfg.audioQualityEnhanceEnabled) AudioQualityEnhanceHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AudioQualityEnhanceHook FAIL: ${e.message}") }

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

    private fun loadConfig(): AudioConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.audioboost.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { AudioConfig(packageName = "global") }
    }
}
