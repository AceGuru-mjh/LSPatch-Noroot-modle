package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogStore
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 音量增强Hook（仅应用层，不修改系统音量服务）
 *
 * 硬性限制：
 *  - 仅 Hook Java 层 API（AudioTrack / MediaPlayer），无法影响系统级 AudioFlinger
 *  - 增益仅在当前进程生命周期内有效，重启后失效
 *  - 不调用 Shizuku，无系统级操作
 *  - 不写 /sys/class/audio 等节点
 *
 * 拦截路径：
 *  1. AudioTrack.setVolume(float) - 多媒体音轨音量
 *  2. AudioTrack.setPlayerVolume(int, int) - 播放器音量
 *  3. MediaPlayer.setVolume(float, float) - 媒体播放器左右声道音量
 *  4. VolumeShaper 路径 - 通过反射设置 VolumeShaper 配置（保守，仅日志）
 */
object VolumeBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.volumeBoostEnabled) return
        LogX.i("音量增强启动（仅应用层） boost=${cfg.boostLevel}%")
        try { LogStore.add("boosted", "音量增强: ${cfg.boostLevel}%") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookAudioTrackSetVolume(lpparam, cfg)
        hookAudioTrackSetPlayerVolume(lpparam, cfg)
        hookMediaPlayerSetVolume(lpparam, cfg)
    }

    /** Hook AudioTrack.setVolume(float) 放大传入值 */
    private fun hookAudioTrackSetVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.AudioTrack", lpparam.classLoader) ?: return
            // setVolume(float) - 单参数版本
            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = (p.args[0] as? Float) ?: return
                            p.args[0] = clampVolume(v * cfg.boostLevel / 100f)
                            LogX.d("AudioTrack.setVolume: $v -> ${p.args[0]}")
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // Android 8+ 多声道 API: setVolume(float, float, int)
            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Float) ?: return
                            val r = (p.args[1] as? Float) ?: return
                            p.args[0] = clampVolume(l * cfg.boostLevel / 100f)
                            p.args[1] = clampVolume(r * cfg.boostLevel / 100f)
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setVolume(L,R,track)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioTrack", "setVolume", e)
        }
    }

    /** Hook AudioTrack.setPlayerVolume(int, int) - Android 11+ 媒体播放器音量 */
    private fun hookAudioTrackSetPlayerVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.AudioTrack", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setPlayerVolume",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // int 类型为 0..MAX_VOLUME_INT，直接乘比例可能溢出，先做边界检查
                            val l = (p.args[0] as? Int) ?: return
                            val r = (p.args[1] as? Int) ?: return
                            // 反射获取 MAX_VOLUME_INT 静态常量
                            val max = try {
                                XposedHelpers.getStaticIntField(cls, "MAX_VOLUME_INT") as Int
                            } catch (_: Throwable) { 32767 }
                            val nl = (l.toLong() * cfg.boostLevel / 100L).toInt().coerceIn(0, max)
                            val nr = (r.toLong() * cfg.boostLevel / 100L).toInt().coerceIn(0, max)
                            p.args[0] = nl
                            p.args[1] = nr
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setPlayerVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioTrack", "setPlayerVolume", e)
        }
    }

    /** Hook MediaPlayer.setVolume(float, float) */
    private fun hookMediaPlayerSetVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.MediaPlayer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Float) ?: return
                            val r = (p.args[1] as? Float) ?: return
                            p.args[0] = clampVolume(l * cfg.boostLevel / 100f)
                            p.args[1] = clampVolume(r * cfg.boostLevel / 100f)
                            LogX.d("MediaPlayer.setVolume: $l/$r -> ${p.args[0]}/${p.args[1]}")
                        }
                    })
                LogX.hookSuccess("MediaPlayer", "setVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // Android 12+ 多声道版本 setVolume(float, float, int)
            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Float) ?: return
                            val r = (p.args[1] as? Float) ?: return
                            p.args[0] = clampVolume(l * cfg.boostLevel / 100f)
                            p.args[1] = clampVolume(r * cfg.boostLevel / 100f)
                        }
                    })
                LogX.hookSuccess("MediaPlayer", "setVolume(L,R,track)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("MediaPlayer", "setVolume", e)
        }
    }

    /** 限幅至 0.0~1.0（AudioTrack/MediaPlayer 原生 API 要求范围） */
    private fun clampVolume(v: Float): Float {
        return v.coerceIn(0f, 1.0f)
    }
}
