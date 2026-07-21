package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.DeviceProfile
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogStore
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 单游戏机型伪�?Hook（仅作用于当前被 Hook 的游戏进程）
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅修改当前进程内�?Build 类属性，退出游戏后恢复真实参数
 *  - �?Hook Java �?SystemProperties.get 方法，不修改底层属性文�?
 *  - 不调�?Shizuku setprop
 *
 * 拦截路径�?
 *  1. android.os.Build 静态字�?-> 游戏读取 MODEL/BRAND/MANUFACTURER
 *  2. SystemProperties.get() -> 游戏读取 ro.product.* 属�?
 *  3. TelephonyManager.getDeviceId -> 伪装设备 ID
 *  4. Runtime.availableProcessors() -> 伪装 CPU 核心�?
 */
object DeviceSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.deviceSpoofEnabled) return

        // 优先使用自定义机型，其次内置机型，最后默认小�?5
        val profile: DeviceProfile = cfg.customDeviceProfile
            ?: com.gameunlocker.noroot.utils.DeviceProfileDatabase.findById(cfg.selectedDeviceProfileId)
            ?: com.gameunlocker.noroot.utils.DeviceProfileDatabase.findById("xiaomi15")
            ?: return

        LogX.i("机型伪装: ${profile.displayName}（仅应用层）")
        try { LogStore.add("spoofed", "伪装机型") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        spoofBuildFields(profile)
        spoofSystemProperties(profile)
        spoofTelephony(lpparam)
        spoofCpuInfo(profile)
    }

    /** 直接修改 Build 类静态字段的�?*/
    private fun spoofBuildFields(profile: DeviceProfile) {
        try {
            val build = XposedHelpers.findClass("android.os.Build", null)
            val fields = mapOf(
                "MODEL" to profile.model,
                "BRAND" to profile.brand,
                "MANUFACTURER" to profile.manufacturer,
                "DEVICE" to profile.device,
                "PRODUCT" to profile.product,
                "BOARD" to profile.board,
                "HARDWARE" to profile.hardware,
                "FINGERPRINT" to profile.fingerprint,
                "DISPLAY" to "${profile.brand} ${profile.model}"
            )
            for ((name, value) in fields) {
                try {
                    XposedHelpers.setStaticObjectField(build, name, value)
                } catch (_: Throwable) {
                    // 部分 ROM 字段�?final，忽略单字段失败
                }
            }
            LogX.i("Build 属性伪装完�? MODEL=${profile.model}")
        } catch (e: Throwable) {
            LogX.e("Build 伪装异常", e)
        }
    }

    /** Hook SystemProperties 拦截属性读�?*/
    private fun spoofSystemProperties(profile: DeviceProfile) {
        try {
            val sp = XposedHelpers.findClassIfExists("android.os.SystemProperties", null) ?: return
            val props = mapOf(
                "ro.product.model" to profile.model,
                "ro.product.brand" to profile.brand,
                "ro.product.manufacturer" to profile.manufacturer,
                "ro.product.device" to profile.device,
                "ro.product.name" to profile.product,
                "ro.product.board" to profile.board,
                "ro.hardware" to profile.hardware,
                "ro.build.version.release" to profile.androidVersion,
                "ro.build.version.sdk" to profile.sdkVersion.toString(),
                "ro.build.fingerprint" to profile.fingerprint,
                "ro.soc.model" to profile.cpuModel,
                "ro.chipname" to profile.cpuModel
            )

            // get(String, String)
            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            if (props.containsKey(key)) p.result = props[key]
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // get(String)
            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            if (props.containsKey(key)) p.result = props[key]
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("SystemProperties", "get(${props.size} props)")
        } catch (e: Throwable) {
            LogX.hookFailed("SystemProperties", "get", e)
        }
    }

    /** 伪装 TelephonyManager 设备 ID */
    private fun spoofTelephony(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tm = XposedHelpers.findClassIfExists(
                "android.telephony.TelephonyManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(tm, "getDeviceId",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = "000000000000000"
                        }
                    })
                LogX.hookSuccess("TelephonyManager", "getDeviceId")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("TelephonyManager", "getDeviceId", e)
        }
    }

    /** 伪装 CPU 核心�?*/
    private fun spoofCpuInfo(profile: DeviceProfile) {
        try {
            val rt = XposedHelpers.findClassIfExists("java.lang.Runtime", null) ?: return
            XposedHelpers.findAndHookMethod(rt, "availableProcessors",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        // 旗舰机通常 8 核以上，返回 8 保证游戏认为是大核心机型
                        p.result = 8
                    }
                })
            LogX.hookSuccess("Runtime", "availableProcessors -> 8")
        } catch (e: Throwable) {
            LogX.hookFailed("Runtime", "availableProcessors", e)
        }
    }
}
