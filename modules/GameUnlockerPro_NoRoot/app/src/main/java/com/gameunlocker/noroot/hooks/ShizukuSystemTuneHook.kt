package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import com.gameunlocker.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 系统级调优 Hook
 *
 * 通过 Shizuku adb 级执行 dumpsys / wm / cmd 等命令，
 * 实现系统级显示检测、分辨率设置和电池优化豁免。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行
 *  - wm size/density 修改影响全系统，请谨慎使用
 */
object ShizukuSystemTuneHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.shizukuSystemTuneEnabled) return
        LogX.i("Shizuku系统调优启动（Shizuku adb级）")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        performSystemTune(lpparam.packageName, cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (ShizukuSystemTune)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (ShizukuSystemTune)", e)
        }
    }

    private fun performSystemTune(pkg: String?, cfg: GameConfig) {
        if (pkg.isNullOrEmpty()) return
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过系统调优")
            return
        }

        try {
            val sfResult = ShizukuHelper.execDumpsys("SurfaceFlinger")
            if (sfResult != null) {
                val lines = sfResult.lines()
                val displayLines = lines.filter {
                    it.contains("Display") || it.contains("refresh-rate") ||
                    it.contains("fps") || it.contains("Hz")
                }
                if (displayLines.isNotEmpty()) {
                    LogX.i("SurfaceFlinger 显示能力:\n${displayLines.joinToString("\n")}")
                }
            }
        } catch (e: Throwable) {
            LogX.e("dumpsys SurfaceFlinger 异常", e)
        }

        if (cfg.resolutionSpoofEnabled && cfg.spoofWidth > 0 && cfg.spoofHeight > 0) {
            try {
                val wmResult = ShizukuHelper.execWmSize(cfg.spoofWidth, cfg.spoofHeight)
                if (wmResult != null) {
                    LogX.i("wm size ${cfg.spoofWidth}x${cfg.spoofHeight} -> $wmResult")
                }
                val dpiResult = ShizukuHelper.execWmDensity(cfg.spoofDpi)
                if (dpiResult != null) {
                    LogX.i("wm density ${cfg.spoofDpi} -> $dpiResult")
                }
            } catch (e: Throwable) {
                LogX.e("wm size/density 异常", e)
            }
        }

        try {
            val ignoreBatteryResult = ShizukuHelper.execIgnoreBatteryOptimizations(pkg)
            if (ignoreBatteryResult != null) {
                LogX.i("set-ignore-battery-optimizations $pkg -> $ignoreBatteryResult")
            }
        } catch (e: Throwable) {
            LogX.e("set-ignore-battery-optimizations 异常", e)
        }
    }
}
