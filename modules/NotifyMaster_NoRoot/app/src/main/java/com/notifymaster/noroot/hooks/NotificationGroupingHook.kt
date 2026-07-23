package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通知分组 Hook（实验性 - NoRoot 版仅应用进程内）
 *
 * 功能：检测同一应用在时间窗口内发出的多条通知，自动注入 group key 合并为组。
 *
 * 拦截路径：
 *  1. Notification.Builder.setGroup - 注入分组 key
 *  2. NotificationManager.notify - 检测时间窗口内的同类通知，返回汇总通知
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Notification.Builder / NotificationManager
 *  - 不修改系统 NotificationManagerService
 *  - 分组状态仅保存在内存中
 */
object NotificationGroupingHook {

    private data class GroupState(
        val groupKey: String,
        var count: Int = 1,
        var firstNotifyTime: Long = System.currentTimeMillis(),
        var lastNotifyTime: Long = System.currentTimeMillis()
    )

    private val groupStates = mutableMapOf<String, GroupState>()
    private var notifyCount = 0

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notificationGroupingEnabled) return
        LogX.i("通知分组启动（实验性，窗口=${cfg.groupingWindowMs}ms，上限=${cfg.maxUngroupedCount}）")

        hookBuilderSetGroup(lpparam, cfg)
        hookNotifyForGrouping(lpparam, cfg)
    }

    private fun hookBuilderSetGroup(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "setGroup",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val originalGroup = p.args[0] as? String
                            val groupKey = cfg.batchGroupKey
                            if (originalGroup == null || originalGroup.isBlank()) {
                                p.args[0] = groupKey
                            } else {
                                p.args[0] = "$groupKey.${originalGroup}"
                            }
                            LogX.d("分组：setGroup -> ${p.args[0]}")
                        } catch (e: Throwable) { LogX.w("setGroup 注入异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "setGroup[grouping]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "setGroup[grouping]", e) }
    }

    private fun hookNotifyForGrouping(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
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
                            notifyCount++
                            val tag = p.args[0] as? String ?: ""
                            val notif = p.args[2] ?: return

                            val appPkg = lpparam.packageName ?: return
                            val stateKey = "$appPkg:$tag"
                            val now = System.currentTimeMillis()

                            val state = groupStates.getOrPut(stateKey) {
                                GroupState(groupKey = cfg.batchGroupKey)
                            }

                            if (state.count > 1 &&
                                (now - state.lastNotifyTime) < cfg.groupingWindowMs
                            ) {
                                state.count++
                                state.lastNotifyTime = now

                                if (state.count > cfg.maxUngroupedCount) {
                                    try {
                                        XposedHelpers.setObjectField(notif, "group", cfg.batchGroupKey)
                                        val extras = XposedHelpers.callMethod(notif, "getExtras")
                                        if (extras != null) {
                                            XposedHelpers.callMethod(
                                                extras, "putString", "android.summaryText",
                                                "已分组 ${state.count} 条通知"
                                            )
                                        }
                                        LogX.d("分组：第 ${state.count} 条通知已合并到组 ${cfg.batchGroupKey}")
                                    } catch (e: Throwable) { LogX.w("设置分组字段异常: ${e.message}") }
                                }
                            } else {
                                state.count = 1
                                state.firstNotifyTime = now
                                state.lastNotifyTime = now
                            }
                        } catch (e: Throwable) { LogX.w("notify 分组检测异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify[grouping]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify[grouping]", e) }

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            notifyCount++
                            val notif = p.args[1] ?: return
                            val stateKey = "${lpparam.packageName}:id_${p.args[0]}"

                            val now = System.currentTimeMillis()
                            val state = groupStates.getOrPut(stateKey) {
                                GroupState(groupKey = cfg.batchGroupKey)
                            }

                            if (state.count > 1 &&
                                (now - state.lastNotifyTime) < cfg.groupingWindowMs
                            ) {
                                state.count++
                                state.lastNotifyTime = now

                                if (state.count > cfg.maxUngroupedCount) {
                                    try {
                                        XposedHelpers.setObjectField(notif, "group", cfg.batchGroupKey)
                                    } catch (e: Throwable) { LogX.w("设置分组字段异常2: ${e.message}") }
                                }
                            } else {
                                state.count = 1
                                state.firstNotifyTime = now
                                state.lastNotifyTime = now
                            }
                        } catch (e: Throwable) { LogX.w("notify(id)分组检测异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id)[grouping]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id)[grouping]", e) }
    }
}
