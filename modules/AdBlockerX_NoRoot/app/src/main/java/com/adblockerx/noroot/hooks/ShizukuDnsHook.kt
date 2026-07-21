package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import com.adblockerx.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku Private DNS 广告拦截 Hook
 *
 * 通过 Shizuku adb 级执行 "settings put global" 命令设置系统级 Private DNS，
 * 实现全设备 DNS 级广告拦截（影响所有应用，不仅仅是已 Hook 的 APP）。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 settings put global
 *  - 不修改 /system/etc/hosts 文件
 *  - DNS 变化影响全系统，请谨慎使用
 */
object ShizukuDnsHook {

    private const val DEFAULT_DNS_SERVER = "dns.adguard.com"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.dnsAdBlockEnabled) return
        LogX.i("Private DNS广告拦截启动（Shizuku adb级） dns=${cfg.dnsServer}")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        setPrivateDns(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (Private DNS)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (Private DNS)", e)
        }
    }

    private fun setPrivateDns(cfg: AdBlockConfig) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 Private DNS 设置")
            return
        }

        val dnsServer = cfg.dnsServer.ifEmpty { DEFAULT_DNS_SERVER }

        try {
            val modeCmd = "settings put global private_dns_mode hostname"
            val modeResult = ShizukuHelper.execShell(modeCmd)
            if (modeResult != null) {
                LogX.i("private_dns_mode hostname -> $modeResult")
            }
        } catch (e: Throwable) {
            LogX.e("设置 private_dns_mode 异常", e)
        }

        try {
            val specCmd = "settings put global private_dns_specifier $dnsServer"
            val specResult = ShizukuHelper.execShell(specCmd)
            if (specResult != null) {
                LogX.i("private_dns_specifier $dnsServer -> $specResult")
            }
        } catch (e: Throwable) {
            LogX.e("设置 private_dns_specifier 异常", e)
        }
    }
}
