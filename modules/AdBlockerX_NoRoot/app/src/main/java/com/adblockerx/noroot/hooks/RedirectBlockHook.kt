package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.AdBlockList
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】重定向拦截 Hook
 *
 * 拦截策略：
 *  - Hook WebViewClient.shouldOverrideUrlLoading 拦截广告跳转深链
 *  - 重点拦截 scheme:// 跳转（taobao://、tmall://、jd://、pinduoduo:// 等）
 *  - 拦截包含 ad/click/redirect 关键字的 URL
 *
 * 边界声明：
 *  - 仅作用于本 APP 进程内的 WebView 跳转
 *  - 不影响系统其他 APP 的 DeepLink
 */
object RedirectBlockHook {

    /** 广告跳转 URL 关键字 */
    private val REDIRECT_KEYWORDS = arrayOf(
        "/ad/", "/ads/", "click", "redirect", "tracker",
        "doubleclick", "googlesyndication",
        "ad.toutiao", "pdp.toutiao",
        "pgdt.ugdtimg", "t.gdt.qq",
        "adsame", "mediav",
        "/jump", "/adclick", "/click?",
        "admaster", "miaozhen"
    )

    /** 拦截的 DeepLink scheme（不阻断，仅记录） */
    private val AD_DEEPLINK_SCHEMES = arrayOf(
        "tbopen://", "tmall://", "jd://", "openapp://",
        "wechat://", "alipays://", "pinduoduo://",
        "snssdk1128://", "snssdk1112://"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.redirectBlockEnabled) return
        LogX.i("【实验性】RedirectBlockHook 启动（应用进程内）")

        hookShouldOverrideUrlLoading(lpparam)
        hookWebViewLoadUrlForRedirect(lpparam)
    }

    private fun hookShouldOverrideUrlLoading(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return

            // 旧版 API
            try {
                XposedHelpers.findAndHookMethod(wvcClass, "shouldOverrideUrlLoading",
                    "android.webkit.WebView", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(1) as? String ?: return
                            if (shouldBlock(url)) {
                                LogX.i("[Redirect] 拦截跳转: $url")
                                p.result = true
                            }
                        }
                    })
                LogX.hookSuccess("WebViewClient", "shouldOverrideUrlLoading(String)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // 新版 API
            try {
                val wrrClass = XposedHelpers.findClassIfExists(
                    "android.webkit.WebResourceRequest", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookMethod(wvcClass, "shouldOverrideUrlLoading",
                    "android.webkit.WebView", wrrClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val req = p.args.getOrNull(1) ?: return
                            val url = try {
                                XposedHelpers.callMethod(req, "getUrl")?.toString()
                            } catch (_: Throwable) { null } ?: return
                            if (shouldBlock(url)) {
                                LogX.i("[Redirect] 拦截跳转: $url")
                                p.result = true
                            }
                        }
                    })
                LogX.hookSuccess("WebViewClient", "shouldOverrideUrlLoading(Request)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("RedirectBlockHook.shouldOverrideUrlLoading 异常", e)
        }
    }

    private fun hookWebViewLoadUrlForRedirect(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(0) as? String ?: return
                            if (shouldBlock(url)) {
                                LogX.i("[Redirect] 拦截 loadUrl: $url")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess("WebView", "loadUrl(redirect)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("RedirectBlockHook.loadUrl 异常", e)
        }
    }

    /** 判断 URL 是否应该被拦截 */
    private fun shouldBlock(url: String): Boolean {
        if (url.isBlank()) return false

        // 1. 命中广告黑名单
        val host = AdBlockList.extractHost(url)
        if (host != null && HostsFilterHook.isBlocked(host)) return true

        // 2. URL 中包含广告跳转关键字
        val lower = url.lowercase()
        if (REDIRECT_KEYWORDS.any { lower.contains(it) }) return true

        // 3. 广告 DeepLink scheme
        if (AD_DEEPLINK_SCHEMES.any { lower.startsWith(it) }) {
            // DeepLink 不直接阻断（避免影响正常功能），仅当 URL 同时包含广告关键字时阻断
            if (REDIRECT_KEYWORDS.any { lower.contains(it) }) return true
        }

        return false
    }
}
