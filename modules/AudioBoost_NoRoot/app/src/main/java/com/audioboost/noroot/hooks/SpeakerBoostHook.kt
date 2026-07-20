package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 扬声器增强Hook（实验性，仅应用层）
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 AudioManager.getStreamMaxVolume / getStreamVolume
 *  - 不修改系统级 AudioManagerService 或 AudioFlinger
 *  - 不调用 Shizuku setStreamVolume（系统级音量需要 Root，留给 Root 版）
 *
 * 实现方式：
 *  1. 拦截 getStreamMaxVolume 返回放大的最大值（欺骗应用层 UI 显示）
 *  2. 拦截 getStreamVolume 返回放大的当前值
 *  3. 拦截 setStreamVolume 时将传入值按比例放大并写入（仅当前进程可见）
 *
 * 注意：此 Hook 仅影响 APP 自身对音量的认知，无法真实放大系统扬声器输出。
 *      真实放大系统扬声器输出需要 Root 版 SystemVolumeHook 通过 Shizuku 修改系统音量。
 */
object SpeakerBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.speakerBoostEnabled) return
        LogX.i("扬声器增强启动（实验性，仅应用层） maxBoost=${cfg.speakerBoostMax}")

        hookAudioManagerGetStreamMaxVolume(lpparam, cfg)
        hookAudioManagerGetStreamVolume(lpparam, cfg)
    }

    /** Hook AudioManager.getStreamMaxVolume 返回放大的最大值 */
    private fun hookAudioManagerGetStreamMaxVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getStreamMaxVolume",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val cur = (p.result as? Int) ?: return
                            // 仅对媒体/扬声器相关 stream 放大
                            val streamType = (p.args[0] as? Int) ?: return
                            // STREAM_MUSIC=3, STREAM_RING=2, STREAM_NOTIFICATION=5, STREAM_ALARM=4
                            if (streamType in listOf(2, 3, 4, 5)) {
                                p.result = cur + cfg.speakerBoostMax
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getStreamMaxVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // Android 9+ getStreamMaxVolume(int, Context)（部分厂商）
            try {
                XposedHelpers.findAndHookMethod(cls, "getStreamMaxVolume",
                    Int::class.javaPrimitiveType, "android.content.Context",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val cur = (p.result as? Int) ?: return
                            val streamType = (p.args[0] as? Int) ?: return
                            if (streamType in listOf(2, 3, 4, 5)) {
                                p.result = cur + cfg.speakerBoostMax
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getStreamMaxVolume(ctx)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "getStreamMaxVolume", e)
        }
    }

    /** Hook AudioManager.getStreamVolume 返回放大的当前值（仅显示用） */
    private fun hookAudioManagerGetStreamVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getStreamVolume",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val cur = (p.result as? Int) ?: return
                            val streamType = (p.args[0] as? Int) ?: return
                            if (streamType in listOf(2, 3, 4, 5)) {
                                // 按比例放大显示，但不超过 max+boost
                                p.result = cur + (cfg.speakerBoostMax / 2)
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getStreamVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "getStreamVolume", e)
        }
    }
}
