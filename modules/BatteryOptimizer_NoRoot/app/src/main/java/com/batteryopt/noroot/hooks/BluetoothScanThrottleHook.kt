package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * 【实验性】蓝牙扫描降�?Hook（应用层�?
 *
 * 功能�?
 *  - Hook BluetoothLeScanner.startScan 系列重载
 *  - 对高�?BLE 扫描�?authority 节流（默认最小间�?60s�?
 *  - Hook BluetoothAdapter.startDiscovery 周边扫描，限制频�?
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP �?BLE 扫描请求
 *  - 不能修改系统 BluetoothManager 全局策略
 *  - 不影响系统级蓝牙扫描（如 Beacon 服务、定位辅助）
 *
 * 注意�?
 *  - 蓝牙扫描（特别是 BLE）会消耗显著电量，频繁扫描�?APP 是耗电大户
 *  - 限频可能影响部分蓝牙交互（如设备发现），属于预期效果
 */
object BluetoothScanThrottleHook {

    /** 记录上次扫描时间，按调用来源节流 */
    private val lastScanTs = ConcurrentHashMap<String, Long>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】蓝牙扫描降频启�?| 最小间�?${cfg.bluetoothScanMinIntervalMs}ms")

        hookStartScan(lpparam, cfg)
        hookStartDiscovery(lpparam, cfg)
    }

    /** Hook BluetoothLeScanner.startScan 多个重载 */
    private fun hookStartScan(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.bluetooth.le.BluetoothLeScanner", lpparam.classLoader
        ) ?: return

        // startScan(ScanCallback)
        try {
            XposedHelpers.findAndHookMethod(
                cls, "startScan",
                "android.bluetooth.le.ScanCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("ble_startScan_cb", cfg)) {
                            p.result = null
                            LogX.w("BLE startScan(callback) 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothLeScanner", "startScan(callback)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // startScan(List<ScanFilter>, ScanSettings, ScanCallback)
        try {
            XposedHelpers.findAndHookMethod(
                cls, "startScan",
                "java.util.List",
                "android.bluetooth.le.ScanSettings",
                "android.bluetooth.le.ScanCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("ble_startScan_filter", cfg)) {
                            p.result = null
                            LogX.w("BLE startScan(filters,settings,callback) 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothLeScanner", "startScan(filters,settings,callback)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // startScan(List<ScanFilter>, ScanSettings, PendingIntent) API29+
        try {
            XposedHelpers.findAndHookMethod(
                cls, "startScan",
                "java.util.List",
                "android.bluetooth.le.ScanSettings",
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("ble_startScan_pi", cfg)) {
                            p.result = 0
                            LogX.w("BLE startScan(filters,settings,intent) 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothLeScanner", "startScan(filters,settings,intent)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook BluetoothAdapter.startDiscovery */
    private fun hookStartDiscovery(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.bluetooth.BluetoothAdapter", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "startDiscovery",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("bt_startDiscovery", cfg)) {
                            p.result = false
                            LogX.w("BluetoothAdapter.startDiscovery 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothAdapter", "startDiscovery")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** 判断是否需要节流（�?key 维度，超过最小间隔才放行�?*/
    private fun shouldThrottle(key: String, cfg: BatteryConfig): Boolean {
        val now = System.currentTimeMillis()
        val last = lastScanTs[key] ?: 0L
        return if (now - last < cfg.bluetoothScanMinIntervalMs) {
            true
        } else {
            lastScanTs[key] = now
            false
        }
    }
}
