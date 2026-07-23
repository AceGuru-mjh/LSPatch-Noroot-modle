package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogStore
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

object CalorieCalculatorHook {

    private val random = Random(System.currentTimeMillis())

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.calorieCalcEnabled) return

        val weight = cfg.userWeight
        val multiplier = cfg.calorieMultiplier
        LogX.i("卡路里计算 Hook 启动 | 体重=${weight}kg 系数=$multiplier")
        try { LogStore.add("calorie", "卡路里计算：体重${weight}kg，系数${multiplier}") } catch (_: Exception) { }

        hookCalorieReadAPIs(lpparam, cfg, weight, multiplier)
        hookHealthDatabaseQueries(lpparam, cfg, weight, multiplier)
    }

    private fun calculateCalories(steps: Int, weight: Float, multiplier: Float): Float {
        val variation = random.nextFloat() * 0.1f - 0.05f
        return steps * multiplier * weight * (1f + variation)
    }

    private fun hookCalorieReadAPIs(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: StepConfig,
        weight: Float,
        multiplier: Float
    ) {
        try {
            val healthDataCls = XposedHelpers.findClassIfExists(
                "com.huawei.health.data.HealthData", lpparam.classLoader)
            if (healthDataCls != null) {
                XposedHelpers.findAndHookMethod(healthDataCls, "getCalorie",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val steps = cfg.customSteps
                                p.result = calculateCalories(steps, weight, multiplier)
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CalorieCalc", "HealthData.getCalorie")
            }
        } catch (e: Exception) { LogX.w("HealthData未找到: ${e.message}") }

        try {
            val xmDataCls = XposedHelpers.findClassIfExists(
                "com.xiaomi.health.data.HealthData", lpparam.classLoader)
            if (xmDataCls != null) {
                XposedHelpers.findAndHookMethod(xmDataCls, "getCalories",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val steps = cfg.customSteps
                                p.result = calculateCalories(steps, weight, multiplier)
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CalorieCalc", "XiaomiHealthData.getCalories")
            }
        } catch (e: Exception) { LogX.w("小米健康数据类未找到: ${e.message}") }

        try {
            val keepDataCls = XposedHelpers.findClassIfExists(
                "com.keepfitness.data.FitnessData", lpparam.classLoader)
            if (keepDataCls != null) {
                XposedHelpers.findAndHookMethod(keepDataCls, "getCalories",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val steps = cfg.customSteps
                                p.result = calculateCalories(steps, weight, multiplier)
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CalorieCalc", "KeepFitnessData.getCalories")
            }
        } catch (e: Exception) { LogX.w("Keep数据类未找到: ${e.message}") }
    }

    private fun hookHealthDatabaseQueries(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: StepConfig,
        weight: Float,
        multiplier: Float
    ) {
        try {
            val queryBuilderCls = XposedHelpers.findClassIfExists(
                "com.huawei.health.cloud.HealthDataQueryBuilder", lpparam.classLoader)
            if (queryBuilderCls != null) {
                XposedHelpers.findAndHookMethod(queryBuilderCls, "queryCalories",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val steps = cfg.customSteps
                                p.result = calculateCalories(steps, weight, multiplier)
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CalorieCalc", "HealthDataQueryBuilder.queryCalories")
            }
        } catch (e: Exception) { LogX.w("QueryBuilder未找到: ${e.message}") }

        try {
            val crCls = XposedHelpers.findClassIfExists(
                "android.content.ContentResolver", lpparam.classLoader)
            if (crCls != null) {
                val targetUris = listOf(
                    "calories", "calorie", "energy", "kcal", "kiloCalories"
                )
                XposedHelpers.findAndHookMethod(crCls, "query",
                    android.net.Uri::class.java, Array<String>::class.java,
                    String::class.java, Array<String>::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val uri = p.args[0] as? android.net.Uri ?: return
                                val uriStr = uri.toString().lowercase()
                                if (targetUris.any { uriStr.contains(it) }) {
                                    LogX.d("拦截卡路里查询: $uriStr")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CalorieCalc", "ContentResolver.query(calories)")
            }
        } catch (e: Exception) { LogX.w("ContentResolver未找到: ${e.message}") }
    }
}
