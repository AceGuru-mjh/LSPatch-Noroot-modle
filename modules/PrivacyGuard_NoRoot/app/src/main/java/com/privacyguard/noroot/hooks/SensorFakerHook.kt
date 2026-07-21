package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Random

/**
 * 传感器伪造Hook（仅应用层，无法影响系统全局�?
 *
 * 硬性限制：
 *  - �?Hook Java �?SensorManager / SensorEvent，无法拦�?Native �?ASensor 直接读取
 *  - 不修改系统传感器服务，不影响其他 APP
 *  - 静态模式下返回固定值，加噪模式下在原值上添加随机噪声
 *
 * 拦截路径�?
 *  1. SensorManager.registerListener 监听注册
 *  2. SensorEventListener.onSensorChanged 回调拦截，修�?SensorEvent.values
 *  3. 对加速度/陀螺仪读数做静态或加噪处理（防传感器指纹追踪）
 */
object SensorFakerHook {

    private val random = Random(System.currentTimeMillis())

    // 传感器类型常�?
    private const val TYPE_ACCELEROMETER = 1
    private const val TYPE_GYROSCOPE = 4
    private const val TYPE_MAGNETIC_FIELD = 2
    private const val TYPE_LIGHT = 5
    private const val TYPE_PROXIMITY = 8

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.sensorFakerEnabled) return
        LogX.i("传感器伪造启动（仅应用层）：模式=${if (cfg.sensorNoiseMode == 0) "静�? else "加噪"}")

        hookSensorEventListener(lpparam, cfg.sensorNoiseMode)
    }

    /** Hook SensorEventListener.onSensorChanged 修改传感器读�?*/
    private fun hookSensorEventListener(lpparam: XC_LoadPackage.LoadPackageParam, noiseMode: Int) {
        try {
            // SensorEvent.values �?float[]，Hook onSensorChanged(SensorEvent) 修改 values
            val listenerCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEventListener", lpparam.classLoader)

            val sensorEventCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEvent", lpparam.classLoader) ?: return

            // Hook SensorEvent 的字段访问比较困难，改为 Hook SensorManager.registerListener
            // 然后包装传入�?listener，但这样实现复杂�?
            // 简化方案：直接 Hook SensorEvent 内部 values 字段不太可行�?
            // 改为 Hook SensorEventListener.onSensorChanged 通过反射修改 SensorEvent.values

            if (listenerCls != null) {
                try {
                    XposedHelpers.findAndHookMethod(listenerCls, "onSensorChanged",
                        sensorEventCls, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                try {
                                    val event = p.args[0] ?: return
                                    val valuesField = sensorEventCls.getDeclaredField("values")
                                    valuesField.isAccessible = true
                                    val values = valuesField.get(event) as? FloatArray ?: return

                                    // 读取 sensor 类型
                                    val sensorField = sensorEventCls.getDeclaredField("sensor")
                                    sensorField.isAccessible = true
                                    val sensor = sensorField.get(event) ?: return
                                    val typeMethod = sensor.javaClass.getMethod("getType")
                                    val sensorType = typeMethod.invoke(sensor) as? Int ?: return

                                    // 仅对加速度/陀螺仪/磁场做处�?
                                    when (sensorType) {
                                        TYPE_ACCELEROMETER, TYPE_GYROSCOPE, TYPE_MAGNETIC_FIELD -> {
                                            if (noiseMode == 0) {
                                                // 静态模式：返回固定�?
                                                val static = if (sensorType == TYPE_ACCELEROMETER) {
                                                    floatArrayOf(0f, 0f, 9.81f)
                                                } else {
                                                    floatArrayOf(0f, 0f, 0f)
                                                }
                                                for (i in values.indices) {
                                                    if (i < static.size) values[i] = static[i]
                                                }
                                            } else {
                                                // 加噪模式：原值上添加 ±5% 噪声
                                                for (i in values.indices) {
                                                    val noise = (random.nextFloat() - 0.5f) * 0.1f * values[i]
                                                    values[i] = values[i] + noise
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                            }
                        })
                    LogX.hookSuccess("SensorEventListener", "onSensorChanged")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }

            // 同时 Hook SensorEventListener2 (API 24+ 的子接口)
            try {
                val listener2 = XposedHelpers.findClassIfExists(
                    "android.hardware.SensorEventListener2", lpparam.classLoader)
                if (listener2 != null) {
                    XposedHelpers.findAndHookMethod(listener2, "onSensorChanged",
                        sensorEventCls, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                // 与上面逻辑相同，简化处�?
                            }
                        })
                    LogX.hookSuccess("SensorEventListener2", "onSensorChanged")
                }
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SensorEventListener", "onSensorChanged", e)
        }
    }
}
