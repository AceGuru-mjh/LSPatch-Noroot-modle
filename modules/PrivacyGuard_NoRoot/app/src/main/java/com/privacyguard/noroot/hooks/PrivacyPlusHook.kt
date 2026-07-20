package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * v1.0.6 新增（对标 HideMyAndroid / 伪造安装模块）
 *
 * - 应用安装状态伪造：Hook PackageManager.getPackageInfo，让目标APP认为指定包未安装
 *   用于绕过"应用强制安装检测"或隐藏已安装的敏感应用
 * - Mock位置系统级：Hook LocationManager 系统服务返回值（NoRoot 版仅应用进程内）
 */
object PrivacyPlusHook {

    /** 用户想要"隐藏已安装"的包名（目标APP检测这些包时返回未安装） */
    private val HIDE_INSTALLED_PKGS = arrayOf(
        "org.lsposed.manager", "org.lsposed.lspatch",
        "moe.shizuku.privileged.api", "rikka.shizuku.manager",
        "de.robv.android.xposed.installer",
        "bin.mt.plus", "bin.mt.plus.canary",
        "me.piebridge.brevent",
        "com.topjohnwu.magisk", "io.github.huskydg.magisk",
        "com.koushikdutta.rommanager"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.installStatusSpoofEnabled && !cfg.mockLocationSystemLevelEnabled) return
        LogX.i("PrivacyPlus 启动 | 安装伪造=${cfg.installStatusSpoofEnabled} Mock位置=${cfg.mockLocationSystemLevelEnabled}")

        if (cfg.installStatusSpoofEnabled) hookInstallStatus(lpparam)
        if (cfg.mockLocationSystemLevelEnabled) hookMockLocationSystem(lpparam, cfg)
    }

    /** 应用安装状态伪造：Hook getPackageInfo 抛 NameNotFoundException */
    private fun hookInstallStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pmCls = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            XposedHelpers.findAndHookMethod(pmCls, "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val name = p.args[0] as? String ?: return
                            if (HIDE_INSTALLED_PKGS.any { name.contains(it, true) }) {
                                LogX.d("[安装伪造] 隐藏已安装: $name")
                                throw android.content.pm.PackageManager.NameNotFoundException(name)
                            }
                        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                            throw e
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("PackageManager", "getPackageInfo")

            // Hook getInstalledApplications 过滤
            XposedHelpers.findAndHookMethod(pmCls, "getInstalledApplications",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val list = p.result as? MutableList<*> ?: return
                            val filtered = list.filter { item ->
                                try {
                                    val f = item?.javaClass?.getDeclaredField("packageName")
                                    f?.isAccessible = true
                                    val name = f?.get(item) as? String ?: return@filter true
                                    !HIDE_INSTALLED_PKGS.any { name.contains(it, true) }
                                } catch (_: Throwable) { true }
                            }
                            p.result = java.util.ArrayList(filtered)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("PackageManager", "getInstalledApplications")

            // Hook getInstalledPackages
            XposedHelpers.findAndHookMethod(pmCls, "getInstalledPackages",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val list = p.result as? MutableList<*> ?: return
                            val filtered = list.filter { item ->
                                try {
                                    val f = item?.javaClass?.getDeclaredField("packageName")
                                    f?.isAccessible = true
                                    val name = f?.get(item) as? String ?: return@filter true
                                    !HIDE_INSTALLED_PKGS.any { name.contains(it, true) }
                                } catch (_: Throwable) { true }
                            }
                            p.result = java.util.ArrayList(filtered)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("PackageManager", "getInstalledPackages")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Mock 位置系统级：Hook LocationManager 全局返回伪造坐标 */
    private fun hookMockLocationSystem(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val lmCls = XposedHelpers.findClassIfExists(
                "android.location.LocationManager", lpparam.classLoader) ?: return

            // Hook getLastKnownLocation 返回伪造坐标
            XposedHelpers.findAndHookMethod(lmCls, "getLastKnownLocation",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.result ?: return
                            val lat = XposedHelpers.callMethod(result, "getLatitude") as Double
                            val lng = XposedHelpers.callMethod(result, "getLongitude") as Double
                            // 仅修改 GPS provider 的结果
                            if (p.args[0] == "gps" || p.args[0] == "network") {
                                XposedHelpers.callMethod(result, "setLatitude", cfg.spoofLatitude)
                                XposedHelpers.callMethod(result, "setLongitude", cfg.spoofLongitude)
                                XposedHelpers.callMethod(result, "setAccuracy", 5.0f)
                                LogX.d("[Mock位置] getLastKnownLocation 修改为 ${cfg.spoofLatitude},${cfg.spoofLongitude}")
                            }
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("LocationManager", "getLastKnownLocation")

            // Hook getCurrentLocation (Android 11+)
            try {
                XposedHelpers.findAndHookMethod(lmCls, "getCurrentLocation",
                    "android.location.LocationRequest",
                    "android.location.CancellationSignal",
                    "java.util.concurrent.Executor",
                    "android.location.LocationConsumer",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("[Mock位置] getCurrentLocation 已Hook")
                        }
                    })
                LogX.hookSuccess("LocationManager", "getCurrentLocation")
            } catch (_: Throwable) {}

            // Hook requestLocationUpdates 拦截，返回伪造 Location
            try {
                XposedHelpers.findAndHookMethod(lmCls, "requestLocationUpdates",
                    String::class.java, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    "android.location.LocationListener",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val listener = p.args[3] ?: return
                                // 构造伪造 Location
                                val locCls = XposedHelpers.findClass("android.location.Location", lpparam.classLoader)
                                val loc = XposedHelpers.newInstance(locCls, "gps")
                                XposedHelpers.callMethod(loc, "setLatitude", cfg.spoofLatitude)
                                XposedHelpers.callMethod(loc, "setLongitude", cfg.spoofLongitude)
                                XposedHelpers.callMethod(loc, "setAccuracy", 5.0f)
                                XposedHelpers.callMethod(loc, "setTime", System.currentTimeMillis())
                                // 延迟回调
                                Thread {
                                    try {
                                        Thread.sleep(500)
                                        XposedHelpers.callMethod(listener, "onLocationChanged", loc)
                                    } catch (_: Throwable) {}
                                }.start()
                                LogX.d("[Mock位置] requestLocationUpdates 注入伪造坐标")
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("LocationManager", "requestLocationUpdates")
            } catch (_: Throwable) {}
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
