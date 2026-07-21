package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogStore
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

/**
 * 步数传感�?Hook（基础功能�?
 *
 * 拦截路径�?
 *  1. SensorManager.getDefaultSensor(int type) �?�?TYPE_STEP_COUNTER(19)/TYPE_STEP_DETECTOR(18) 返回包装后的传感�?
 *  2. SensorManager.registerListener(...) �?拦截注册过程，注入伪�?SensorEvent
 *  3. SensorEventListener.onSensorChanged(SensorEvent) �?直接修改 event.values 为目标步�?
 *
 * 伪造值计算：
 *  - 基准值：cfg.customSteps
 *  - 随机波动：±cfg.randomFluctuation（避免固定值被识别为外挂）
 *  - 进程内每秒缓慢累加（模拟真实走路步频 ~1.2 �?秒）
 *
 * 硬性限制（NoRoot版）�?
 *  - �?Hook Java �?SensorManager，不影响系统传感器服�?
 *  - 不修改系统属�?文件，仅进程内有�?
 */
object StepSensorHook {

    private const val TYPE_STEP_DETECTOR = 18
    private const val TYPE_STEP_COUNTER = 19

    private val random = Random(System.currentTimeMillis())

    /** 当前进程累计步数（启动时初始化为目标值，随后缓慢累加�?*/
    private var currentSteps: Int = 0
    private var lastTickMs: Long = 0L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.stepModifyEnabled) return
        currentSteps = cfg.customSteps
        lastTickMs = System.currentTimeMillis()
        LogX.i("步数传感�?Hook 启动 | 目标步数=${cfg.customSteps} 波动±${cfg.randomFluctuation}")
        try { LogStore.add("modified", "修改步数: ${cfg.customSteps}") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookGetDefaultSensor(lpparam, cfg)
        hookRegisterListener(lpparam, cfg)
        hookSensorEventListener(lpparam, cfg)
    }

    /** Hook SensorManager.getDefaultSensor(int) �?标记目标传感�?*/
    private fun hookGetDefaultSensor(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val smCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorManager", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(smCls, "getDefaultSensor",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val type = p.args[0] as? Int ?: return
                            if (type == TYPE_STEP_COUNTER || type == TYPE_STEP_DETECTOR) {
                                LogX.d("getDefaultSensor 命中 type=$type | 已标�?)
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("SensorManager", "getDefaultSensor")
        } catch (e: Exception) {
            LogX.hookFailed("SensorManager", "getDefaultSensor", e)
        }
    }

    /** Hook SensorManager.registerListener �?拦截步数传感器注�?*/
    private fun hookRegisterListener(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val smCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorManager", lpparam.classLoader) ?: return
            // 多签名覆盖：registerListener(SensorEventListener, Sensor, int)
            try {
                XposedHelpers.findAndHookMethod(smCls, "registerListener",
                    "android.hardware.SensorEventListener",
                    "android.hardware.Sensor",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val sensor = p.args[1] ?: return
                                val typeMethod = sensor.javaClass.getMethod("getType")
                                val type = typeMethod.invoke(sensor) as? Int ?: return
                                if (type == TYPE_STEP_COUNTER || type == TYPE_STEP_DETECTOR) {
                                    LogX.d("registerListener 命中 type=$type")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SensorManager", "registerListener(3arg)")
            } catch (e: Exception) { LogX.w("registerListener 3 参签名不存在: ${e.message}") }

            // 多签名覆盖：registerListener(SensorEventListener, Sensor, int, Handler)
            try {
                XposedHelpers.findAndHookMethod(smCls, "registerListener",
                    "android.hardware.SensorEventListener",
                    "android.hardware.Sensor",
                    Int::class.javaPrimitiveType,
                    "android.os.Handler",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val sensor = p.args[1] ?: return
                                val typeMethod = sensor.javaClass.getMethod("getType")
                                val type = typeMethod.invoke(sensor) as? Int ?: return
                                if (type == TYPE_STEP_COUNTER || type == TYPE_STEP_DETECTOR) {
                                    LogX.d("registerListener(4arg) 命中 type=$type")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SensorManager", "registerListener(4arg)")
            } catch (e: Exception) { LogX.w("registerListener 4 参签名不存在: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SensorManager", "registerListener", e)
        }
    }

    /** Hook SensorEventListener.onSensorChanged �?直接修改 event.values */
    private fun hookSensorEventListener(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
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
                            val valuesField = eventCls.getDeclaredField("values")
                            valuesField.isAccessible = true
                            val values = valuesField.get(event) as? FloatArray ?: return

                            val sensorField = eventCls.getDeclaredField("sensor")
                            sensorField.isAccessible = true
                            val sensor = sensorField.get(event) ?: return
                            val type = sensor.javaClass.getMethod("getType").invoke(sensor) as? Int ?: return

                            if (type == TYPE_STEP_COUNTER) {
                                // 累加模拟真实走路步频
                                tick(cfg)
                                values[0] = currentSteps.toFloat()
                                LogX.d("onSensorChanged 注入步数: $currentSteps")
                            } else if (type == TYPE_STEP_DETECTOR) {
                                // 步数检测器：每次触发表示走了一�?
                                values[0] = 1f
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("SensorEventListener", "onSensorChanged")
        } catch (e: Exception) {
            LogX.hookFailed("SensorEventListener", "onSensorChanged", e)
        }
    }

    /** 模拟真实步频累加（约 1.2 �?秒） */
    private fun tick(cfg: StepConfig) {
        val now = System.currentTimeMillis()
        val deltaMs = now - lastTickMs
        if (deltaMs < 800) return  // 800ms 内不重复累加
        lastTickMs = now
        // 随机波动（避免固定值被识别�?
        val fl = if (cfg.randomFluctuation > 0)
            random.nextInt(-cfg.randomFluctuation, cfg.randomFluctuation + 1) else 0
        currentSteps = (cfg.customSteps + fl).coerceAtLeast(0)
    }
}
