package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * v1.0.6 新增（对标 AdClose）
 *
 * - 截图录屏限制移除：Hook Window.setFlags / SurfaceView，移除 FLAG_SECURE
 * - 摇一摇广告跳转禁用：Hook SensorManager，拦截加速度计高频事件
 * - VPN/代理检测绕过：Hook NetworkInfo/ConnectivityManager，返回非 VPN 状态
 */
object AdClosePlusHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.screenshotUnlockEnabled && !cfg.shakeAdBlockEnabled && !cfg.vpnDetectBypassEnabled) return
        LogX.i("AdClosePlus 启动 | 截图=${cfg.screenshotUnlockEnabled} 摇一摇=${cfg.shakeAdBlockEnabled} VPN绕过=${cfg.vpnDetectBypassEnabled}")

        if (cfg.screenshotUnlockEnabled) hookScreenshotUnlock(lpparam)
        if (cfg.shakeAdBlockEnabled) hookShakeAdBlock(lpparam)
        if (cfg.vpnDetectBypassEnabled) hookVpnDetectBypass(lpparam)
    }

    /** 截图录屏限制移除：Hook Window.setFlags 清除 FLAG_SECURE */
    private fun hookScreenshotUnlock(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val winCls = XposedHelpers.findClassIfExists("android.view.Window", lpparam.classLoader) ?: return
            // FLAG_SECURE = 0x00002000
            val FLAG_SECURE = 0x00002000

            // Hook setFlags(int flags, int mask)
            XposedHelpers.findAndHookMethod(winCls, "setFlags",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val flags = p.args[0] as Int
                            val mask = p.args[1] as Int
                            // 如果尝试设置 FLAG_SECURE，从 flags 和 mask 中移除
                            if (flags and FLAG_SECURE != 0 || mask and FLAG_SECURE != 0) {
                                p.args[0] = flags and FLAG_SECURE.inv()
                                p.args[1] = mask and FLAG_SECURE.inv()
                                LogX.d("[截图] 已移除 FLAG_SECURE")
                            }
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Window", "setFlags")

            // Hook addFlags 兼容
            XposedHelpers.findAndHookMethod(winCls, "addFlags", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val flags = p.args[0] as Int
                            if (flags and FLAG_SECURE != 0) {
                                p.args[0] = flags and FLAG_SECURE.inv()
                                LogX.d("[截图] 已移除 addFlags FLAG_SECURE")
                            }
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Window", "addFlags")

            // Hook SurfaceView.setSecure (Android 7+)
            try {
                val svCls = XposedHelpers.findClassIfExists("android.view.SurfaceView", lpparam.classLoader)
                if (svCls != null) {
                    XposedHelpers.findAndHookMethod(svCls, "setSecure", Boolean::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                p.args[0] = false
                                LogX.d("[截图] 已拦截 SurfaceView.setSecure")
                            }
                        })
                    LogX.hookSuccess("SurfaceView", "setSecure")
                }
            } catch (_: Throwable) {}
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** 摇一摇广告禁用：Hook SensorManager 加速度计，拦截高频事件 */
    private fun hookShakeAdBlock(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val smCls = XposedHelpers.findClassIfExists("android.hardware.SensorManager", lpparam.classLoader) ?: return
            // Hook registerListener，对加速度计( TYPE_ACCELEROMETER = 1 )返回 false（注册失败）
            XposedHelpers.findAndHookMethod(smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val sensor = p.args[1] ?: return
                            val type = XposedHelpers.callMethod(sensor, "getType") as Int
                            // TYPE_ACCELEROMETER=1, TYPE_LINEAR_ACCELERATION=10
                            if (type == 1 || type == 10) {
                                p.result = false
                                LogX.d("[摇一摇] 已拦截加速度计注册")
                            }
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** VPN/代理检测绕过：Hook NetworkInfo/ConnectivityManager 返回非 VPN */
    private fun hookVpnDetectBypass(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook NetworkInfo.getType() 返回非 TYPE_VPN(17)
            val niCls = XposedHelpers.findClassIfExists("android.net.NetworkInfo", lpparam.classLoader)
            if (niCls != null) {
                XposedHelpers.findAndHookMethod(niCls, "getType",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                if (p.result == 17) {  // TYPE_VPN
                                    p.result = 1  // TYPE_WIFI
                                    LogX.d("[VPN] 已伪装为 WIFI")
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                XposedHelpers.findAndHookMethod(niCls, "getSubtype",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                if (p.result == 0) p.result = 1
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("NetworkInfo", "getType")
            }

            // Hook ConnectivityManager.getNetworkInfo
            val cmCls = XposedHelpers.findClassIfExists("android.net.ConnectivityManager", lpparam.classLoader)
            if (cmCls != null) {
                XposedHelpers.findAndHookMethod(cmCls, "getNetworkInfo", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                if (p.args[0] == 17) {  // 查询 TYPE_VPN
                                    p.args[0] = 1  // 改查 TYPE_WIFI
                                    LogX.d("[VPN] 已拦截 VPN 网络查询")
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("ConnectivityManager", "getNetworkInfo")
            }

            // Hook SystemProperties.get("net.dns1") 等 VPN 特征
            try {
                val spCls = XposedHelpers.findClassIfExists("android.os.SystemProperties", lpparam.classLoader)
                if (spCls != null) {
                    XposedHelpers.findAndHookMethod(spCls, "get", String::class.java,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val key = p.args[0] as? String ?: return
                                    if (key.startsWith("net.dns") || key.contains("vpn")) {
                                        p.result = "8.8.8.8"
                                    }
                                } catch (_: Throwable) {}
                            }
                        })
                }
            } catch (_: Throwable) {}
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
