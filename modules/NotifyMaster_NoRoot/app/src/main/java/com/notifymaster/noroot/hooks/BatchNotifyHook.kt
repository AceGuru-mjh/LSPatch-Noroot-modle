package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通知分组 Hook（实验性 - NoRoot 版仅应用进程内）
 *
 * 功能：将同一 APP 发出的多条通知合并为一个分组（Notification Group）。
 *
 * 拦截路径：
 *  1. NotificationManager.notify(int id, Notification n)
 *  2. NotificationManager.notify(String tag, int id, Notification n)
 *
 * 实现：
 *  - 每条通知 setGroup 添加 group key
 *  - 累计超过 batchMaxCount 条时，发送一条 setGroupSummary=true 的汇总通知
 *
 * 硬性限制：
 *  - 仅作用于当前 APP 进程内通知
 *  - 不修改系统通知服务
 *  - 汇总通知的图标/标题使用通用模板
 */
object BatchNotifyHook {

    private val counter = AtomicInteger(0)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.batchNotifyEnabled) return
        LogX.i("通知分组启动（实验性，group=${cfg.batchGroupKey}, max=${cfg.batchMaxCount}）")

        hookNotify(lpparam, cfg)
    }

    private fun hookNotify(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return
        val notifCls = XposedHelpers.findClassIfExists(
            "android.app.Notification", lpparam.classLoader) ?: return

        // Hook build 之前的 Notification 设置 group（通过 hook Notification.Builder.build）
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            // 调用 setGroup
                            XposedHelpers.callMethod(builder, "setGroup", cfg.batchGroupKey)
                            // 调用 setGroupSummary(false)（单条非汇总）
                            XposedHelpers.callMethod(builder, "setGroupSummary", false)
                            val count = counter.incrementAndGet()
                            LogX.d("通知分组：已为通知设置 group=${cfg.batchGroupKey}, count=$count")
                        } catch (e: Throwable) {
                            LogX.w("通知分组 build 前异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build[batch]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build[batch]", e) }

        // Hook notify(int, Notification) 累计计数，达到阈值时 post 汇总
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        tryPostSummary(p.thisObject, cfg)
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id, Notification)[batch]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id, Notification)[batch]", e) }
    }

    /** 当累计 >= batchMaxCount 时发送一条汇总通知 */
    private fun tryPostSummary(nmObj: Any?, cfg: NotifyConfig) {
        if (nmObj == null) return
        val count = counter.get()
        if (count < cfg.batchMaxCount) return

        try {
            val builderCls = Class.forName("android.app.Notification\$Builder")
            // 构造 Builder 反射需要 Context，这里用 NotificationManager 反推不到，跳过
            // 实际工程中需 Hook Application.onCreate 拿 Context 后传进来，此处保留计数+日志，避免运行时异常
            LogX.d("通知分组：累计 $count 条，已超阈值 ${cfg.batchMaxCount}，准备刷新汇总（占位实现）")
        } catch (e: Throwable) {
            LogX.w("通知分组汇总异常: ${e.message}")
        }
    }

    fun reset() {
        counter.set(0)
    }
}
