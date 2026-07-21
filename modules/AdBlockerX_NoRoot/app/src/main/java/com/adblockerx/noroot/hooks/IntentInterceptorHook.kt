package com.adblockerx.noroot.hooks

import android.content.Intent
import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Intent 跳转拦截 Hook
 *
 * 拦截策略�?
 *  - Hook ContextWrapper.startActivity / startActivityForResult
 *  - Hook Instrumentation.execStartActivity（兜底）
 *  - 解析 Intent.data / action / category，识别广告跳�?
 *  - 命中黑名�?URL 或广告关键字时阻断跳�?
 *
 * 边界声明�?
 *  - 仅作用于�?APP 进程内的 Intent 跳转
 *  - 不影响系统其�?APP
 *  - 谨慎使用：可能影响部分正常功�?
 */
object IntentInterceptorHook {

    /** 广告跳转 Intent action 关键�?*/
    private val AD_INTENT_KEYWORDS = arrayOf(
        "ad", "ads", "advert", "banner", "splash",
        "doubleclick", "googlesyndication",
        "toutiao", "gdt", "baidu", "ksad"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.intentInterceptorEnabled) return
        LogX.i("【实验性】IntentInterceptorHook 启动（应用进程内�?)

        hookContextStartActivity(lpparam)
        hookInstrumentationExecStartActivity(lpparam)
    }

    private fun hookContextStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cw = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader) ?: return

            // startActivity(Intent)
            try {
                XposedHelpers.findAndHookMethod(cw, "startActivity",
                    Intent::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val intent = p.args.getOrNull(0) as? Intent ?: return
                            if (shouldBlockIntent(intent)) {
                                LogX.i("[Intent] 拦截 startActivity: ${intent.data} ${intent.action}")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "startActivity")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // startActivityForResult(Intent, int)
            try {
                XposedHelpers.findAndHookMethod(cw, "startActivityForResult",
                    Intent::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val intent = p.args.getOrNull(0) as? Intent ?: return
                            if (shouldBlockIntent(intent)) {
                                LogX.i("[Intent] 拦截 startActivityForResult: ${intent.data}")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "startActivityForResult")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("IntentInterceptorHook.ContextWrapper 异常", e)
        }
    }

    private fun hookInstrumentationExecStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val instr = XposedHelpers.findClassIfExists(
                "android.app.Instrumentation", lpparam.classLoader) ?: return

            // execStartActivity 重载较多，使用反射遍�?
            val methods = instr.declaredMethods.filter { it.name == "execStartActivity" }
            for (m in methods) {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 第一个参数通常�?Intent
                            val intent = p.args.getOrNull(0) as? Intent ?: return
                            if (shouldBlockIntent(intent)) {
                                LogX.i("[Intent] 拦截 Instrumentation.execStartActivity: ${intent.data}")
                                p.result = null
                            }
                        }
                    })
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
            LogX.hookSuccess("Instrumentation", "execStartActivity x${methods.size}")
        } catch (e: Throwable) {
            LogX.e("IntentInterceptorHook.Instrumentation 异常", e)
        }
    }

    /** 判断 Intent 是否应该被拦�?*/
    private fun shouldBlockIntent(intent: Intent): Boolean {
        // 1. data URL 命中黑名�?
        val data = intent.data?.toString() ?: ""
        if (data.isNotBlank()) {
            val host = com.adblockerx.noroot.utils.AdBlockList.extractHost(data)
            if (host != null && HostsFilterHook.isBlocked(host)) return true

            // 2. data 中包含广告关键字
            val lower = data.lowercase()
            if (AD_INTENT_KEYWORDS.any { lower.contains(it) }) return true
        }

        // 3. action / category 中包含广告关键字
        val action = intent.action?.lowercase() ?: ""
        if (action.isNotBlank() && AD_INTENT_KEYWORDS.any { action.contains(it) }) return true

        // 4. component className 包含广告关键�?
        val cls = intent.component?.className?.lowercase() ?: ""
        if (cls.isNotBlank() && AD_INTENT_KEYWORDS.any { cls.contains(it) }) return true

        return false
    }
}
