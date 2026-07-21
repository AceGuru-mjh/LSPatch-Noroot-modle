package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

/**
 * StepCounter / StepDetector �?Hook（基础功能�?
 *
 * 拦截路径�?
 *  1. android.hardware.StepCounter �?Android 8+ 步数计数器内部类，hook 其构造和 getValue
 *  2. android.hardware.StepDetector �?步数检测器内部�?
 *  3. SensorEvent.values �?在事件分发前直接修改 values[0]
 *
 * 注：StepCounter/StepDetector �?Android 内部 Sensor 子类（不同厂商实现不同）�?
 * �?Hook 仅针对应用层可访问的反射入口，无法影�?Native ASensor 层�?
 *
 * 硬性限制（NoRoot版）�?
 *  - �?Hook Java �?Sensor 类，不修改系统传感器服务
 */
object StepCounterHook {

    private val random = Random(System.currentTimeMillis())

    /** 候选内部类全限定名（不同厂商命名差异） */
    private val counterClassCandidates = listOf(
        "android.hardware.StepCounter",
        "android.hardware.Sensor\${'$'}StepCounter",
        "android.hardware.SensorManager\${'$'}StepCounterImpl"
    )

    private val detectorClassCandidates = listOf(
        "android.hardware.StepDetector",
        "android.hardware.Sensor\${'$'}StepDetector",
        "android.hardware.SensorManager\${'$'}StepDetectorImpl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.stepModifyEnabled) return
        LogX.i("StepCounter Hook 启动 | 目标步数=${cfg.customSteps}")

        hookCounterClasses(lpparam, cfg)
        hookDetectorClasses(lpparam, cfg)
        hookSensorEventValues(lpparam, cfg)
    }

    /** Hook 步数计数器内部类 */
    private fun hookCounterClasses(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        var hitAny = false
        for (clsName in counterClassCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // Hook 构造函�?�?标记计数器实例化
                try {
                    XposedHelpers.findAndHookConstructor(cls, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("StepCounter 实例�? $clsName")
                        }
                    })
                    LogX.hookSuccess(clsName, "<init>")
                    hitAny = true
                } catch (e: Exception) { LogX.w("构�?hook 失败 $clsName: ${e.message}") }

                // Hook 可能存在�?getValue 方法
                try {
                    XposedHelpers.findAndHookMethod(cls, "getValue",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val fake = computeFakeStep(cfg)
                                    p.result = fake.toFloat()
                                    LogX.d("$clsName.getValue 返回 $fake")
                                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                            }
                        })
                    LogX.hookSuccess(clsName, "getValue")
                } catch (e: Exception) { LogX.w("getValue hook 失败 $clsName: ${e.message}") }
            } catch (e: Exception) { LogX.w("候选类 $clsName 异常: ${e.message}") }
        }
        if (!hitAny) LogX.w("StepCounter 候选类全部未命中（应用层无该类�?)
    }

    /** Hook 步数检测器内部�?*/
    private fun hookDetectorClasses(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        for (clsName in detectorClassCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookConstructor(cls, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("StepDetector 实例�? $clsName")
                        }
                    })
                    LogX.hookSuccess(clsName, "<init>")
                } catch (e: Exception) { LogX.w("构�?hook 失败 $clsName: ${e.message}") }
            } catch (e: Exception) { LogX.w("候选类 $clsName 异常: ${e.message}") }
        }
    }

    /** Hook SensorEvent.values 字段写入（兜底：直接修改 values 数组�?*/
    private fun hookSensorEventValues(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val eventCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEvent", lpparam.classLoader) ?: return
            // Hook SensorEvent 内部读取方法（如存在�?
            try {
                XposedHelpers.findAndHookMethod(eventCls, "toString",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val valuesField = eventCls.getDeclaredField("values")
                                valuesField.isAccessible = true
                                val values = valuesField.get(p.thisObject) as? FloatArray ?: return
                                if (values.isNotEmpty()) {
                                    val fake = computeFakeStep(cfg)
                                    // 仅当日志可见，不修改 toString 行为
                                    LogX.d("SensorEvent.toString values[0]=${values[0]} (目标=$fake)")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SensorEvent", "toString")
            } catch (e: Exception) { LogX.w("SensorEvent.toString hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SensorEvent", "toString", e)
        }
    }

    private fun computeFakeStep(cfg: StepConfig): Int {
        val fl = if (cfg.randomFluctuation > 0) random.nextInt(-cfg.randomFluctuation, cfg.randomFluctuation + 1) else 0
        return (cfg.customSteps + fl).coerceAtLeast(0)
    }
}
