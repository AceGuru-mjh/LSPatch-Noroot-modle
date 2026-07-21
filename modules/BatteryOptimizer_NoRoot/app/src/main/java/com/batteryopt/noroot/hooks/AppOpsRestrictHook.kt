package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import com.batteryopt.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku cmd appops 后台限制 Hook
 *
 * 通过 Shizuku adb 级执�?"cmd appops set" 命令，在系统级限制目标应用的
 * WAKE_LOCK / RUN_IN_BACKGROUND / BOOT_COMPLETED 等关键权限，
 * 远比 Java �?Hook 效果更强�? *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执�?cmd appops
 *  - 不调�?root 命令
 */
object AppOpsRestrictHook {

    private val defaultRestrictedPackages = listOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo"
    )

    private val defaultAppOpsRestrictions = mapOf(
        "WAKE_LOCK" to "deny",
        "RUN_IN_BACKGROUND" to "deny",
        "BOOT_COMPLETED" to "deny",
        "RUN_ANY_IN_BACKGROUND" to "deny",
        "START_FOREGROUND" to "ignore"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.appOpsRestrictEnabled) return
        LogX.i("cmd appops后台限制启动（Shizuku adb级） pkgs=${cfg.appOpsRestrictedPackages}")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        applyAppOpsRestrictions(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (cmd appops)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (cmd appops)", e)
        }
    }

    private fun applyAppOpsRestrictions(cfg: BatteryConfig) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 cmd appops 限制")
            return
        }

        val packages = cfg.appOpsRestrictedPackages.ifEmpty { defaultRestrictedPackages }
        val restrictions = cfg.appOpsRestrictions.ifEmpty { defaultAppOpsRestrictions }

        for (pkg in packages) {
            for ((op, mode) in restrictions) {
                try {
                    val cmd = "cmd appops set \"$pkg\" $op $mode"
                    val result = ShizukuHelper.execShell(cmd)
                    if (result != null) {
                        LogX.i("cmd appops set $pkg $op $mode -> $result")
                    } else {
                        LogX.w("cmd appops set $pkg $op $mode 执行失败")
                    }
                } catch (e: Throwable) {
                    LogX.e("cmd appops set $pkg $op $mode 异常", e)
                }
            }
        }
    }
}
