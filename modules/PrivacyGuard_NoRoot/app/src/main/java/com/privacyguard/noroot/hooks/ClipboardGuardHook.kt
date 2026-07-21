package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogStore
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 剪贴板保护Hook（仅应用层，无法影响系统全局）
 *
 * 硬性限制：
 *  - 仅 Hook Java 层 ClipboardManager API，无法拦截 Native 层直接读取
 *  - 阻断模式会让 APP 读不到剪贴板内容（返回 null 或空 ClipData）
 *  - 不修改系统剪贴板，仅在当前进程内拦截
 *
 * 功能：
 *  1. 记录 APP 读取剪贴板行为（防偷读）
 *  2. 可选阻断 getPrimaryClip 返回（防 APP 偷读剪贴板内容）
 */
object ClipboardGuardHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.clipboardGuardEnabled) return
        LogX.i("剪贴板保护启动（仅应用层）：阻断=${cfg.clipboardBlockRead}")

        hookClipboardManager(lpparam, cfg.clipboardBlockRead)
    }

    private fun hookClipboardManager(lpparam: XC_LoadPackage.LoadPackageParam, blockRead: Boolean) {
        try {
            val cm = XposedHelpers.findClassIfExists(
                "android.content.ClipboardManager", lpparam.classLoader) ?: return

            // getPrimaryClip()
            try {
                XposedHelpers.findAndHookMethod(cm, "getPrimaryClip", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.w("检测到APP读取剪贴板: ${p.thisObject?.javaClass?.name}")
                        try { LogStore.add("blocked", "阻止剪贴板读取") } catch (_: Exception) { }
                        try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                        if (blockRead) {
                            // 返回 null 让 APP 以为剪贴板为空
                            p.result = null
                        }
                    }
                })
                LogX.hookSuccess("ClipboardManager", "getPrimaryClip")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getPrimaryClipDescription()
            try {
                XposedHelpers.findAndHookMethod(cm, "getPrimaryClipDescription", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("APP读取剪贴板描述")
                        if (blockRead) p.result = null
                    }
                })
                LogX.hookSuccess("ClipboardManager", "getPrimaryClipDescription")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // hasPrimaryClip()
            try {
                XposedHelpers.findAndHookMethod(cm, "hasPrimaryClip", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (blockRead) p.result = false
                    }
                })
                LogX.hookSuccess("ClipboardManager", "hasPrimaryClip")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // setPrimaryClip() 记录写入行为（不阻断，因为合法写入需要保留）
            try {
                XposedHelpers.findAndHookMethod(cm, "setPrimaryClip",
                    "android.content.ClipData", object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("APP写入剪贴板（已记录，未阻断）")
                        }
                    })
                LogX.hookSuccess("ClipboardManager", "setPrimaryClip")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("ClipboardManager", "primary-clip", e)
        }
    }
}
