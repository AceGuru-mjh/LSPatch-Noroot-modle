package com.audioboost.noroot.hooks

import android.media.AudioManager
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject

object PerAppVolumeHook {

    private val volumeStore: MutableMap<String, Int> = mutableMapOf()
    private var lastPackage: String? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.perAppVolumeEnabled) return
        LogX.i("PerAppVolume: 已启用 per-app音量配置")

        val profiles = parseProfiles(cfg.volumeProfiles)
        val streamTypes = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING
        )

        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader
            ) ?: return

            for (streamType in streamTypes) {
                try {
                    XposedHelpers.findAndHookMethod(cls, "setStreamVolume",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val stream = p.args[0] as? Int ?: return
                                val volume = p.args[1] as? Int ?: return
                                val pkg = lpparam.packageName ?: return

                                val targetVolume = profiles[pkg] ?: return
                                if (stream == AudioManager.STREAM_MUSIC) {
                                    volumeStore[pkg] = targetVolume
                                    p.args[1] = targetVolume
                                    LogX.d("PerAppVolume: $pkg STREAM_MUSIC -> $targetVolume")
                                }
                            }

                            override fun afterHookedMethod(p: MethodHookParam) {
                                val pkg = lpparam.packageName ?: return
                                if (lastPackage != pkg) {
                                    volumeStore[lastPackage ?: ""]?.let { stored ->
                                        LogX.d("PerAppVolume: restore $lastPackage volume=$stored")
                                    }
                                    lastPackage = pkg
                                }
                            }
                        })
                    LogX.hookSuccess("AudioManager", "setStreamVolume(stream=$streamType)")
                } catch (e: Exception) { LogX.w("PerAppVolume: setStreamVolume $streamType 异常: ${e.message}") }
            }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "setStreamVolume", e)
        }
    }

    private fun parseProfiles(raw: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (entry in raw) {
            try {
                val obj = JSONObject(entry)
                val pkg = obj.optString("pkg", "")
                val vol = obj.optInt("volume", -1)
                if (pkg.isNotEmpty() && vol in 0..100) {
                    map[pkg] = vol
                }
            } catch (e: Exception) { LogX.w("volumeProfiles 解析失败: ${e.message}") }
        }
        return map
    }
}
