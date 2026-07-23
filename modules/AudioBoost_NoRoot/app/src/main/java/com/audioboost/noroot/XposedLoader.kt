package com.audioboost.noroot

import android.util.Log
import com.audioboost.noroot.core.ConfigClient
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

            Log.e(TAG, "Loading PerAppVolumeHook...")
            try { if (cfg.perAppVolumeEnabled) PerAppVolumeHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "PerAppVolumeHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading HeadphoneProfileHook...")
            try { if (cfg.headphoneAutoSwitchEnabled) HeadphoneProfileHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "HeadphoneProfileHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ScheduledMuteHook...")
            try { if (cfg.scheduledMuteEnabled) ScheduledMuteHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ScheduledMuteHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BluetoothDeviceEqHook...")
            try { if (cfg.btDeviceEqEnabled) BluetoothDeviceEqHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BluetoothDeviceEqHook FAIL: ${e.message}") }

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
