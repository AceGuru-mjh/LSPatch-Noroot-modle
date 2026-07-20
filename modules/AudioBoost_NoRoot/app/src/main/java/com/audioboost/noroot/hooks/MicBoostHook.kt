package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 麦克风增益增强Hook（实验性，仅应用层）
 *
 * 硬性限制：
 *  - 仅 Hook AudioRecord Java 层读取 API
 *  - 不修改系统级 AudioRecord 输入增益参数（需 Root，留给 Root 版）
 *  - 不调用 Shizuku 设置系统麦克风增益
 *
 * 实现方式：
 *  - Hook AudioRecord.read(byte[], int, int) / read(short[], int, int)
 *    对读取到的 PCM 样本做整数倍增益（受 micBoostLevel 控制）
 *  - 仅放大 16bit PCM 数据，对其他格式不做处理
 */
object MicBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.micBoostEnabled) return
        LogX.i("麦克风增益启动（实验性，仅应用层） boost=${cfg.micBoostLevel}%")

        hookAudioRecordReadShortArray(lpparam, cfg)
        hookAudioRecordReadByteArray(lpparam, cfg)
    }

    /** Hook AudioRecord.read(short[], int, int) - 16bit PCM 增益 */
    private fun hookAudioRecordReadShortArray(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader) ?: return
            val gain = cfg.micBoostLevel / 100f
            try {
                XposedHelpers.findAndHookMethod(cls, "read",
                    ShortArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val buf = p.args[0] as? ShortArray ?: return
                            val offset = (p.args[1] as? Int) ?: 0
                            val readCount = (p.result as? Int) ?: return
                            if (readCount <= 0) return
                            // 应用增益并防止 16bit 溢出
                            for (i in offset until offset + readCount) {
                                if (i >= buf.size) break
                                val amp = (buf[i].toInt() * gain).toInt()
                                buf[i] = amp.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "read(short[])")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // Android 6+ read(short[], int, int, int) - 带 readMode 的版本
            try {
                XposedHelpers.findAndHookMethod(cls, "read",
                    ShortArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val buf = p.args[0] as? ShortArray ?: return
                            val offset = (p.args[1] as? Int) ?: 0
                            val readCount = (p.result as? Int) ?: return
                            if (readCount <= 0) return
                            for (i in offset until offset + readCount) {
                                if (i >= buf.size) break
                                val amp = (buf[i].toInt() * gain).toInt()
                                buf[i] = amp.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "read(short[],readMode)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioRecord", "read(short[])", e)
        }
    }

    /** Hook AudioRecord.read(byte[], int, int) - 16bit PCM（按字节解析后增益） */
    private fun hookAudioRecordReadByteArray(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader) ?: return
            val gain = cfg.micBoostLevel / 100f
            try {
                XposedHelpers.findAndHookMethod(cls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val buf = p.args[0] as? ByteArray ?: return
                            val offset = (p.args[1] as? Int) ?: 0
                            val readCount = (p.result as? Int) ?: return
                            if (readCount <= 0 || readCount % 2 != 0) return
                            // 仅处理 16bit PCM（2 字节一个样本，小端序）
                            var i = offset
                            val end = offset + readCount
                            while (i + 1 < end) {
                                val lo = buf[i].toInt() and 0xFF
                                val hi = buf[i + 1].toInt() shl 8
                                var sample = (lo or hi).toShort().toInt()
                                sample = (sample * gain).toInt()
                                sample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                buf[i] = (sample and 0xFF).toByte()
                                buf[i + 1] = ((sample shr 8) and 0xFF).toByte()
                                i += 2
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "read(byte[])")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioRecord", "read(byte[])", e)
        }
    }
}
