package com.videosaver.noroot.hooks

import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.LogX
import com.videosaver.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku UI 自动化 截图/点击 Hook
 *
 * 通过 Shizuku adb 级执行 screencap + input tap 命令实现 UI 自动化，
 * 用于辅助视频下载等场景的按钮自动化点击。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 screencap + input tap
 *  - 不调用 root 命令
 *  - 截图保存到 /sdcard/Download/
 */
object ShizukuCaptureHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.shizukuCaptureEnabled) return
        LogX.i("Shizuku截图/点击自动化启动（Shizuku adb级）")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        performCapture(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (screencap/input tap)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (screencap/input tap)", e)
        }
    }

    private fun performCapture(cfg: VideoConfig) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 screencap / input tap 自动化")
            return
        }

        val savePath = cfg.customSavePath.ifEmpty { "/sdcard/Download/" }
        try {
            val screencapCmd = "screencap -p ${savePath}screenshot.png"
            val result = ShizukuHelper.execShell(screencapCmd)
            if (result != null) {
                LogX.i("screencap -> ${savePath}screenshot.png")
            } else {
                LogX.w("screencap 执行失败")
            }
        } catch (e: Throwable) {
            LogX.e("screencap 异常", e)
        }

        try {
            val tapCmd = "input tap ${cfg.shizukuTapX} ${cfg.shizukuTapY}"
            val tapResult = ShizukuHelper.execShell(tapCmd)
            if (tapResult != null) {
                LogX.i("input tap ${cfg.shizukuTapX},${cfg.shizukuTapY} -> $tapResult")
            }
        } catch (e: Throwable) {
            LogX.e("input tap 异常", e)
        }
    }
}
