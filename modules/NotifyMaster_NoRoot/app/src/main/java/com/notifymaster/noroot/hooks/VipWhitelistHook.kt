package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogStore
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * VIP 白名单 Hook（实验性 - NoRoot 版仅应用进程内）
 *
 * 功能：白名单中的联系人或 APP 发出的通知始终显示，绕过后续所有过滤。
 *
 * 拦截路径：
 *  1. NotificationManager.notify - 检测通知标题/内容是否命中白名单联系人
 *  2. 命中白名单的通知直接放行，不做任何过滤/修改
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 NotificationManager.notify
 *  - 不修改系统 NotificationManagerService
 *  - 仅在 vipContactApps 列表中的 APP 生效白名单检测
 */
object VipWhitelistHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.vipWhitelistEnabled) return

        val currentPkg = lpparam.packageName ?: return
        if (cfg.vipContactApps.isNotEmpty() && currentPkg !in cfg.vipContactApps) {
            LogX.d("VIP白名单：当前 APP $currentPkg 不在 vipContactApps 列表，跳过")
            return
        }

        LogX.i("VIP白名单启动（实验性，联系人=${cfg.vipContactNames.size}，APP=${cfg.vipContactApps.size}）")
        try { LogStore.add("whitelist", "VIP白名单已启用") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookNotifyWhitelist(lpparam, cfg)
    }

    private fun hookNotifyWhitelist(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val notif = p.args[2] ?: return
                            if (isVipNotification(notif, cfg)) {
                                LogX.i("VIP白名单命中：放行通知")
                                logVipNotification(notif, lpparam.packageName)
                            }
                        } catch (e: Throwable) { LogX.w("VIP白名单检测异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify[vip-whitelist]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify[vip-whitelist]", e) }

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val notif = p.args[1] ?: return
                            if (isVipNotification(notif, cfg)) {
                                LogX.i("VIP白名单命中(by id)：放行通知")
                                logVipNotification(notif, lpparam.packageName)
                            }
                        } catch (e: Throwable) { LogX.w("VIP白名单检测异常2: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id)[vip-whitelist]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id)[vip-whitelist]", e) }
    }

    private fun isVipNotification(notif: Any, cfg: NotifyConfig): Boolean {
        if (cfg.vipContactNames.isEmpty()) return false
        val text = extractNotificationText(notif) ?: return false
        return cfg.vipContactNames.any { name ->
            name.isNotBlank() && text.contains(name, ignoreCase = true)
        }
    }

    private fun extractNotificationText(notif: Any): String? {
        return try {
            val sb = StringBuilder()
            try {
                val ticker = XposedHelpers.callMethod(notif, "getTickerText")
                if (ticker != null) sb.append(ticker.toString())
            } catch (_: Throwable) { }

            try {
                val extras = XposedHelpers.callMethod(notif, "getExtras")
                if (extras != null) {
                    val title = XposedHelpers.callMethod(extras, "getCharSequence", "android.title")
                    val text = XposedHelpers.callMethod(extras, "getCharSequence", "android.text")
                    val bigText = XposedHelpers.callMethod(extras, "getCharSequence", "android.bigText")
                    if (title != null) sb.append(title.toString())
                    if (text != null) sb.append(text.toString())
                    if (bigText != null) sb.append(bigText.toString())
                }
            } catch (_: Throwable) { }

            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Throwable) { null }
    }

    private fun logVipNotification(notif: Any, pkg: String?) {
        try {
            val extras = XposedHelpers.callMethod(notif, "getExtras")
            val title = if (extras != null) {
                XposedHelpers.callMethod(extras, "getCharSequence", "android.title")?.toString() ?: ""
            } else ""
            val text = if (extras != null) {
                XposedHelpers.callMethod(extras, "getCharSequence", "android.text")?.toString() ?: ""
            } else ""
            LogX.i("VIP通知 [${pkg ?: "未知"}] 标题=$title 内容=$text")
            try { LogStore.add("whitelist", "VIP放行: $title - $text") } catch (_: Exception) { }
        } catch (_: Throwable) { }
    }
}
