package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LocationManager 定位优化 Hook（应用层�?
 *
 * 功能�?
 *  1. Hook requestLocationUpdates，对高频定位请求降频（minTimeInterval 提至 30s�?
 *  2. 对后台高�?GPS 请求降级为网络定位（节省 GPS 芯片功耗）
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP 的定位请求，不能修改系统 LocationManagerService
 *  - 不影响系统级定位、紧急呼叫定位等
 *  - 导航�?APP（如地图）建议关闭此优化避免功能受损
 */
object LocationOptHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Location 定位优化启动 | 最小间�?${cfg.locationMinIntervalMs}ms GPS降级=${cfg.locationDowngradeGps}")

        hookRequestLocationUpdates(lpparam, cfg)
    }

    private fun hookRequestLocationUpdates(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        val lmCls = XposedHelpers.findClassIfExists(
            "android.location.LocationManager", lpparam.classLoader
        ) ?: return

        // 重载1: requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener)
        try {
            XposedHelpers.findAndHookMethod(
                lmCls, "requestLocationUpdates",
                String::class.java,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                "android.location.LocationListener",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val provider = p.args[0] as? String ?: return
                        var minTime = p.args[1] as Long
                        if (minTime < cfg.locationMinIntervalMs) {
                            val old = minTime
                            p.args[1] = cfg.locationMinIntervalMs
                            LogX.w("定位间隔放大: $provider ${old}ms -> ${cfg.locationMinIntervalMs}ms")
                            minTime = cfg.locationMinIntervalMs
                        }
                        if (cfg.locationDowngradeGps &&
                            provider == "gps" && minTime < 60_000L
                        ) {
                            p.args[0] = "network"
                            LogX.w("GPS 高频定位降级�?NETWORK 定位")
                        }
                    }
                })
            LogX.hookSuccess("LocationManager", "requestLocationUpdates(provider,minTime,minDistance,listener)")
        } catch (e: Exception) {
            LogX.e("Hook requestLocationUpdates(4�? 异常", e)
        }

        // 重载2: requestLocationUpdates(long minTime, float minDistance, Criteria criteria, LocationListener listener, Looper looper)
        try {
            XposedHelpers.findAndHookMethod(
                lmCls, "requestLocationUpdates",
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                "android.location.Criteria",
                "android.location.LocationListener",
                "android.os.Looper",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val minTime = p.args[0] as Long
                        if (minTime < cfg.locationMinIntervalMs) {
                            val old = minTime
                            p.args[0] = cfg.locationMinIntervalMs
                            LogX.w("定位间隔放大(Criteria): ${old}ms -> ${cfg.locationMinIntervalMs}ms")
                        }
                    }
                })
            LogX.hookSuccess("LocationManager", "requestLocationUpdates(minTime,minDistance,Criteria,listener,looper)")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }

        // 重载3: requestLocationUpdates(String provider, long minTime, float minDistance, PendingIntent intent)
        try {
            XposedHelpers.findAndHookMethod(
                lmCls, "requestLocationUpdates",
                String::class.java,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val provider = p.args[0] as? String ?: return
                        val minTime = p.args[1] as Long
                        if (minTime < cfg.locationMinIntervalMs) {
                            val old = minTime
                            p.args[1] = cfg.locationMinIntervalMs
                            LogX.w("定位间隔放大(PI): $provider ${old}ms -> ${cfg.locationMinIntervalMs}ms")
                        }
                        if (cfg.locationDowngradeGps &&
                            provider == "gps" && minTime < 60_000L
                        ) {
                            p.args[0] = "network"
                            LogX.w("GPS(PendingIntent) 降级�?NETWORK")
                        }
                    }
                })
            LogX.hookSuccess("LocationManager", "requestLocationUpdates(provider,minTime,minDistance,intent)")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
