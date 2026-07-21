package com.batteryopt.noroot.hooks

import android.os.Build
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * 【实验性】振动器限频 Hook（应用层�?
 *
 * 功能�?
 *  - Hook Vibrator.vibrate 系列方法
 *  - 对高频振动按调用来源节流（默认最小间�?1000ms�?
 *  - 减少 APP 频繁振动反馈导致的电量消�?
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP 的振动请�?
 *  - 不能修改系统 VibratorService 全局策略
 *  - 不影响系统级振动（如来电、闹钟）
 *
 * 注意�?
 *  - 游戏�?APP 可能依赖振动反馈，开启本功能会影响体�?
 *  - 默认关闭，建议仅在确认无频繁振动需求时启用
 */
object VibratorThrottleHook {

    /** 记录上次振动时间 */
    private val lastVibrateTs = ConcurrentHashMap<String, Long>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】振动器限频启动 | 最小间�?${cfg.vibratorMinIntervalMs}ms")

        hookVibrateMillis(lpparam, cfg)
        hookVibratePattern(lpparam, cfg)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hookVibrateVibrationEffect(lpparam, cfg)
        }
    }

    /** Hook vibrate(long millis) / vibrate(long millis, AudioAttributes) API26+ 已废弃但部分APP仍调�?*/
    private fun hookVibrateMillis(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.os.Vibrator", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                java.lang.Long.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_long", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(long) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(long)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook vibrate(long[] pattern, int repeat) */
    private fun hookVibratePattern(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.os.Vibrator", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                LongArray::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_pattern", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(pattern,repeat) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(long[],int)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook vibrate(VibrationEffect) API26+ */
    private fun hookVibrateVibrationEffect(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.os.Vibrator", lpparam.classLoader
        ) ?: return
        val effectCls = XposedHelpers.findClassIfExists(
            "android.os.VibrationEffect", lpparam.classLoader
        ) ?: return

        // vibrate(VibrationEffect)
        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                "android.os.VibrationEffect",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_effect", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(VibrationEffect) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(VibrationEffect)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // vibrate(VibrationEffect, AudioAttributes)
        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                "android.os.VibrationEffect",
                "android.media.AudioAttributes",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_effect_aa", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(VibrationEffect,AudioAttributes) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(VibrationEffect,AudioAttributes)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun shouldThrottle(key: String, cfg: BatteryConfig): Boolean {
        val now = System.currentTimeMillis()
        val last = lastVibrateTs[key] ?: 0L
        return if (now - last < cfg.vibratorMinIntervalMs) {
            true
        } else {
            lastVibrateTs[key] = now
            false
        }
    }
}
