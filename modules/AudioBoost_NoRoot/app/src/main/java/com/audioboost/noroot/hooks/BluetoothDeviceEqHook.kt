package com.audioboost.noroot.hooks

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.media.audiofx.Equalizer
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import com.audioboost.noroot.utils.LogStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject

object BluetoothDeviceEqHook {

    private var currentDeviceId: String? = null
    private var deviceProfiles: Map<String, List<Int>> = emptyMap()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.btDeviceEqEnabled) return
        LogX.i("BluetoothDeviceEq: 已启用 蓝牙设备独立EQ")

        deviceProfiles = parseProfiles(cfg.btDeviceProfiles)
        LogX.i("BluetoothDeviceEq: 加载了 ${deviceProfiles.size} 个设备 Profile")

        try { LogStore.add("bteq", "蓝牙设备EQ: ${deviceProfiles.size} profiles") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookBluetoothAdapterGetRemoteDevice(lpparam, cfg)
        hookEqualizerSetBandLevel(lpparam, cfg)
    }

    private fun hookBluetoothAdapterGetRemoteDevice(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.bluetooth.BluetoothAdapter", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(cls, "getRemoteDevice",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val address = p.args[0] as? String ?: return
                        val device = p.result as? BluetoothDevice ?: return
                        val name = try { device.name } catch (_: Throwable) { "unknown" }
                        val deviceId = "$name ($address)"

                        if (deviceId != currentDeviceId) {
                            currentDeviceId = deviceId
                            LogX.i("BluetoothDeviceEq: 连接蓝牙设备 $deviceId")
                            applyDeviceEq(deviceId, cfg)
                        }
                    }
                })
            LogX.hookSuccess("BluetoothAdapter", "getRemoteDevice")
        } catch (e: Exception) {
            LogX.hookFailed("BluetoothAdapter", "getRemoteDevice", e)
        }
    }

    private fun hookEqualizerSetBandLevel(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.Equalizer", lpparam.classLoader
            ) ?: return
            if (!cfg.equalizerEnabled) return

            XposedHelpers.findAndHookMethod(cls, "setBandLevel",
                Short::class.javaPrimitiveType,
                Short::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val deviceId = currentDeviceId ?: return
                        val bands = deviceProfiles[deviceId] ?: return
                        val band = (p.args[0] as? Short)?.toInt() ?: return
                        if (band < bands.size) {
                            p.args[1] = bands[band].toShort()
                            LogX.d("BluetoothDeviceEq: band[$band] -> ${bands[band]} for $deviceId")
                        }
                    }
                })
            LogX.hookSuccess("Equalizer", "setBandLevel (BT override)")
        } catch (e: Exception) {
            LogX.w("BluetoothDeviceEq: Equalizer.setBandLevel 异常: ${e.message}")
        }
    }

    private fun applyDeviceEq(deviceId: String, cfg: AudioConfig) {
        val bands = deviceProfiles[deviceId] ?: return
        LogX.i("BluetoothDeviceEq: 应用设备 EQ $deviceId -> bands=$bands")
    }

    private fun parseProfiles(raw: List<String>): Map<String, List<Int>> {
        val map = mutableMapOf<String, List<Int>>()
        for (entry in raw) {
            try {
                val obj = JSONObject(entry)
                val device = obj.optString("deviceId", "")
                val bandsArr = obj.optJSONArray("eqBands") ?: continue
                val bands = mutableListOf<Int>()
                for (i in 0 until bandsArr.length()) {
                    bands.add(bandsArr.optInt(i, 0))
                }
                if (device.isNotEmpty() && bands.isNotEmpty()) {
                    map[device] = bands
                }
            } catch (_: Exception) { }
        }
        return map
    }
}
