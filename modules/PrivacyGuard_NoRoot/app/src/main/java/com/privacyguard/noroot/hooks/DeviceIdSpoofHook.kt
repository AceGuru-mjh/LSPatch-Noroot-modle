package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.FakeDeviceCache
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 设备ID伪造Hook（仅应用层，无法影响系统全局）
 *
 * 硬性限制：
 *  - 仅 Hook Java 层 API，无法拦截 Native 层直接读取 /proc 或 ioctl 的检测
 *  - 伪造值仅在当前进程生命周期内稳定，进程重启后重新随机
 *  - 不修改系统属性，不写 /system 或 /sys 文件
 *  - 不调用 Shizuku，无系统级操作
 *
 * 拦截路径：
 *  1. TelephonyManager.getDeviceId / getImei / getMeid / getSubscriberId / getLine1Number
 *  2. Settings.Secure.getString(ANDROID_ID)
 *  3. WifiInfo.getMacAddress / getBSSID
 *  4. Build.getSerial
 */
object DeviceIdSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.deviceIdSpoofEnabled) return
        LogX.i("设备ID伪造启动（仅应用层）")

        hookTelephonyManager(lpparam)
        hookSettingsSecure(lpparam)
        hookWifiInfo(lpparam)
        hookBuildSerial(lpparam)
    }

    /** Hook TelephonyManager 设备标识相关方法 */
    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tm = XposedHelpers.findClassIfExists(
                "android.telephony.TelephonyManager", lpparam.classLoader) ?: return

            // getDeviceId() (deprecated but 仍被部分APP使用)
            try {
                XposedHelpers.findAndHookMethod(tm, "getDeviceId", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeImei
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getDeviceId")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getDeviceId(int slot)
            try {
                XposedHelpers.findAndHookMethod(tm, "getDeviceId",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = FakeDeviceCache.fakeImei
                        }
                    })
                LogX.hookSuccess("TelephonyManager", "getDeviceId(slot)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getImei()
            try {
                XposedHelpers.findAndHookMethod(tm, "getImei", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeImei
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getImei")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getImei(int slot)
            try {
                XposedHelpers.findAndHookMethod(tm, "getImei",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = FakeDeviceCache.fakeImei
                        }
                    })
                LogX.hookSuccess("TelephonyManager", "getImei(slot)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getMeid()
            try {
                XposedHelpers.findAndHookMethod(tm, "getMeid", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeMeid
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getMeid")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getMeid(int slot)
            try {
                XposedHelpers.findAndHookMethod(tm, "getMeid",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = FakeDeviceCache.fakeMeid
                        }
                    })
                LogX.hookSuccess("TelephonyManager", "getMeid(slot)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getSubscriberId()
            try {
                XposedHelpers.findAndHookMethod(tm, "getSubscriberId", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSubscriberId
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getSubscriberId")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getLine1Number()
            try {
                XposedHelpers.findAndHookMethod(tm, "getLine1Number", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeLine1Number
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getLine1Number")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getSimSerialNumber()
            try {
                XposedHelpers.findAndHookMethod(tm, "getSimSerialNumber", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSimSerial
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getSimSerialNumber")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("TelephonyManager", "device-id", e)
        }
    }

    /** Hook Settings.Secure.getString 拦截 ANDROID_ID */
    private fun hookSettingsSecure(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ss = XposedHelpers.findClassIfExists(
                "android.provider.Settings.Secure", lpparam.classLoader) ?: return
            val ANDROID_ID = "android_id"

            // getString(ContentResolver, String name)
            try {
                XposedHelpers.findAndHookMethod(ss, "getString",
                    "android.content.ContentResolver", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[1] as? String ?: return
                            if (name == ANDROID_ID) {
                                p.result = FakeDeviceCache.fakeAndroidId
                            }
                        }
                    })
                LogX.hookSuccess("Settings.Secure", "getString(ANDROID_ID)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getString(ContentResolver, String name, String def)
            try {
                XposedHelpers.findAndHookMethod(ss, "getString",
                    "android.content.ContentResolver", String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[1] as? String ?: return
                            if (name == ANDROID_ID) {
                                p.result = FakeDeviceCache.fakeAndroidId
                            }
                        }
                    })
                LogX.hookSuccess("Settings.Secure", "getString(ANDROID_ID, def)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Settings.Secure", "getString", e)
        }
    }

    /** Hook WifiInfo MAC/BSSID */
    private fun hookWifiInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wifi = XposedHelpers.findClassIfExists(
                "android.net.wifi.WifiInfo", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wifi, "getMacAddress", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeMacAddress
                    }
                })
                LogX.hookSuccess("WifiInfo", "getMacAddress")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wifi, "getBSSID", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeBssid
                    }
                })
                LogX.hookSuccess("WifiInfo", "getBSSID")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("WifiInfo", "mac/bssid", e)
        }
    }

    /** Hook Build.getSerial (Android 9+ deprecated) */
    private fun hookBuildSerial(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val build = XposedHelpers.findClassIfExists(
                "android.os.Build", lpparam.classLoader) ?: return

            // Build.getSerial() 静态方法
            try {
                XposedHelpers.findAndHookMethod(build, "getSerial", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSerial
                    }
                })
                LogX.hookSuccess("Build", "getSerial")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // 直接覆盖 Build.SERIAL 静态字段
            try {
                XposedHelpers.setStaticObjectField(build, "SERIAL", FakeDeviceCache.fakeSerial)
                LogX.d("Build.SERIAL 字段已覆盖")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Build", "getSerial", e)
        }
    }
}
