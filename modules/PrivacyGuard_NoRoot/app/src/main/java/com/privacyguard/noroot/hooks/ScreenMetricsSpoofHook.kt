package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】屏幕参数防指纹
 *
 * 伪造屏幕分辨率、密度、刷新率等参数，防止通过屏幕特征进行设备指纹追踪。
 *
 * 硬性限制：仅修改传给当前APP的 DisplayMetrics/Display 查询结果，
 *          不影响真实显示输出（画面不会因此改变）。
 */
object ScreenMetricsSpoofHook {

    // 伪装为常见机型参数（1080x2400, density 2.75 / 440dpi）
    private const val FAKE_WIDTH = 1080
    private const val FAKE_HEIGHT = 2400
    private const val FAKE_DENSITY = 2.75f
    private const val FAKE_DENSITY_DPI = 440
    private const val FAKE_REFRESH_RATE = 120f

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.screenMetricsSpoofEnabled) return
        LogX.i("【实验性】屏幕参数防指纹启动")

        hookDisplayMetrics(lpparam)
        hookDisplay(lpparam)
    }

    private fun hookDisplayMetrics(lpparam: XC_LoadPackage.LoadPackageParam) {
        val dm = XposedHelpers.findClassIfExists(
            "android.util.DisplayMetrics", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(dm, "setToDefaults", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val obj = p.thisObject
                    XposedHelpers.setIntField(obj, "widthPixels", FAKE_WIDTH)
                    XposedHelpers.setIntField(obj, "heightPixels", FAKE_HEIGHT)
                    XposedHelpers.setFloatField(obj, "density", FAKE_DENSITY)
                    XposedHelpers.setIntField(obj, "densityDpi", FAKE_DENSITY_DPI)
                    XposedHelpers.setFloatField(obj, "xdpi", FAKE_DENSITY_DPI.toFloat())
                    XposedHelpers.setFloatField(obj, "ydpi", FAKE_DENSITY_DPI.toFloat())
                }
            })
            LogX.hookSuccess("DisplayMetrics", "setToDefaults")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookDisplay(lpparam: XC_LoadPackage.LoadPackageParam) {
        val display = XposedHelpers.findClassIfExists(
            "android.view.Display", lpparam.classLoader) ?: return

        // getRefreshRate
        try {
            XposedHelpers.findAndHookMethod(display, "getRefreshRate", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = FAKE_REFRESH_RATE
                }
            })
            LogX.hookSuccess("Display", "getRefreshRate")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getMetrics(DisplayMetrics)
        try {
            XposedHelpers.findAndHookMethod(display, "getMetrics",
                "android.util.DisplayMetrics", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val dm = p.args[0] ?: return
                    XposedHelpers.setIntField(dm, "widthPixels", FAKE_WIDTH)
                    XposedHelpers.setIntField(dm, "heightPixels", FAKE_HEIGHT)
                    XposedHelpers.setFloatField(dm, "density", FAKE_DENSITY)
                    XposedHelpers.setIntField(dm, "densityDpi", FAKE_DENSITY_DPI)
                }
            })
            LogX.hookSuccess("Display", "getMetrics")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getRealMetrics
        try {
            XposedHelpers.findAndHookMethod(display, "getRealMetrics",
                "android.util.DisplayMetrics", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val dm = p.args[0] ?: return
                    XposedHelpers.setIntField(dm, "widthPixels", FAKE_WIDTH)
                    XposedHelpers.setIntField(dm, "heightPixels", FAKE_HEIGHT)
                    XposedHelpers.setFloatField(dm, "density", FAKE_DENSITY)
                    XposedHelpers.setIntField(dm, "densityDpi", FAKE_DENSITY_DPI)
                }
            })
            LogX.hookSuccess("Display", "getRealMetrics")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
