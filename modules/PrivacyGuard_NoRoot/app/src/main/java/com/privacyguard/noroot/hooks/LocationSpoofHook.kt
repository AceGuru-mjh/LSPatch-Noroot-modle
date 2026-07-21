package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 位置伪造Hook（仅应用层，无法影响系统全局�?
 *
 * 硬性限制：
 *  - 仅修改传�?APP �?Location 对象，不修改系统 GPS 定位
 *  - 不影响系�?LBS 服务、紧急呼叫定位、其�?APP 的真实定�?
 *  - APP 通过 native 直接�?GPS 原始数据时本Hook无效
 *  - 不调�?Shizuku，无系统级操�?
 *
 * 拦截路径�?
 *  1. LocationManager.getLastKnownLocation
 *  2. LocationManager.requestLocationUpdates (回调 Location)
 *  3. Location 构造函数（修改经纬度字段）
 */
object LocationSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.locationSpoofEnabled) return
        LogX.i("位置伪造启动（仅应用层）：lat=${cfg.spoofLatitude} lng=${cfg.spoofLongitude}")

        hookLocationManager(lpparam, cfg.spoofLatitude, cfg.spoofLongitude)
        hookLocationConstructor(lpparam, cfg.spoofLatitude, cfg.spoofLongitude)
    }

    /** Hook LocationManager 拦截定位获取 */
    private fun hookLocationManager(
        lpparam: XC_LoadPackage.LoadPackageParam,
        lat: Double, lng: Double) {
        try {
            val lm = XposedHelpers.findClassIfExists(
                "android.location.LocationManager", lpparam.classLoader) ?: return

            // getLastKnownLocation(String provider)
            try {
                XposedHelpers.findAndHookMethod(lm, "getLastKnownLocation",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.result ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("LocationManager", "getLastKnownLocation")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getLastKnownLocation(String provider, LastLocationRequest)
            try {
                XposedHelpers.findAndHookMethod(lm, "getLastKnownLocation",
                    String::class.java, "android.location.LastLocationRequest",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.result ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("LocationManager", "getLastKnownLocation(API30+)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // requestLocationUpdates(long, float, Criteria, PendingIntent) 等多个重�?
            // 因重载数量较多，统一通过反射找所�?requestLocationUpdates 重载
            try {
                val methods = lm.declaredMethods.filter { it.name == "requestLocationUpdates" }
                for (m in methods) {
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                // requestLocationUpdates 的回调参数是 LocationListener
                                // 这里不直接修改回调，而是依赖 Location 构�?Hook 拦截
                                LogX.d("requestLocationUpdates 调用拦截: ${p.method}")
                            }
                        })
                    } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                }
                LogX.d("requestLocationUpdates ${methods.size} 个重载已Hook")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("LocationManager", "location", e)
        }
    }

    /** Hook Location 构造函数，修改经纬�?*/
    private fun hookLocationConstructor(
        lpparam: XC_LoadPackage.LoadPackageParam,
        lat: Double, lng: Double) {
        try {
            val locCls = XposedHelpers.findClassIfExists(
                "android.location.Location", lpparam.classLoader) ?: return

            // Location(String provider) 构�?
            try {
                XposedHelpers.findAndHookConstructor(locCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.thisObject ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("Location", "<init>(provider)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // Location(Location l) 拷贝构�?
            try {
                XposedHelpers.findAndHookConstructor(locCls, locCls,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.thisObject ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("Location", "<init>(copy)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // setLatitude / setLongitude
            try {
                XposedHelpers.findAndHookMethod(locCls, "setLatitude",
                    Double::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = lat
                        }
                    })
                LogX.hookSuccess("Location", "setLatitude")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(locCls, "setLongitude",
                    Double::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = lng
                        }
                    })
                LogX.hookSuccess("Location", "setLongitude")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Location", "constructor", e)
        }
    }

    /** 修改 Location 对象的经纬度字段 */
    private fun modifyLocation(loc: Any, lat: Double, lng: Double) {
        try {
            XposedHelpers.callMethod(loc, "setLatitude", lat)
            XposedHelpers.callMethod(loc, "setLongitude", lng)
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
