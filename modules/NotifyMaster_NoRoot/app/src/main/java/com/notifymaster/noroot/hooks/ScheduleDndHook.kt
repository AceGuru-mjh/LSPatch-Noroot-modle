package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Calendar

/**
 * 定时免打扰 Hook（实验性 - NoRoot 版仅应用进程内）
 *
 * 功能：按时间表控制通知静默/免打扰。
 *  - 周一至周五：默认 22:00 - 07:00 免打扰
 *  - 周六/周日：默认 23:00 - 09:00 免打扰
 *
 * 拦截路径：
 *  1. Notification.Builder.build() - 在免打扰时段强制静默通知
 *  2. Notification.Builder.setDefaults - 拦截铃声/震动设置
 *  3. NotificationManager.notify - 免打扰时段可选拦截不显示
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Notification.Builder / NotificationManager
 *  - 不修改系统 NotificationManagerService / AudioManager
 *  - 不调用系统级 DND API
 */
object ScheduleDndHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.scheduleDndEnabled) return

        if (!isInDndPeriod(cfg)) {
            LogX.d("定时免打扰：当前不在免打扰时段，跳过")
            return
        }

        LogX.i("定时免打扰启动（实验性，当前在免打扰时段内）")

        hookBuilderDnd(lpparam, cfg)
        hookNotifyDnd(lpparam, cfg)
    }

    private fun isInDndPeriod(cfg: NotifyConfig): Boolean {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 ... 7=周六
        val hour = cal.get(Calendar.HOUR_OF_DAY) // 0-23

        val isWeekend = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY

        return if (isWeekend) {
            val start = cfg.weekendStartHour
            val end = cfg.weekendEndHour
            if (start > end) {
                hour >= start || hour < end
            } else {
                hour in start until end
            }
        } else {
            val start = cfg.weekdayStartHour
            val end = cfg.weekdayEndHour
            if (start > end) {
                hour >= start || hour < end
            } else {
                hour in start until end
            }
        }
    }

    private fun hookBuilderDnd(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            if (!isInDndPeriod(cfg)) return
                            val builder = p.thisObject ?: return
                            try {
                                XposedHelpers.callMethod(builder, "setDefaults", 0)
                            } catch (e: Throwable) { LogX.w("DND setDefaults 异常: ${e.message}") }
                            try {
                                XposedHelpers.callMethod(builder, "setSound", null as Any?)
                            } catch (e: Throwable) { LogX.w("DND setSound 异常: ${e.message}") }
                            try {
                                XposedHelpers.callMethod(builder, "setVibrate", null as Any?)
                            } catch (e: Throwable) { LogX.w("DND setVibrate 异常: ${e.message}") }
                            try {
                                XposedHelpers.callMethod(builder, "setPriority", 1) // PRIORITY_LOW
                            } catch (e: Throwable) { LogX.w("DND setPriority 异常: ${e.message}") }
                            LogX.d("定时免打扰：Builder.build 强制静默")
                        } catch (e: Throwable) { LogX.w("DND build 异常: ${e.message}") }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            if (!isInDndPeriod(cfg)) return
                            val notif = p.result ?: return
                            XposedHelpers.setIntField(notif, "defaults", 0)
                            XposedHelpers.setObjectField(notif, "sound", null)
                            XposedHelpers.setObjectField(notif, "vibrate", null)
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build[dnd]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build[dnd]", e) }

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "setDefaults",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isInDndPeriod(cfg)) {
                            p.args[0] = 0
                            LogX.d("定时免打扰：setDefaults 已清零")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "setDefaults[dnd]")
        } catch (e: Exception) { LogX.w("DND setDefaults Hook 失败: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "setSound",
                "android.net.Uri",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isInDndPeriod(cfg)) {
                            p.args[0] = null
                            LogX.d("定时免打扰：setSound 已置空")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "setSound[dnd]")
        } catch (e: Exception) { LogX.w("DND setSound Hook 失败: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "setVibrate",
                LongArray::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isInDndPeriod(cfg)) {
                            p.args[0] = null
                            LogX.d("定时免打扰：setVibrate 已置空")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "setVibrate[dnd]")
        } catch (e: Exception) { LogX.w("DND setVibrate Hook 失败: ${e.message}") }
    }

    private fun hookNotifyDnd(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
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
                        if (isInDndPeriod(cfg)) {
                            val notif = p.args[2] ?: return
                            try {
                                XposedHelpers.setIntField(notif, "defaults", 0)
                                XposedHelpers.setObjectField(notif, "sound", null)
                                XposedHelpers.setObjectField(notif, "vibrate", null)
                            } catch (_: Throwable) { }
                            LogX.d("定时免打扰：notify 静默处理")
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify[dnd]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify[dnd]", e) }

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isInDndPeriod(cfg)) {
                            val notif = p.args[1] ?: return
                            try {
                                XposedHelpers.setIntField(notif, "defaults", 0)
                                XposedHelpers.setObjectField(notif, "sound", null)
                                XposedHelpers.setObjectField(notif, "vibrate", null)
                            } catch (_: Throwable) { }
                            LogX.d("定时免打扰：notify(id) 静默处理")
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id)[dnd]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id)[dnd]", e) }
    }
}
