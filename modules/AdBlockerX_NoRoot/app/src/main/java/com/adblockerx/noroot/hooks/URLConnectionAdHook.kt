package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.AdBlockList
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.net.URL

/**
 * URLConnection / HttpURLConnection 广告拦截 Hook
 *
 * 拦截策略：
 *  1. URL.openConnection：对广告域名的 URL 抛 IOException 跳过
 *  2. HttpURLConnection.connect：广告域名直接抛异常
 *  3. HttpURLConnection.getResponseCode：返回 404
 *  4. HttpsURLConnection 同理
 */
object URLConnectionAdHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.urlConnectionAdEnabled) return
        LogX.i("URLConnectionAdHook 启动（应用进程内）")

        hookUrlOpenConnection(lpparam)
        hookHttpURLConnection(lpparam)
    }

    private fun hookUrlOpenConnection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val urlClass = XposedHelpers.findClassIfExists(
                "java.net.URL", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(urlClass, "openConnection",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.thisObject as? URL ?: return
                            val host = url.host ?: return
                            if (HostsFilterHook.isBlocked(host)) {
                                LogX.i("[URLConnection] 拦截 openConnection: $host")
                                p.result = null
                                p.throwable = java.io.IOException("AdBlockerX blocked: $host")
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                val proxyClass = XposedHelpers.findClassIfExists(
                    "java.net.Proxy", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookMethod(urlClass, "openConnection",
                    proxyClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.thisObject as? URL ?: return
                            val host = url.host ?: return
                            if (HostsFilterHook.isBlocked(host)) {
                                LogX.i("[URLConnection] 拦截 openConnection(Proxy): $host")
                                p.throwable = java.io.IOException("AdBlockerX blocked: $host")
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("URLConnectionAdHook.openConnection 异常", e)
        }
    }

    private fun hookHttpURLConnection(lpparam: XC_LoadPackage.LoadPackageParam) {
        val httpUrlConnClass = XposedHelpers.findClassIfExists(
            "java.net.HttpURLConnection", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(httpUrlConnClass, "connect",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val host = extractHostFromConn(p.thisObject) ?: return
                        if (HostsFilterHook.isBlocked(host)) {
                            LogX.i("[HttpURLConnection] 拦截 connect: $host")
                            p.throwable = java.io.IOException("AdBlockerX blocked: $host")
                        }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(httpUrlConnClass, "getResponseCode",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val host = extractHostFromConn(p.thisObject) ?: return
                        if (HostsFilterHook.isBlocked(host)) {
                            LogX.i("[HttpURLConnection] 拦截 getResponseCode: $host -> 404")
                            p.result = 404
                        }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(httpUrlConnClass, "getInputStream",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val host = extractHostFromConn(p.thisObject) ?: return
                        if (HostsFilterHook.isBlocked(host)) {
                            LogX.i("[HttpURLConnection] 拦截 getInputStream: $host -> empty")
                            p.result = ByteArrayInputStream(ByteArray(0))
                        }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            val httpsClass = XposedHelpers.findClassIfExists(
                "javax.net.ssl.HttpsURLConnection", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(httpsClass, "getResponseCode",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val host = extractHostFromConn(p.thisObject) ?: return
                        if (HostsFilterHook.isBlocked(host)) {
                            LogX.i("[HttpsURLConnection] 拦截 getResponseCode: $host -> 404")
                            p.result = 404
                        }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun extractHostFromConn(conn: Any): String? {
        return try {
            val url = XposedHelpers.callMethod(conn, "getURL") as? URL ?: return null
            AdBlockList.extractHost(url.toString())
        } catch (_: Throwable) { null }
    }
}
