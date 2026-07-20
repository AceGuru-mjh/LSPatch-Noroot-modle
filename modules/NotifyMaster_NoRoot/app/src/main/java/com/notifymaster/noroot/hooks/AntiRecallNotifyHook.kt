package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 防通知撤回 Hook（NoRoot 版 - 仅应用进程内）
 *
 * 功能：阻止应用主动 cancel 自己发出的通知（防撤回提示被清掉）。
 *
 * 拦截路径：
 *  1. NotificationManager.cancel(int id)
 *  2. NotificationManager.cancel(String tag, int id)
 *  3. NotificationManager.cancelAll()
 *  4. NotificationManager.cancelAsUser(...)
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内的 cancel 调用（应用想撤回自己的通知时拦截）
 *  - 不 Hook 系统 NotificationListenerService（NoRoot 版无系统权限）
 *  - 用户手动划掉通知不在此 Hook 范围（那是系统侧调用）
 */
object AntiRecallNotifyHook {

    /** 标记：Hook 安装后，应用自身 cancel 一律被拦截 */
    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.antiRecallNotifyEnabled) return
        if (isApplied) return
        isApplied = true

        LogX.i("防通知撤回启动（应用自身 cancel 全部拦截）")

        hookCancel(lpparam)
    }

    private fun hookCancel(lpparam: XC_LoadPackage.LoadPackageParam) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        // cancel(int id)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancel",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancel(id=${p.args[0]})")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancel(id)")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "cancel(id)", e) }

        // cancel(String tag, int id)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancel",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancel(tag=${p.args[0]}, id=${p.args[1]})")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancel(tag, id)")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "cancel(tag, id)", e) }

        // cancelAll()
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancelAll",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancelAll()")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancelAll")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "cancelAll", e) }

        // cancelAsUser(String tag, int id, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancelAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.os.UserHandle",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancelAsUser")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancelAsUser")
        } catch (e: Exception) { LogX.w("cancelAsUser 不存在或 Hook 失败: ${e.message}") }
    }

    /** 释放（重启 Hook 链路时调用） */
    fun release() {
        isApplied = false
    }
}
