package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.LogX
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】消息搜索增强 Hook
 *
 * 功能：
 *  - Hook 微信/QQ 搜索接口，放宽搜索限制
 *  - 让更多历史消息可被搜索（默认微信仅搜索近期消息/部分聊天记录）
 *  - Hook 搜索结果过滤，扩大搜索范围
 *
 * 实现原理：
 *  - 微信搜索通过 FTSIndexService 或类似类提供全文检索
 *  - 搜索接口有"仅近X天"等限制
 *  - Hook 设置搜索时间范围的参数，强制返回全部历史
 *
 * 硬性限制：
 *  - 仅作用于当前 APP 的搜索请求
 *  - 不同版本搜索接口差异大，使用多候选类名容错
 *  - 实验性功能，可能因微信版本变化失效
 */
object MessageSearchEnhanceHook {

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_MESSAGE_SEARCH_ENHANCE)) return
        HookHelper.log("【实验性】加载消息搜索增强Hook（微信）")

        hookSearchTimeRange(lpparam)
        hookSearchLimit(lpparam)
        hookSearchResultFilter(lpparam)
    }

    /** Hook 微信搜索时间范围限制方法 */
    private fun hookSearchTimeRange(lpparam: XC_LoadPackage.LoadPackageParam) {
        val searchClasses = listOf(
            "com.tencent.mm.plugin.fts.FTSIndexService",
            "com.tencent.mm.plugin.fts.a",
            "com.tencent.mm.plugin.fts.FTSMessageIndexLogic",
            "com.tencent.mm.sdk.storage.MMHistogramStorage"
        )

        for (clsName in searchClasses) {
            val cls = HookHelper.findClassSafe(lpparam, clsName) ?: continue

            // Hook 设置最小时间戳的方法（强制设为 0 = 不限）
            try {
                XposedHelpers.findAndHookMethod(cls, "setMinTime",
                    java.lang.Long.TYPE,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            p.args[0] = 0L
                            HookHelper.logD("搜索时间范围已放宽到 0（全部历史）")
                        }
                    })
                HookHelper.logD("搜索时间范围 Hook 成功: $clsName")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Hook 设置最大返回条数的方法（强制放大）
            try {
                XposedHelpers.findAndHookMethod(cls, "setLimit",
                    Int::class.javaPrimitiveType,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            val cur = p.args[0] as Int
                            if (cur < 1000) {
                                p.args[0] = 1000
                                HookHelper.logD("搜索结果条数限制放宽: $cur -> 1000")
                            }
                        }
                    })
                HookHelper.logD("搜索条数限制 Hook 成功: $clsName")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook 搜索接口的入参（如 search(String query, int limit) ）放大 limit */
    private fun hookSearchLimit(lpparam: XC_LoadPackage.LoadPackageParam) {
        val searchApiClasses = listOf(
            "com.tencent.mm.plugin.fts.FTSIndexJNI",
            "com.tencent.mm.plugin.fts.FTSSearchLogic",
            "com.tencent.mm.plugin.fts.b.a"
        )

        for (clsName in searchApiClasses) {
            val cls = HookHelper.findClassSafe(lpparam, clsName) ?: continue

            // search(String, int) 类签名
            try {
                XposedHelpers.findAndHookMethod(cls, "search",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            val limit = p.args[1] as Int
                            if (limit < 500) p.args[1] = 500
                        }
                    })
                HookHelper.logD("search(String, int) Hook 成功: $clsName")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook 搜索结果过滤逻辑，避免按时间过滤掉结果 */
    private fun hookSearchResultFilter(lpparam: XC_LoadPackage.LoadPackageParam) {
        val filterClasses = listOf(
            "com.tencent.mm.plugin.fts.ui.FTSSearchUI",
            "com.tencent.mm.plugin.fts.a.h"
        )

        for (clsName in filterClasses) {
            val cls = HookHelper.findClassSafe(lpparam, clsName) ?: continue

            // Hook isTimeRangeMatched 之类的方法，强制返回 true
            try {
                XposedHelpers.findAndHookMethod(cls, "isTimeRangeMatched",
                    java.lang.Long.TYPE,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            p.result = true
                        }
                    })
                HookHelper.logD("时间范围过滤 Hook 成功: $clsName")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Hook shouldFilterByTime 强制返回 false
            try {
                XposedHelpers.findAndHookMethod(cls, "shouldFilterByTime",
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            p.result = false
                        }
                    })
                HookHelper.logD("时间过滤禁用 Hook 成功: $clsName")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }
}
