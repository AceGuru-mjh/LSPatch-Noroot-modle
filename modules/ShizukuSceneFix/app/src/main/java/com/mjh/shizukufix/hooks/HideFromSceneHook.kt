package com.mjh.shizukufix.hooks

import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】隐藏模块自身存�?
 *
 * 工作原理�?
 *  - �?Scene 进程�?Hook PackageManager.getInstalledApplications / getInstalledPackages
 *  - 从返回结果中过滤掉本模块（com.mjh.shizukufix）和相关 Xposed 框架�?
 *  - Hook PackageManager.getPackageInfo 对模块自身包名查询抛 NameNotFoundException
 *
 * 适用场景�?
 *  - Scene 检测已安装应用列表中是否含�?Xposed 模块时，�?Hook 隐藏模块存在
 *  - 防止 Scene 拒绝在检测到 Xposed 框架的环境下运行
 *
 * 注意�?
 *  - 实验性功能，可能无法绕过 Scene 的所有检测路�?
 *  - 仅修改传�?Scene 的查询结果，不影响系统真实安装状�?
 */
object HideFromSceneHook {

    /** 需要隐藏的包名清单（本模块 + LSPosed/LSPatch/Shizuku Manager/Magisk 等） */
    private val HIDE_PKGS = arrayOf(
        "com.mjh.shizukufix",                       // 本模�?
        "org.lsposed.manager", "org.lsposed.lspatch",
        "de.robv.android.xposed.installer",
        "moe.shizuku.privileged.api", "rikka.shizuku.manager",
        "com.topjohnwu.magisk", "io.github.huskydg.magisk"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.hideFromSceneEnabled) return
        LogX.i("【实验性】隐藏模块自身存在启�?)

        hookInstalledApplications(lpparam)
        hookInstalledPackages(lpparam)
        hookGetPackageInfo(lpparam)
    }

    /** Hook getInstalledApplications 过滤敏感包名 */
    private fun hookInstalledApplications(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pm = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(pm, "getInstalledApplications",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = filterList(p.result)
                    }
                })
            LogX.hookSuccess("ApplicationPackageManager", "getInstalledApplications")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(pm, "getInstalledApplicationsAsUser",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = filterList(p.result)
                    }
                })
            LogX.hookSuccess("ApplicationPackageManager", "getInstalledApplicationsAsUser")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook getInstalledPackages 过滤敏感包名 */
    private fun hookInstalledPackages(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pm = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(pm, "getInstalledPackages",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = filterList(p.result)
                    }
                })
            LogX.hookSuccess("ApplicationPackageManager", "getInstalledPackages")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook getPackageInfo 对敏感包名抛 NameNotFoundException */
    private fun hookGetPackageInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pm = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(pm, "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val name = p.args[0] as? String ?: return
                        if (HIDE_PKGS.any { name.equals(it, true) }) {
                            throw android.content.pm.PackageManager.NameNotFoundException(name)
                        }
                    }
                })
            LogX.hookSuccess("ApplicationPackageManager", "getPackageInfo")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** 从返回列表中过滤掉敏感包�?*/
    private fun filterList(result: Any?): Any? {
        val list = result as? MutableList<*> ?: return result
        val filtered = list.filter { item ->
            try {
                val f = item?.javaClass?.getDeclaredField("packageName")
                f?.isAccessible = true
                val name = f?.get(item) as? String ?: return@filter true
                !HIDE_PKGS.any { name.equals(it, true) }
            } catch (_: Throwable) { true }
        }
        return java.util.ArrayList(filtered)
    }
}
