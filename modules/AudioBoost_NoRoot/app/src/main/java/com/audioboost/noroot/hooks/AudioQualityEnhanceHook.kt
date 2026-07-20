package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 音质增强Hook（实验性，仅应用层）
 *
 * 硬性限制：
 *  - 仅 Hook MediaFormat / MediaCodec 配置 API
 *  - 不修改系统级音频输出链路
 *  - 提升后的实际音质取决于硬件能力，部分设备可能无变化
 *
 * 实现方式：
 *  1. Hook MediaFormat.KEY_SAMPLE_RATE 设置 - 强制提升到目标采样率
 *  2. Hook MediaFormat.KEY_PCM_ENCODING 设置 - 强制提升到目标位深（16bit->24bit/32bit）
 *  3. Hook AudioRecord.Builder.setSampleRate - 强制提升录音采样率
 *
 * 注意：仅适用于解码后 PCM 输出场景，对编码场景保持原值。
 */
object AudioQualityEnhanceHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.audioQualityEnhanceEnabled) return
        LogX.i("音质增强启动（实验性） sampleRate=${cfg.targetSampleRate} bitDepth=${cfg.targetBitDepth}")

        hookMediaFormatSetInteger(lpparam, cfg)
        hookAudioRecordBuilderSetSampleRate(lpparam, cfg)
    }

    /**
     * Hook MediaFormat.setInteger(String, int)
     * 拦截 KEY_SAMPLE_RATE 和 KEY_PCM_ENCODING，替换为更高值
     */
    private fun hookMediaFormatSetInteger(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.MediaFormat", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setInteger",
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            val v = (p.args[1] as? Int) ?: return
                            when (key) {
                                // 采样率：仅当目标高于原值时替换
                                "sample-rate" -> {
                                    if (v < cfg.targetSampleRate) {
                                        p.args[1] = cfg.targetSampleRate
                                        LogX.d("MediaFormat sample-rate: $v -> ${cfg.targetSampleRate}")
                                    }
                                }
                                // PCM 位深编码
                                "pcm-encoding" -> {
                                    // 2 = 16bit, 3 = 24bit packed, 4 = 32bit float
                                    val targetEnc = when (cfg.targetBitDepth) {
                                        24 -> 3
                                        32 -> 4
                                        else -> 2  // 16bit
                                    }
                                    if (v < targetEnc) {
                                        p.args[1] = targetEnc
                                        LogX.d("MediaFormat pcm-encoding: $v -> $targetEnc")
                                    }
                                }
                            }
                        }
                    })
                LogX.hookSuccess("MediaFormat", "setInteger")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("MediaFormat", "setInteger", e)
        }
    }

    /** Hook AudioRecord.Builder.setSampleRate 强制提升录音采样率 */
    private fun hookAudioRecordBuilderSetSampleRate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord.Builder", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setSampleRate",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = (p.args[0] as? Int) ?: return
                            if (v < cfg.targetSampleRate) {
                                p.args[0] = cfg.targetSampleRate
                                LogX.d("AudioRecord.Builder sampleRate: $v -> ${cfg.targetSampleRate}")
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord.Builder", "setSampleRate")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioRecord.Builder", "setSampleRate", e)
        }
    }
}
