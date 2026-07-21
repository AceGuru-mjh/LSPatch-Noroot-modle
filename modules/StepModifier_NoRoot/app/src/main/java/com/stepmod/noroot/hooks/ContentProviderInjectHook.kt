package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogX
import com.stepmod.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku ContentProvider 步数注入 Hook
 *
 * 通过 Shizuku adb 级执行 "content insert" 命令，直接向目标应用的
 * ContentProvider 注入步数数据，绕过 Java 层传感器 Hook。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 content insert
 *  - URI 需根据实际 APP 调整
 *  - 不调用 root 命令
 */
object ContentProviderInjectHook {

    private val defaultStepUris = mapOf(
        "com.xiaomi.hm.health" to "content://com.xiaomi.hm.health/steps",
        "com.huawei.health" to "content://com.huawei.health.provider/steps"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.contentProviderInjectEnabled) return
        LogX.i("ContentProvider步数注入启动（Shizuku adb级） steps=${cfg.customSteps}")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        injectSteps(lpparam.packageName, cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (content insert)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (content insert)", e)
        }
    }

    private fun injectSteps(pkg: String?, cfg: StepConfig) {
        if (pkg.isNullOrEmpty()) return
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 ContentProvider 步数注入")
            return
        }

        val uri = cfg.stepInjectionUri.ifEmpty {
            defaultStepUris[pkg] ?: return
        }

        val steps = cfg.customSteps
        try {
            val cmd = "content insert --uri $uri --bind steps:s:$steps"
            val result = ShizukuHelper.execShell(cmd)
            if (result != null) {
                LogX.i("content insert $uri steps=$steps -> $result")
            } else {
                LogX.w("content insert $uri 执行失败")
            }
        } catch (e: Throwable) {
            LogX.e("content insert $uri 异常", e)
        }
    }
}
