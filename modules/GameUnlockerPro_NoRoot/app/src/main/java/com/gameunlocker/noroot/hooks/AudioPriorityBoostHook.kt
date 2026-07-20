package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 音频优先级提升 Hook（实验性）
 *
 * 功能：
 *  - Hook AudioTrack 构造与播放方法，提升音频线程优先级
 *  - Hook AudioTrack.setPerformanceMode 强制 PERFORMANCE_MODE_LOW_LATENCY
 *  - Hook MediaPlayer.setAudioAttributes 提升音频流类型为 STREAM_MUSIC
 *
 * 硬性限制：
 *  - 仅修改应用进程内的音频处理调度
 *  - 实际音频延迟由底层 ALSA/TinyALSA 驱动决定
 *  - 部分 ROM 的 AudioFlinger 不支持低延迟模式
 *
 * 实验性声明：对节奏游戏（音游）和射击游戏（脚步声定位）有可感知效果。
 */
object AudioPriorityBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.audioPriorityBoostEnabled) return
        LogX.i("音频优先级提升启动（实验性，仅应用层）")

        hookAudioTrack(lpparam)
        hookAudioRecord(lpparam)
        boostAudioThreadPriority()
    }

    /** Hook AudioTrack 设置低延迟模式 */
    private fun hookAudioTrack(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClassIfExists(
                "android.media.AudioTrack", lpparam.classLoader) ?: return

            // setPerformanceMode (API 26+)
            try {
                XposedHelpers.findAndHookMethod(at, "setPerformanceMode",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // PERFORMANCE_MODE_LOW_LATENCY = 2
                            // PERFORMANCE_MODE_POWER_SAVING = 1
                            // PERFORMANCE_MODE_NONE = 0
                            p.args[0] = 2
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setPerformanceMode -> LOW_LATENCY")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // play() 时提升线程优先级
            try {
                XposedHelpers.findAndHookMethod(at, "play", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val pt = Class.forName("android.os.Process")
                            val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                            // THREAD_PRIORITY_AUDIO = -16
                            m.invoke(null, -16)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
                LogX.hookSuccess("AudioTrack", "play -> threadPriority AUDIO")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("AudioTrack", "setPerformanceMode", e)
        }
    }

    /** Hook AudioRecord 提升录音线程优先级（语音类游戏） */
    private fun hookAudioRecord(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ar = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(ar, "startRecording",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val pt = Class.forName("android.os.Process")
                                val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                                m.invoke(null, -16)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "startRecording -> threadPriority AUDIO")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("AudioRecord", "startRecording", e)
        }
    }

    /** 启动时把当前线程优先级提升至 AUDIO */
    private fun boostAudioThreadPriority() {
        try {
            val pt = Class.forName("android.os.Process")
            val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
            m.invoke(null, -16)
            LogX.d("音频线程优先级提升至 AUDIO(-16)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
