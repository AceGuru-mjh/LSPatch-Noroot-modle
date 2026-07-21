package com.audioboost.noroot.hooks

import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import com.audioboost.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Tinymix 硬件音频桥接 Hook（Shizuku adb 级）
 *
 * 通过与底层 ALSA 混音器直接交互，实现硬件级音频增强，
 * 远比 Java 层 AudioTrack/MediaPlayer Hook 效果更强。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 tinymix 命令
 *  - 不修改 /sys/class/audio 等系统节点
 *  - tinymix 命令可能在部分设备上不可用（需内核支持）
 */
object TinymixBridgeHook {

    private val defaultControls = mapOf(
        "Speaker Volume" to 20,
        "Headphone Volume" to 15,
        "ADC1 Volume" to 12,
        "Bass Boost" to 1
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.tinymixEnabled) return
        LogX.i("Tinymix桥接启动（Shizuku adb级） controls=${cfg.tinymixControls}")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        applyTinymixControls(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (tinymix)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (tinymix)", e)
        }
    }

    private fun applyTinymixControls(cfg: AudioConfig) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过tinymix硬件音频设置")
            return
        }

        try {
            val availableControls = ShizukuHelper.listTinymixControls()
            if (availableControls.isNullOrEmpty()) {
                LogX.w("tinymix 命令无输出（设备可能不支持）")
                return
            }
            LogX.i("tinymix 可用控件:\n$availableControls")
        } catch (e: Throwable) {
            LogX.e("探测tinymix控件失败", e)
        }

        val controls = cfg.tinymixControls.ifEmpty { defaultControls }
        for ((control, value) in controls) {
            try {
                val result = ShizukuHelper.execTinymix(control, value)
                if (result != null) {
                    LogX.i("tinymix \"$control\" $value -> $result")
                } else {
                    LogX.w("tinymix \"$control\" $value 执行失败")
                }
            } catch (e: Throwable) {
                LogX.e("tinymix \"$control\" $value 异常", e)
            }
        }

        try {
            val probeOutput = ShizukuHelper.execShell("tinymix | grep -i volume")
            if (!probeOutput.isNullOrEmpty()) {
                LogX.i("tinymix 音量相关控件:\n$probeOutput")
            }
        } catch (e: Throwable) {
            LogX.e("探测音量控件失败", e)
        }
    }
}
