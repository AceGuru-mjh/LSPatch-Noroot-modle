package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】已安装应用可见性伪装
 *
 * 拦截 PackageManager 查询，从返回列表中过滤掉敏感应用（Xposed/Shizuku/MT管理器等），
 * 防止目标APP通过遍历已安装应用进行环境检测或用户画像。
 *
 * 硬性限制：仅修改传给当前APP的查询结果，不影响系统真实安装状态。
 */
object PackageVisibilitySpoofHook {

    private val HIDE_PKGS = arrayOf(
        "org.lsposed.manager", "org.lsposed.lspatch",
        "moe.shizuku.privileged.api", "rikka.shizuku.manager",
        "de.robv.android.xposed.installer",
        "bin.mt.plus", "bin.mt.plus.canary",
        "me.piebridge.brevent",
        "com.topjohnwu.magisk", "io.github.huskydg.magisk",
        "com.koushikdutta.rommanager"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.packageVisibilitySpoofEnabled) return
        LogX.i("【实验性】已安装应用可见性伪装启动")

        hookInstalledApps(lpparam)
        hookGetPackageInfo(lpparam)
    }

    private fun hookInstalledApps(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pm = XposedHelpers.findClassIfExists(
            "android.content.pm.PackageManager", lpparam.classLoader) ?: return

        // getInstalledApplications(int)
        try {
            XposedHelpers.findAndHookMethod(pm, "getInstalledApplications",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = filterList(p.result)
                    }
                })
            LogX.hookSuccess("PackageManager", "getInstalledApplications")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getInstalledPackages(int)
        try {
            XposedHelpers.findAndHookMethod(pm, "getInstalledPackages",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = filterList(p.result)
                    }
                })
            LogX.hookSuccess("PackageManager", "getInstalledPackages")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getInstalledApplicationsAsUser
        try {
            XposedHelpers.findAndHookMethod(pm, "getInstalledApplicationsAsUser",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = filterList(p.result)
                    }
                })
            LogX.hookSuccess("PackageManager", "getInstalledApplicationsAsUser")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookGetPackageInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pm = XposedHelpers.findClassIfExists(
            "android.content.pm.PackageManager", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(pm, "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val name = p.args[0] as? String ?: return
                        if (HIDE_PKGS.any { name.contains(it, true) }) {
                            throw android.content.pm.PackageManager.NameNotFoundException(name)
                        }
                    }
                })
            LogX.hookSuccess("PackageManager", "getPackageInfo")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun filterList(result: Any?): Any? {
        val list = result as? MutableList<*> ?: return result
        val filtered = list.filter { item ->
            try {
                val f = item?.javaClass?.getDeclaredField("packageName")
                f?.isAccessible = true
                val name = f?.get(item) as? String ?: return@filter true
                !HIDE_PKGS.any { name.contains(it, true) }
            } catch (_: Throwable) { true }
        }
        return java.util.ArrayList(filtered)
    }
}
