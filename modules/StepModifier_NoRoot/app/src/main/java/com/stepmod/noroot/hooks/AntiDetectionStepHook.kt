package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogStore
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

object AntiDetectionStepHook {

    private val random = Random(System.currentTimeMillis())
    private var baseSteps = 0
    private var currentFluctuation = 0
    private var isResting = false
    private var restUntilMs = 0L
    private var lastUpdateMs = 0L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.antiDetectionEnabled) return

        baseSteps = cfg.customSteps
        currentFluctuation = 0
        isResting = false
        restUntilMs = 0L

        LogX.i("反检测 Hook 启动 | 波动范围±${cfg.fluctuationRange} 休息概率=${cfg.restProbability}")
        try { LogStore.add("antidetect", "反检测：波动${cfg.fluctuationRange}，休息概率${cfg.restProbability}") } catch (_: Exception) { }

        hookSensorEventForAntiDetection(lpparam, cfg)
    }

    private fun hookSensorEventForAntiDetection(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: StepConfig
    ) {
        try {
            val listenerCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEventListener", lpparam.classLoader) ?: return
            val eventCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEvent", lpparam.classLoader) ?: return

            XposedHelpers.findAndHookMethod(listenerCls, "onSensorChanged",
                eventCls, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val event = p.args[0] ?: return
                            val sensorField = eventCls.getDeclaredField("sensor")
                            sensorField.isAccessible = true
                            val sensor = sensorField.get(event) ?: return
                            val type = sensor.javaClass.getMethod("getType").invoke(sensor) as? Int ?: return

                            if (type != 19) return

                            updateAntiDetectionState(cfg)

                            val valuesField = eventCls.getDeclaredField("values")
                            valuesField.isAccessible = true
                            val values = valuesField.get(event) as? FloatArray ?: return

                            if (isResting) {
                                values[0] = 0f
                            } else {
                                val adjusted = (baseSteps + currentFluctuation).coerceAtLeast(0)
                                values[0] = adjusted.toFloat()
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("AntiDetection", "onSensorChanged")
        } catch (e: Exception) {
            LogX.hookFailed("AntiDetection", "onSensorChanged", e)
        }
    }

    private fun updateAntiDetectionState(cfg: StepConfig) {
        val now = System.currentTimeMillis()

        if (isResting) {
            if (now >= restUntilMs) {
                isResting = false
                LogX.d("休息结束，恢复步数输出")
            }
        } else {
            val roll = random.nextFloat()
            if (roll < cfg.restProbability) {
                isResting = true
                restUntilMs = now + random.nextLong(30000L, 300000L)
                LogX.d("进入休息期，持续${(restUntilMs - now) / 1000}秒")
            }
        }

        if (now - lastUpdateMs > 5000) {
            lastUpdateMs = now
            currentFluctuation = if (cfg.fluctuationRange > 0)
                random.nextInt(-cfg.fluctuationRange, cfg.fluctuationRange + 1) else 0
        }
    }
}
