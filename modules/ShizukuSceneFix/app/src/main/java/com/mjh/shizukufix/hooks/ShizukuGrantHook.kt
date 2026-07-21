package com.mjh.shizukufix.hooks

import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import com.mjh.shizukufix.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku pm grant 权限授予 Hook
 *
 * 通过 Shizuku adb 级执行 "pm grant" 命令，真正授予目标应用 Shizuku API 权限，
 * 并广播 APPLICATION_PERMISSION_CHANGED 通知 Shizuku 管理器刷新。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 pm grant + am broadcast
 *  - 不调用 root 命令
 *  - 相比模拟 UI 点击授权，此方式更可靠且无需前台交互
 */
object ShizukuGrantHook {

    private val defaultGrantPackages = listOf(
        "com.omarea.vtools"
    )

    private val defaultGrantPermissions = listOf(
        "moe.shizuku.manager.permission.API_V23"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.pmGrantEnabled) return
        LogX.i("pm grant权限授予启动（Shizuku adb级） pkgs=${cfg.pmGrantPackages}")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        grantShizukuPermissions(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (pm grant)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (pm grant)", e)
        }
    }

    private fun grantShizukuPermissions(cfg: ShizukuFixConfig) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 pm grant")
            return
        }

        val packages = cfg.pmGrantPackages.ifEmpty { defaultGrantPackages }
        val permissions = cfg.pmGrantPermissions.ifEmpty { defaultGrantPermissions }

        for (pkg in packages) {
            for (perm in permissions) {
                try {
                    val cmd = "pm grant \"$pkg\" \"$perm\""
                    val result = ShizukuHelper.execShell(cmd)
                    if (result != null) {
                        LogX.i("pm grant $pkg $perm -> $result")
                    } else {
                        LogX.w("pm grant $pkg $perm 执行失败")
                    }
                } catch (e: Throwable) {
                    LogX.e("pm grant $pkg $perm 异常", e)
                }
            }
        }

        try {
            val broadcastCmd = "am broadcast -a moe.shizuku.manager.action.APPLICATION_PERMISSION_CHANGED"
            val broadcastResult = ShizukuHelper.execShell(broadcastCmd)
            if (broadcastResult != null) {
                LogX.i("am broadcast APPLICATION_PERMISSION_CHANGED -> $broadcastResult")
            }
        } catch (e: Throwable) {
            LogX.e("am broadcast 异常", e)
        }
    }
}
