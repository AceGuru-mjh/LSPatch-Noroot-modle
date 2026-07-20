package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 分辨率 & 画质伪装 Hook
 *
 * 功能：低分辨率手机伪装 2K 屏幕，强制游戏加载高清材质包
 *
 * 硬性限制：
 *  - 仅修改应用读取到的 Display/DisplayMetrics 值
 *  - 实际 GPU 渲染分辨率由游戏渲染引擎决定，本 Hook 不改变硬件分辨率
 *  - 在某些游戏上可能因分辨率不匹配导致 UI 错位
 */
object ResolutionSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.resolutionSpoofEnabled) return
        val w = cfg.spoofWidth; val h = cfg.spoofHeight; val d = cfg.spoofDpi
        LogX.i("分辨率伪装: ${w}x${h} @${d}dpi（应用层）")

        hookDisplaySize(lpparam, w, h)
        hookDisplayMetrics(lpparam, w, h, d)
    }

    private fun hookDisplaySize(lpparam: XC_LoadPackage.LoadPackageParam, w: Int, h: Int) {
        try {
            val dc = XposedHelpers.findClassIfExists(
                "android.view.Display", lpparam.classLoader) ?: return
            val pt = XposedHelpers.findClassIfExists(
                "android.graphics.Point", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(dc, "getRealSize", pt, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val point = p.args[0] ?: return
                            point.javaClass.getField("x").setInt(point, w)
                            point.javaClass.getField("y").setInt(point, h)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(dc, "getSize", pt, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val point = p.args[0] ?: return
                            point.javaClass.getField("x").setInt(point, w)
                            point.javaClass.getField("y").setInt(point, h)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("Display", "getRealSize/getSize -> ${w}x${h}")
        } catch (e: Throwable) {
            LogX.hookFailed("Display", "getSize", e)
        }
    }

    private fun hookDisplayMetrics(lpparam: XC_LoadPackage.LoadPackageParam, w: Int, h: Int, d: Int) {
        try {
            val dmc = XposedHelpers.findClassIfExists(
                "android.util.DisplayMetrics", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(dmc, "setToDefaults", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val dm = p.thisObject
                        dm.javaClass.getField("widthPixels").setInt(dm, w)
                        dm.javaClass.getField("heightPixels").setInt(dm, h)
                        dm.javaClass.getField("densityDpi").setInt(dm, d)
                        val density = d / 160f
                        dm.javaClass.getField("density").setFloat(dm, density)
                        dm.javaClass.getField("scaledDensity").setFloat(dm, density)
                        dm.javaClass.getField("xdpi").setFloat(dm, d.toFloat())
                        dm.javaClass.getField("ydpi").setFloat(dm, d.toFloat())
                    } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                }
            })
            LogX.hookSuccess("DisplayMetrics", "setToDefaults -> ${w}x${h} @${d}dpi")
        } catch (e: Throwable) {
            LogX.hookFailed("DisplayMetrics", "setToDefaults", e)
        }
    }
}
