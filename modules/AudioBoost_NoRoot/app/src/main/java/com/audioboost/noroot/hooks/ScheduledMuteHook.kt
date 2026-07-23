package com.audioboost.noroot.hooks

import android.media.AudioManager
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import com.audioboost.noroot.utils.LogStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Calendar

object ScheduledMuteHook {

    private var nightModeActive = false
    private var originalVolume: Int? = null
    private var lastCheckTime: Long = 0L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.scheduledMuteEnabled) return
        LogX.i("ScheduledMute: 已启用 定时音量 night=${cfg.nightStartHour}:00-${cfg.nightEndHour}:00 vol=${cfg.nightVolumePercent}%")

        try { LogStore.add("scheduled", "定时静音: ${cfg.nightStartHour}:00-${cfg.nightEndHour}:00 ${cfg.nightVolumePercent}%") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookAudioManagerSetStreamVolume(lpparam, cfg)
    }

    private fun hookAudioManagerSetStreamVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(cls, "setStreamVolume",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val stream = p.args[0] as? Int ?: return
                        if (stream != AudioManager.STREAM_MUSIC) return
                        val volume = p.args[1] as? Int ?: return

                        updateNightMode(cfg)

                        if (nightModeActive) {
                            val maxVol = try {
                                val am = XposedHelpers.callMethod(
                                    p.thisObject, "getStreamMaxVolume", stream
                                ) as? Int
                            } catch (_: Throwable) { null } ?: 15

                            val nightVol = (maxVol * cfg.nightVolumePercent / 100).coerceAtLeast(0)
                            if (volume > nightVol) {
                                if (originalVolume == null) originalVolume = volume
                                p.args[1] = nightVol
                                LogX.d("ScheduledMute: 夜间模式 ${cfg.nightVolumePercent}% vol=$volume -> $nightVol")
                            }
                        } else {
                            originalVolume?.let {
                                if (volume == 0 || (lastCheckTime == 0L)) {
                                    p.args[1] = it
                                    LogX.d("ScheduledMute: 日间模式 恢复音量=${it}")
                                    originalVolume = null
                                }
                            }
                        }
                        lastCheckTime = System.currentTimeMillis()
                    }
                })
            LogX.hookSuccess("AudioManager", "setStreamVolume")
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "setStreamVolume", e)
        }
    }

    private fun updateNightMode(cfg: AudioConfig) {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < 60000) return

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val nightStart = cfg.nightStartHour.coerceIn(0, 23)
        val nightEnd = cfg.nightEndHour.coerceIn(0, 23)

        nightModeActive = if (nightStart < nightEnd) {
            hour in nightStart until nightEnd
        } else {
            hour >= nightStart || hour < nightEnd
        }

        if (nightModeActive && originalVolume == null) {
            LogX.i("ScheduledMute: 进入夜间模式 (${hour}h)")
        } else if (!nightModeActive && originalVolume != null) {
            LogX.i("ScheduledMute: 退出夜间模式 (${hour}h)")
            originalVolume = null
        }
    }
}
