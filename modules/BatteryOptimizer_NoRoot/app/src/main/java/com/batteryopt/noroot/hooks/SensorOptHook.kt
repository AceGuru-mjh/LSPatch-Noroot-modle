package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SensorManager 传感器降�?Hook（应用层�?
 *
 * 功能�?
 *  - Hook registerListener，对高频传感器（>50Hz）降频到合理�?
 *  - 默认上限 200000us = 5Hz
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP 的传感器注册请求
 *  - AR/VR/运动�?APP 需要高频传感器，建议关闭此优化避免功能受损
 */
object SensorOptHook {

    /** 高频阈值（微秒）：低于此值视为高频，需要降频�?0Hz = 20000us */
    private const val HIGH_FREQ_THRESHOLD_US = 20_000

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Sensor 传感器优化启�?| 上限=${cfg.sensorMaxRateUs}us")

        hookRegisterListener(lpparam, cfg)
    }

    private fun hookRegisterListener(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        val smCls = XposedHelpers.findClassIfExists(
            "android.hardware.SensorManager", lpparam.classLoader
        ) ?: return

        // 重载1: registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs)
        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降�? ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(3�?")
        } catch (e: Exception) {
            LogX.e("Hook registerListener(3�? 异常", e)
        }

        // 重载2: registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs, int maxReportLatencyUs)
        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降�?带延�?: ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(4�?")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }

        // 重载3: registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs, Handler handler)
        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                "android.os.Handler",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降�?带Handler): ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(带Handler)")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
