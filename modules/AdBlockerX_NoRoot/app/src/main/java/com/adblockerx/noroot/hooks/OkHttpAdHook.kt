package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * OkHttp 广告拦截 Hook
 *
 * 拦截策略：
 *  1. RealCall.execute / enqueue：对广告域名请求直接返回空 Response 或抛异常跳过
 *  2. Interceptor.Chain.proceed：拦截器链中过滤广告请求
 *
 * 注意事项：
 *  - OkHttp 在不同 APP 中可能被混淆（class 名变为 a.b.c 等）
 *  - 使用 findClassIfExists 多候选类名 + try-catch 容错
 */
object OkHttpAdHook {

    private val REAL_CALL_CANDIDATES = listOf(
        "okhttp3.RealCall",
        "okhttp3.internal.connection.RealCall",
        "okhttp3.OkHttpClient\$RealCall"
    )

    private val RESPONSE_CLASS = "okhttp3.Response"
    private val RESPONSE_BUILDER_CLASS = "okhttp3.Response\$Builder"
    private val REQUEST_CLASS = "okhttp3.Request"
    private val HTTP_URL_CLASS = "okhttp3.HttpUrl"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.okHttpAdEnabled) return
        LogX.i("OkHttpAdHook 启动（应用进程内，多候选类名容错）")

        hookRealCall(lpparam)
        hookInterceptorChain(lpparam)
    }

    private fun hookRealCall(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in REAL_CALL_CANDIDATES) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue

            try {
                XposedHelpers.findAndHookMethod(clazz, "execute",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = try { extractRequestUrl(p.thisObject) } catch (_: Throwable) { null }
                            if (url != null && HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[OkHttp] 拦截 execute: $url")
                                p.result = buildEmptyResponse(lpparam, url)
                            }
                        }
                    })
                LogX.d("[OkHttp] 已 Hook $className.execute")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(clazz, "enqueue",
                    "okhttp3.Callback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = try { extractRequestUrl(p.thisObject) } catch (_: Throwable) { null }
                            if (url != null && HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[OkHttp] 拦截 enqueue: $url")
                                p.result = null
                            }
                        }
                    })
                LogX.d("[OkHttp] 已 Hook $className.enqueue")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun hookInterceptorChain(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chainClass = XposedHelpers.findClassIfExists(
            "okhttp3.Interceptor\$Chain", lpparam.classLoader) ?: return
        val requestClass = XposedHelpers.findClassIfExists(REQUEST_CLASS, lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(chainClass, "proceed",
                requestClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val req = p.args.getOrNull(0) ?: return
                        val url = try { extractUrlFromRequest(req) } catch (_: Throwable) { null }
                        if (url != null && HostsFilterHook.isUrlBlocked(url)) {
                            LogX.i("[OkHttp] 拦截 Chain.proceed: $url")
                            p.result = buildEmptyResponse(lpparam, url)
                        }
                    }
                })
            LogX.d("[OkHttp] 已 Hook Interceptor.Chain.proceed")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun extractRequestUrl(realCall: Any): String? {
        return try {
            val req = XposedHelpers.callMethod(realCall, "request") ?: return null
            extractUrlFromRequest(req)
        } catch (_: Throwable) { null }
    }

    private fun extractUrlFromRequest(req: Any): String? {
        return try {
            val url = XposedHelpers.callMethod(req, "url") ?: return null
            url.toString()
        } catch (_: Throwable) {
            try { req.toString() } catch (_: Throwable) { null }
        }
    }

    private fun buildEmptyResponse(lpparam: XC_LoadPackage.LoadPackageParam, url: String): Any? {
        return try {
            val respClass = XposedHelpers.findClassIfExists(RESPONSE_CLASS, lpparam.classLoader) ?: return null
            val builderClass = XposedHelpers.findClassIfExists(RESPONSE_BUILDER_CLASS, lpparam.classLoader) ?: return null
            val requestClass = XposedHelpers.findClassIfExists(REQUEST_CLASS, lpparam.classLoader) ?: return null
            val httpUrlClass = XposedHelpers.findClassIfExists(HTTP_URL_CLASS, lpparam.classLoader)

            val reqBuilder = XposedHelpers.callStaticMethod(requestClass, "newBuilder") ?: return null
            val fakeReq = try {
                if (httpUrlClass != null) {
                    val urlObj = XposedHelpers.callStaticMethod(httpUrlClass, "get", url)
                    XposedHelpers.callMethod(reqBuilder, "url", urlObj)
                }
                XposedHelpers.callMethod(reqBuilder, "build")
            } catch (_: Throwable) { null } ?: return null

            val builder = XposedHelpers.newInstance(builderClass)
            XposedHelpers.callMethod(builder, "request", fakeReq)
            XposedHelpers.callMethod(builder, "code", 404)
            try { XposedHelpers.callMethod(builder, "message", "AdBlockerX Blocked") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            try { XposedHelpers.callMethod(builder, "protocol",
                XposedHelpers.getStaticObjectField(
                    XposedHelpers.findClass("okhttp3.Protocol", lpparam.classLoader), "HTTP_1_1"))
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            XposedHelpers.callMethod(builder, "build")
        } catch (e: Throwable) {
            LogX.e("[OkHttp] 构造空 Response 异常", e)
            null
        }
    }
}
