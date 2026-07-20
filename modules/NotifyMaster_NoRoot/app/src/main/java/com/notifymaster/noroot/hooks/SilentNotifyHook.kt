package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 静默通知 Hook（实验性 - NoRoot 版仅应用进程内）
 *
 * 功能：让指定 APP 发出的通知静默（不响铃不震动）。
 *
 * 拦截路径：
 *  1. Notification.Builder.build() - 修改 defaults 字段（去掉 SOUND/VIBRATE）
 *  2. NotificationChannel 构造 - 强制 importance=LOW(2)
 *  3. NotificationChannel.setSound/setVibrationPattern - 设为 null/空
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Notification.Builder / NotificationChannel
 *  - 不修改系统 NotificationManagerService
 *  - 仅在 silentTargetApps 列表中的 APP 生效
 */
object SilentNotifyHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.silentNotifyEnabled) return
        // 仅当当前 APP 在静默列表中时生效
        if (lpparam.packageName !in cfg.silentTargetApps) {
            LogX.d("静默通知：当前 APP ${lpparam.packageName} 不在静默列表，跳过")
            return
        }
        LogX.i("静默通知启动（实验性，对 ${lpparam.packageName} 生效）")

        hookBuilderBuild(lpparam)
        hookNotificationChannel(lpparam)
    }

    private fun hookBuilderBuild(lpparam: XC_LoadPackage.LoadPackageParam) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            // setDefaults(0) 清除 SOUND/VIBRATE/LIGHTS
                            try {
                                XposedHelpers.callMethod(builder, "setDefaults", 0)
                            } catch (e: Throwable) { LogX.w("setDefaults 异常: ${e.message}") }
                            // setSound(null)
                            try {
                                XposedHelpers.callMethod(builder, "setSound", null as Any?)
                            } catch (e: Throwable) { LogX.w("setSound 异常: ${e.message}") }
                            // setVibrate(null)
                            try {
                                XposedHelpers.callMethod(builder, "setVibrate", null as Any?)
                            } catch (e: Throwable) { LogX.w("setVibrate 异常: ${e.message}") }
                            // setPriority(PRIORITY_LOW)
                            try {
                                XposedHelpers.callMethod(builder, "setPriority", 1) // PRIORITY_LOW
                            } catch (e: Throwable) { LogX.w("setPriority(LOW) 异常: ${e.message}") }
                        } catch (e: Throwable) {
                            LogX.w("静默 build 前异常: ${e.message}")
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val notif = p.result ?: return
                            // 兜底：直接清 defaults 字段
                            XposedHelpers.setIntField(notif, "defaults", 0)
                            XposedHelpers.setObjectField(notif, "sound", null)
                            XposedHelpers.setObjectField(notif, "vibrate", null)
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build[silent]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build[silent]", e) }
    }

    private fun hookNotificationChannel(lpparam: XC_LoadPackage.LoadPackageParam) {
        val channelCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationChannel", lpparam.classLoader) ?: return

        // Hook 构造方法：强制 importance=LOW(2)
        try {
            XposedHelpers.findAndHookConstructor(
                channelCls,
                String::class.java,
                CharSequence::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            p.args[2] = 2 // IMPORTANCE_LOW
                            LogX.d("静默：通知渠道 importance 强制为 LOW(2)")
                        } catch (e: Throwable) { LogX.w("渠道构造静默异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationChannel", "<init>[silent]")
        } catch (e: Exception) { LogX.w("NotificationChannel 构造 Hook 失败: ${e.message}") }

        // Hook setSound
        try {
            XposedHelpers.findAndHookMethod(
                channelCls, "setSound",
                "android.net.Uri",
                "android.media.AudioAttributes",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.args[0] = null
                    }
                })
            LogX.hookSuccess("NotificationChannel", "setSound")
        } catch (e: Exception) { LogX.w("setSound Hook 失败: ${e.message}") }

        // Hook setVibrationPattern
        try {
            XposedHelpers.findAndHookMethod(
                channelCls, "setVibrationPattern",
                LongArray::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.args[0] = null
                    }
                })
            LogX.hookSuccess("NotificationChannel", "setVibrationPattern")
        } catch (e: Exception) { LogX.w("setVibrationPattern Hook 失败: ${e.message}") }
    }
}
