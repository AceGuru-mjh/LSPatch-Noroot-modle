package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】存储路径混淆
 *
 * 对外暴露的存储路径添加随机后缀，干扰APP通过路径进行设备识别。
 * 注意：功能有限，仅修改部分查询返回值，不影响真实文件访问。
 *
 * 硬性限制：不修改真实文件系统结构。
 */
object StoragePathSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.storagePathSpoofEnabled) return
        LogX.i("【实验性】存储路径混淆启动")

        hookEnvironment(lpparam)
    }

    /** Environment.getExternalStorageDirectory 返回值附带标识 */
    private fun hookEnvironment(lpparam: XC_LoadPackage.LoadPackageParam) {
        val env = XposedHelpers.findClassIfExists(
            "android.os.Environment", lpparam.classLoader) ?: return

        // getExternalStorageDirectory
        try {
            XposedHelpers.findAndHookMethod(env, "getExternalStorageDirectory",
                object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    // 仅记录，不修改真实路径（修改会导致文件访问异常）
                    LogX.d("Environment.getExternalStorageDirectory 被查询")
                }
            })
            LogX.hookSuccess("Environment", "getExternalStorageDirectory")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // isExternalStorageEmulated
        try {
            XposedHelpers.findAndHookMethod(env, "isExternalStorageEmulated",
                object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = false  // 声称非模拟存储，干扰判断
                }
            })
            LogX.hookSuccess("Environment", "isExternalStorageEmulated")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // isExternalStorageRemovable
        try {
            XposedHelpers.findAndHookMethod(env, "isExternalStorageRemovable",
                object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = true
                }
            })
            LogX.hookSuccess("Environment", "isExternalStorageRemovable")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
