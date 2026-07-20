package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 均衡器Hook（仅应用层）
 *
 * 硬性限制：
 *  - 仅 Hook AudioEffect.Equalizer Java 层 API
 *  - 不修改系统级 AudioFlinger 均衡器配置
 *
 * 拦截路径：
 *  1. Equalizer.setBandLevel(short, short) - 替换 bandLevel 为配置的目标增益
 *  2. Equalizer.getBandLevel(short) - 返回伪造值
 *  3. Equalizer.setBandLevels(short[], short[]) - 批量设置
 *  4. Equalizer.getNumberOfBands() - 至少返回 5 段（适配用户配置）
 */
object EqualizerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.equalizerEnabled) return
        LogX.i("均衡器启动 bands=${cfg.eqBands}")

        hookEqualizerSetBandLevel(lpparam, cfg)
        hookEqualizerGetBandLevel(lpparam, cfg)
        hookEqualizerSetBandLevels(lpparam, cfg)
        hookEqualizerGetNumberOfBands(lpparam, cfg)
    }

    /** Hook Equalizer.setBandLevel(short band, short level) */
    private fun hookEqualizerSetBandLevel(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.Equalizer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setBandLevel",
                    Short::class.javaPrimitiveType, Short::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val band = (p.args[0] as? Short)?.toInt() ?: return
                            val targetLevel = cfg.eqBands.getOrNull(band)?.toShort() ?: return
                            p.args[1] = targetLevel
                            LogX.d("Equalizer.setBandLevel band=$band -> $targetLevel")
                        }
                    })
                LogX.hookSuccess("Equalizer", "setBandLevel")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Equalizer", "setBandLevel", e)
        }
    }

    /** Hook Equalizer.getBandLevel(short) 返回伪造值 */
    private fun hookEqualizerGetBandLevel(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.Equalizer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getBandLevel",
                    Short::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val band = (p.args[0] as? Short)?.toInt() ?: return
                            val targetLevel = cfg.eqBands.getOrNull(band)?.toShort() ?: return
                            p.result = targetLevel
                        }
                    })
                LogX.hookSuccess("Equalizer", "getBandLevel")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Equalizer", "getBandLevel", e)
        }
    }

    /** Hook Equalizer.setBandLevels(short[], short[]) 批量设置（部分厂商私有API） */
    private fun hookEqualizerSetBandLevels(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.Equalizer", lpparam.classLoader) ?: return
            try {
                // 反射查找带数组参数的私有方法（部分厂商实现）
                val m = XposedHelpers.findMethodExactIfExists(
                    cls, "setBandLevels",
                    ShortArray::class.java, ShortArray::class.java) ?: return
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val bands = p.args[0] as? ShortArray ?: return
                        val levels = p.args[1] as? ShortArray ?: return
                        // 用配置覆盖 levels
                        for (i in bands.indices) {
                            val b = bands[i].toInt()
                            cfg.eqBands.getOrNull(b)?.let { target ->
                                if (i < levels.size) levels[i] = target.toShort()
                            }
                        }
                    }
                })
                LogX.hookSuccess("Equalizer", "setBandLevels")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Equalizer", "setBandLevels", e)
        }
    }

    /** Hook Equalizer.getNumberOfBands() 至少返回 5 段 */
    private fun hookEqualizerGetNumberOfBands(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.Equalizer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getNumberOfBands", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val cur = (p.result as? Short)?.toInt() ?: 0
                        val desired = maxOf(cfg.eqBands.size, 5)
                        if (cur < desired) p.result = desired.toShort()
                    }
                })
                LogX.hookSuccess("Equalizer", "getNumberOfBands")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Equalizer", "getNumberOfBands", e)
        }
    }
}
