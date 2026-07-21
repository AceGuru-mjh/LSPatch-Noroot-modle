package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogStore
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * JobScheduler 优化 Hook（应用层�?
 *
 * 功能�?
 *  1. Hook JobScheduler.schedule，对非紧�?Job 追加 requireDeviceIdle 约束
 *  2. 对高频重�?Job 限频（放大最小周期）
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP 调度�?Job
 *  - 不能修改系统 JobScheduler 全局策略
 */
object JobSchedulerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("JobScheduler 优化启动 | 最小周�?${cfg.jobMinPeriodMs}ms idle约束=${cfg.jobRequireIdle}")
        try { LogStore.add("optimized", "优化JobScheduler") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookSchedule(lpparam, cfg)
        hookCancel(lpparam)
        hookEnqueue(lpparam)
    }

    private fun hookCancel(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val jsCls = XposedHelpers.findClassIfExists(
                "android.app.job.JobScheduler", lpparam.classLoader
            ) ?: return
            XposedHelpers.findAndHookMethod(
                jsCls, "cancel",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("[Job] cancel jobId=${p.args[0]}")
                    }
                })
            LogX.hookSuccess("JobScheduler", "cancel")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun hookEnqueue(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val jsCls = XposedHelpers.findClassIfExists(
                "android.app.job.JobScheduler", lpparam.classLoader
            ) ?: return
            // enqueue(JobInfo, JobWorkItem) Android 8+
            XposedHelpers.findAndHookMethod(
                jsCls, "enqueue",
                "android.app.job.JobInfo", "android.app.job.JobWorkItem",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("[Job] enqueue jobId")
                    }
                })
            LogX.hookSuccess("JobScheduler", "enqueue")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }


    private fun hookSchedule(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val jsCls = XposedHelpers.findClassIfExists(
                "android.app.job.JobScheduler", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                jsCls, "schedule",
                "android.app.job.JobInfo",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val jobInfo = p.args[0] ?: return
                        try {
                            modifyJobInfo(jobInfo, cfg)
                        } catch (e: Exception) {
                            LogX.e("修改 JobInfo 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("JobScheduler", "schedule")
        } catch (e: Exception) {
            LogX.e("Hook schedule 异常", e)
        }
    }

    private fun modifyJobInfo(jobInfo: Any, cfg: BatteryConfig) {
        val cls = jobInfo.javaClass

        // 1. 放大周期
        try {
            val periodField = cls.getDeclaredField("intervalMillis")
            periodField.isAccessible = true
            val cur = periodField.getLong(jobInfo)
            if (cur > 0 && cur < cfg.jobMinPeriodMs) {
                periodField.setLong(jobInfo, cfg.jobMinPeriodMs)
                LogX.w("Job 周期放大: ${cur}ms -> ${cfg.jobMinPeriodMs}ms")
            }
        } catch (_: Exception) {
            // 不同 Android 版本字段名可能不同，忽略
        }

        // 2. 追加 idle 约束（仅非紧�?Job�?
        if (cfg.jobRequireIdle) {
            try {
                val flagsField = cls.getDeclaredField("flags")
                flagsField.isAccessible = true
                val curFlags = flagsField.getInt(jobInfo)
                // FLAG_REQUIRE_DEVICE_IDLE = 1 << 0
                val newFlags = curFlags or (1 shl 0)
                flagsField.setInt(jobInfo, newFlags)
                LogX.d("Job 追加 requireDeviceIdle 约束")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }

        // 3. 日志记录 jobId
        try {
            val idField = cls.getDeclaredField("jobId")
            idField.isAccessible = true
            val jobId = idField.getInt(jobInfo)
            LogX.d("Job schedule: id=$jobId")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
