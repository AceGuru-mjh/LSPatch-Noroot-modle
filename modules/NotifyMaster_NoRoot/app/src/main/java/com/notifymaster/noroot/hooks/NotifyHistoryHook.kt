package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知历史 Hook（NoRoot 版 - 仅应用进程内）
 *
 * 功能：Hook NotificationManager.notify，记录通知到内存历史列表（提供查询接口）。
 *
 * 拦截路径：
 *  1. NotificationManager.notify(int id, Notification n)
 *  2. NotificationManager.notify(String tag, int id, Notification n)
 *
 * 硬性限制：
 *  - 通知历史仅保存在内存中，进程重启后消失（NoRoot 不能跨进程共享）
 *  - 不写入文件（无系统级权限）
 *  - 仅记录当前 APP 自己发出的通知
 */
object NotifyHistoryHook {

    /** 单条历史记录 */
    data class HistoryEntry(
        val timestamp: Long,
        val timeStr: String,
        val packageName: String,
        val id: Int,
        val tag: String?,
        val title: String?,
        val text: String?,
        val ticker: String?
    )

    /** 历史记录列表（线程安全） */
    private val historyList = mutableListOf<HistoryEntry>()
    private const val MAX_HISTORY = 500
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notifyHistoryEnabled) return
        LogX.i("通知历史启动（内存记录，最多 $MAX_HISTORY 条）")

        hookNotify(lpparam)
    }

    private fun hookNotify(lpparam: XC_LoadPackage.LoadPackageParam) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        // notify(int, Notification)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = p.args[0] as Int
                        val notif = p.args[1] ?: return
                        recordEntry(lpparam.packageName, id, null, notif)
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id, Notification)[history]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id, Notification)[history]", e) }

        // notify(String tag, int id, Notification)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val tag = p.args[0] as? String
                        val id = p.args[1] as Int
                        val notif = p.args[2] ?: return
                        recordEntry(lpparam.packageName, id, tag, notif)
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(tag, id, Notification)[history]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(tag, id, Notification)[history]", e) }
    }

    /** 写入历史记录 */
    private fun recordEntry(pkg: String, id: Int, tag: String?, notif: Any) {
        try {
            val title = readExtrasCharSequence(notif, "android.title")
            val text = readExtrasCharSequence(notif, "android.text")
            val ticker = try {
                XposedHelpers.callMethod(notif, "getTickerText")?.toString()
            } catch (_: Throwable) { null }

            val entry = HistoryEntry(
                timestamp = System.currentTimeMillis(),
                timeStr = dateFormat.format(Date()),
                packageName = pkg,
                id = id,
                tag = tag,
                title = title,
                text = text,
                ticker = ticker
            )
            synchronized(historyList) {
                historyList.add(0, entry)
                if (historyList.size > MAX_HISTORY) {
                    historyList.subList(MAX_HISTORY, historyList.size).clear()
                }
            }
            LogX.d("通知历史已记录: pkg=$pkg id=$id title=$title")
        } catch (e: Throwable) {
            LogX.w("通知历史记录异常: ${e.message}")
        }
    }

    private fun readExtrasCharSequence(notif: Any, key: String): String? {
        return try {
            val extras = XposedHelpers.callMethod(notif, "getExtras") ?: return null
            (XposedHelpers.callMethod(extras, "getCharSequence", key) as? CharSequence)?.toString()
        } catch (_: Throwable) { null }
    }

    // ===== 查询接口 =====

    /** 获取所有历史记录（副本） */
    @Suppress("unused")
    fun getAllHistory(): List<HistoryEntry> = synchronized(historyList) { historyList.toList() }

    /** 按关键词搜索历史 */
    @Suppress("unused")
    fun searchHistory(keyword: String): List<HistoryEntry> {
        if (keyword.isBlank()) return getAllHistory()
        return synchronized(historyList) {
            historyList.filter {
                (it.title?.contains(keyword) == true) ||
                (it.text?.contains(keyword) == true) ||
                (it.ticker?.contains(keyword) == true)
            }.toList()
        }
    }

    /** 清空历史 */
    @Suppress("unused")
    fun clearHistory() {
        synchronized(historyList) { historyList.clear() }
    }

    /** 释放（重置 Hook） */
    fun release() {
        synchronized(historyList) { historyList.clear() }
    }
}
