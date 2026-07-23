package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogStore
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.util.Calendar

object ScheduleStepHook {

    private var lastHourCheck = -1
    private var currentTargetSteps = 0

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.scheduleStepEnabled) return
        if (cfg.schedules.isEmpty()) {
            LogX.w("定时策略已启用但无策略配置")
            return
        }

        LogX.i("定时策略 Hook 启动 | 策略数=${cfg.schedules.size}")
        try { LogStore.add("schedule", "定时策略已启用") } catch (_: Exception) { }

        parseSchedulesAndUpdateTarget(cfg)

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

                            if (type == 19) {
                                parseSchedulesAndUpdateTarget(cfg)
                                if (currentTargetSteps > 0) {
                                    val valuesField = eventCls.getDeclaredField("values")
                                    valuesField.isAccessible = true
                                    val values = valuesField.get(event) as? FloatArray ?: return
                                    values[0] = currentTargetSteps.toFloat()
                                }
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("ScheduleStep", "onSensorChanged")
        } catch (e: Exception) {
            LogX.hookFailed("ScheduleStep", "onSensorChanged", e)
        }
    }

    private fun parseSchedulesAndUpdateTarget(cfg: StepConfig) {
        val cal = Calendar.getInstance()
        val nowHour = cal.get(Calendar.HOUR_OF_DAY)
        if (nowHour == lastHourCheck) return
        lastHourCheck = nowHour

        for (schedStr in cfg.schedules) {
            try {
                val obj = JSONObject(schedStr)
                val start = obj.optInt("start", 0)
                val end = obj.optInt("end", 23)
                val steps = obj.optInt("steps", 0)

                if (nowHour in start..end) {
                    currentTargetSteps = steps
                    LogX.d("命中时段 $start:$end 目标步数=$steps")
                    return
                }
            } catch (_: Exception) { }
        }
        currentTargetSteps = cfg.customSteps
    }
}
