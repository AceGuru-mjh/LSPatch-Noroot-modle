package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.FakeDeviceCache
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】网络信息伪造
 *
 * 伪造本机IP地址、DNS等信息，防止网络指纹追踪。
 *
 * 硬性限制：仅修改传给当前APP的Java层网络信息查询结果，
 *          不影响真实网络连接和底层socket。
 */
object NetworkInfoSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.networkInfoSpoofEnabled) return
        LogX.i("【实验性】网络信息伪造启动")

        hookWifiInfoIpAddress(lpparam)
        hookDhcpInfo(lpparam)
        hookNetworkInterface(lpparam)
    }

    /** WifiInfo.getIpAddress 返回伪造IP（int形式） */
    private fun hookWifiInfoIpAddress(lpparam: XC_LoadPackage.LoadPackageParam) {
        val wifi = XposedHelpers.findClassIfExists(
            "android.net.wifi.WifiInfo", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(wifi, "getIpAddress", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = ipToInt(FakeDeviceCache.fakeIpAddress)
                }
            })
            LogX.hookSuccess("WifiInfo", "getIpAddress")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getIpAddressAsString (部分厂商扩展)
        try {
            XposedHelpers.findAndHookMethod(wifi, "getIpAddressAsString", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = FakeDeviceCache.fakeIpAddress
                }
            })
            LogX.hookSuccess("WifiInfo", "getIpAddressAsString")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** DhcpInfo.ipAddress / dns1 / dns2 / gateway */
    private fun hookDhcpInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        val dhcp = XposedHelpers.findClassIfExists(
            "android.net.DhcpInfo", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(dhcp, "getIpAddress",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = ipToInt(FakeDeviceCache.fakeIpAddress)
                    }
                })
            LogX.hookSuccess("DhcpInfo", "getIpAddress")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // 直接覆盖字段
        try {
            XposedHelpers.findAndHookConstructor(dhcp, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val obj = p.thisObject
                    XposedHelpers.setIntField(obj, "ipAddress", ipToInt(FakeDeviceCache.fakeIpAddress))
                    XposedHelpers.setIntField(obj, "dns1", ipToInt(FakeDeviceCache.fakeDns))
                    XposedHelpers.setIntField(obj, "dns2", ipToInt("8.8.4.4"))
                }
            })
            LogX.hookSuccess("DhcpInfo", "<init>")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** NetworkInterface.getHardwareAddress 返回空（防MAC指纹） */
    private fun hookNetworkInterface(lpparam: XC_LoadPackage.LoadPackageParam) {
        val ni = XposedHelpers.findClassIfExists(
            "java.net.NetworkInterface", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(ni, "getHardwareAddress", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = ByteArray(0)
                }
            })
            LogX.hookSuccess("NetworkInterface", "getHardwareAddress")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        if (parts.size != 4) return 0
        return try {
            var r = 0
            for (p in parts) r = (r shl 8) or (p.toInt() and 0xFF)
            // NetworkInterface 期望 big-endian，DhcpInfo 使用 little-endian
            // 这里返回 big-endian，部分场景需反转
            Integer.reverseBytes(r)
        } catch (_: Throwable) { 0 }
    }
}
