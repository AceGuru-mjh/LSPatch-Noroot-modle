package com.microx.enhancer.hooks

import android.graphics.Color
import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.LogX
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】自定义主题 Hook
 *
 * 功能：
 *  - Hook 资源加载，注入自定义颜色（覆盖原主题色）
 *  - 微信：Hook WXResources getColor/getDrawable，替换主色调
 *  - QQ：Hook QQResources 同样方法
 *
 * 实现原理：
 *  - Android Resources.getColor(int id) 是查找颜色的统一入口
 *  - 微信/QQ 主题色通常对应特定资源 ID
 *  - 通过 Hook getColor，对已知颜色值（如微信绿色 #FF07C160）替换为自定义色
 *
 * 硬性限制：
 *  - 仅替换应用层颜色查询结果，不修改资源文件
 *  - 部分颜色通过 Drawable 直接渲染，可能需要额外 Hook
 *  - 实验性功能，可能因版本变化失效
 */
object CustomThemeHook {

    /** 默认替换的目标颜色（微信绿/QQ蓝），可扩展 */
    private val targetColors = intArrayOf(
        0xFF07C160.toInt(),  // 微信主绿
        0xFF1AAD19.toInt(),  // 微信绿（旧版）
        0xFF10AEFF.toInt(),  // QQ 浅蓝
        0xFF12B7F5.toInt()   // QQ 蓝
    )

    /** 替换为的青色（与模块主题一致） */
    private const val REPLACEMENT_COLOR = 0xFF00695C.toInt()

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_THEME)) return
        HookHelper.log("【实验性】加载自定义主题Hook（微信）")
        hookResourcesGetColor(lpparam)
    }

    fun hookQQ(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_THEME)) return
        HookHelper.log("【实验性】加载自定义主题Hook（QQ）")
        hookResourcesGetColor(lpparam)
    }

    /** Hook Resources.getColor(int) / getColor(int, Theme) 替换主题色 */
    private fun hookResourcesGetColor(lpparam: XC_LoadPackage.LoadPackageParam) {
        val resCls = XposedHelpers.findClassIfExists(
            "android.content.res.Resources", lpparam.classLoader
        ) ?: return

        // getColor(int) 已废弃但部分代码仍调用
        try {
            XposedHelpers.findAndHookMethod(
                resCls, "getColor",
                Int::class.javaPrimitiveType,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                        val original = p.result as? Int ?: return
                        if (targetColors.contains(original)) {
                            p.result = REPLACEMENT_COLOR
                            HookHelper.logD("颜色替换: #${Integer.toHexString(original)} -> #${Integer.toHexString(REPLACEMENT_COLOR)}")
                        }
                    }
                })
            HookHelper.logD("Resources.getColor(int) Hook 成功")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getColor(int, Resources.Theme) API23+
        try {
            XposedHelpers.findAndHookMethod(
                resCls, "getColor",
                Int::class.javaPrimitiveType,
                "android.content.res.Resources.Theme",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                        val original = p.result as? Int ?: return
                        if (targetColors.contains(original)) {
                            p.result = REPLACEMENT_COLOR
                            HookHelper.logD("颜色替换(Theme): #${Integer.toHexString(original)} -> #${Integer.toHexString(REPLACEMENT_COLOR)}")
                        }
                    }
                })
            HookHelper.logD("Resources.getColor(int, Theme) Hook 成功")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // Hook Color.parseColor，对已知颜色字符串做替换
        try {
            val colorCls = XposedHelpers.findClassIfExists(
                "android.graphics.Color", lpparam.classLoader
            ) ?: return
            XposedHelpers.findAndHookMethod(
                colorCls, "parseColor",
                String::class.java,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                        val colorStr = p.args[0] as? String ?: return
                        // 微信绿字符串替换
                        if (colorStr.equals("#07C160", true) ||
                            colorStr.equals("#1AAD19", true) ||
                            colorStr.equals("#10AEFF", true) ||
                            colorStr.equals("#12B7F5", true)
                        ) {
                            p.result = REPLACEMENT_COLOR
                            HookHelper.logD("Color.parseColor 替换: $colorStr -> #${Integer.toHexString(REPLACEMENT_COLOR)}")
                        }
                    }
                })
            HookHelper.logD("Color.parseColor Hook 成功")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
