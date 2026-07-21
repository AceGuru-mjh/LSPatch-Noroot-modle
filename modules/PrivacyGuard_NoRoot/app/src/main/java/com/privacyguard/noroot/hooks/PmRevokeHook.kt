package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import com.privacyguard.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku pm revoke 权限移除 Hook
 *
 * 通过 Shizuku adb 级执行 "pm revoke" 命令，真正移除目标应用的权限，
 * 而不仅仅是欺骗 checkSelfPermission 返回值。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 pm revoke
 *  - 不调用 root 命令
 *  - 权限撤销后需系统支持恢复
 */
object PmRevokeHook {

    private val defaultRevokePermissions = listOf(
        "android.permission.READ_CONTACTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_EXTERNAL_STORAGE"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.pmRevokeEnabled) return
        LogX.i("pm revoke权限撤销启动（Shizuku adb级） perms=${cfg.pmRevokePermissions}")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        revokePermissions(lpparam.packageName, cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (pm revoke)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (pm revoke)", e)
        }
    }

    private fun revokePermissions(pkg: String?, cfg: PrivacyConfig) {
        if (pkg.isNullOrEmpty()) return
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 pm revoke")
            return
        }

        val perms = cfg.pmRevokePermissions.ifEmpty { defaultRevokePermissions }
        for (perm in perms) {
            try {
                val cmd = "pm revoke \"$pkg\" \"$perm\""
                val result = ShizukuHelper.execShell(cmd)
                if (result != null) {
                    LogX.i("pm revoke $pkg $perm -> $result")
                } else {
                    LogX.w("pm revoke $pkg $perm 执行失败")
                }
            } catch (e: Throwable) {
                LogX.e("pm revoke $pkg $perm 异常", e)
            }
        }
    }
}
