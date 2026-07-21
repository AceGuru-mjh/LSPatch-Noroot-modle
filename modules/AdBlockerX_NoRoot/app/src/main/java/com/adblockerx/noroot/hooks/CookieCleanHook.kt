package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Cookie 清理 Hook
 *
 * 拦截策略�?
 *  - Hook android.webkit.CookieManager.getCookie / getCookieAsString
 *  - 在返回前过滤掉追踪类 Cookie（如 _ga / _gid / IDE / uid 等）
 *  - 同时 Hook CookieManager.setCookie 记录但不阻断
 *
 * 边界声明�?
 *  - 仅作用于�?APP 进程内的 CookieManager 调用
 *  - 不修改系�?Cookie 存储，仅在读取时过滤
 */
object CookieCleanHook {

    /** 追踪�?Cookie 名关键字（匹配大小写不敏感） */
    private val TRACKING_COOKIE_KEYS = arrayOf(
        "_ga", "_gid", "_gat", "_gcl_au",           // Google Analytics
        "IDE", "uid", "trk", "track",               // 通用追踪
        "__qca", "_fbp", "fr",                       // Quantcast/Facebook
        "tapad_tid", "tdid",                         // Tapad/TalkingData
        "um_distinct_id", "umt",                     // Umeng
        "Hm_lvt_", "Hm_lpvt_",                       // 百度统计
        "sajssdk_", "sensorsdata",                   // 神策
        "arr_affinity", "arr_session_id",            // Array
        "_drtid", "_clck", "_clsk",                  // ClearShift
        "ANON_ID", "ANON_ID_old"                     // AudienceNetwork
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.cookieCleanEnabled) return
        LogX.i("【实验性】CookieCleanHook 启动（应用进程内�?)

        hookCookieManagerGet(lpparam)
    }

    private fun hookCookieManagerGet(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cm = XposedHelpers.findClassIfExists(
                "android.webkit.CookieManager", lpparam.classLoader) ?: return

            // getCookie(String url)
            try {
                XposedHelpers.findAndHookMethod(cm, "getCookie",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val raw = p.result as? String ?: return
                            val cleaned = filterTrackingCookies(raw)
                            if (cleaned != raw) {
                                p.result = cleaned
                                LogX.d("[Cookie] 清理追踪 Cookie: ${raw.length} -> ${cleaned.length}")
                            }
                        }
                    })
                LogX.hookSuccess("CookieManager", "getCookie(url)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // getCookie(String url, String[] additionalInfo) API 26+
            try {
                XposedHelpers.findAndHookMethod(cm, "getCookie",
                    String::class.java, "android.webkit.WebviewDelegate\$WebViewFactoryPointer",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val raw = p.result as? String ?: return
                            p.result = filterTrackingCookies(raw)
                        }
                    })
                LogX.hookSuccess("CookieManager", "getCookie(url, factory)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // setCookie 记录但不阻断
            try {
                XposedHelpers.findAndHookMethod(cm, "setCookie",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("[Cookie] APP 设置 Cookie: ${p.args.getOrNull(0)}")
                        }
                    })
                LogX.hookSuccess("CookieManager", "setCookie")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("CookieCleanHook 异常", e)
        }
    }

    /** 过滤掉追踪类 Cookie */
    private fun filterTrackingCookies(raw: String): String {
        val parts = raw.split(";")
        val kept = parts.filter { part ->
            val name = part.trim().substringBefore("=", "").trim()
            if (name.isEmpty()) return@filter true
            !TRACKING_COOKIE_KEYS.any { key ->
                name.equals(key, ignoreCase = true) || name.startsWith(key, ignoreCase = true)
            }
        }
        return kept.joinToString(";")
    }
}
