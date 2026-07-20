package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 低音增强Hook（仅应用层）
 *
 * 硬性限制：
 *  - 仅 Hook AudioEffect.BassBoost Java 层 API
 *  - 不修改系统级 AudioFlinger 音效链
 *  - 增益仅在当前进程生命周期内有效
 *
 * 拦截路径：
 *  1. BassBoost.setStrength(short) - 强制设置高 strength（0~1000）
 *  2. BassBoost.getStrength() - 返回伪造的高 strength（防止APP覆盖回原值）
 *  3. BassBoost.setStrengthSupported(boolean) - 强制设为 true（部分厂商魔改）
 */
object BassBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.bassBoostEnabled) return
        LogX.i("低音增强启动 bassLevel=${cfg.bassLevel}%")

        hookBassBoostSetStrength(lpparam, cfg)
        hookBassBoostGetStrength(lpparam, cfg)
        hookBassBoostSetStrengthSupported(lpparam)
    }

    /** Hook BassBoost.setStrength(short) 强制设为目标值 */
    private fun hookBassBoostSetStrength(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            // BassBoost 是 AudioEffect 的内部静态子类
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.BassBoost", lpparam.classLoader) ?: return
            // 目标 strength 范围 0~1000，bassLevel 0~100 映射到 0~1000
            val targetStrength = (cfg.bassLevel * 10).toShort()
            try {
                XposedHelpers.findAndHookMethod(cls, "setStrength",
                    Short::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 替换传入值为目标值（忽略APP调用方传入）
                            p.args[0] = targetStrength
                            LogX.d("BassBoost.setStrength -> $targetStrength")
                        }
                    })
                LogX.hookSuccess("BassBoost", "setStrength")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("BassBoost", "setStrength", e)
        }
    }

    /** Hook BassBoost.getStrength() 返回伪造值 */
    private fun hookBassBoostGetStrength(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.BassBoost", lpparam.classLoader) ?: return
            val targetStrength = (cfg.bassLevel * 10).toShort()
            try {
                XposedHelpers.findAndHookMethod(cls, "getStrength", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = targetStrength
                    }
                })
                LogX.hookSuccess("BassBoost", "getStrength")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("BassBoost", "getStrength", e)
        }
    }

    /** Hook BassBoost.setStrengthSupported 强制返回成功（部分厂商私有API） */
    private fun hookBassBoostSetStrengthSupported(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.BassBoost", lpparam.classLoader) ?: return
            // getStrengthSupported() 强制返回 true
            try {
                XposedHelpers.findAndHookMethod(cls, "getStrengthSupported", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = true
                    }
                })
                LogX.hookSuccess("BassBoost", "getStrengthSupported")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("BassBoost", "getStrengthSupported", e)
        }
    }
}
