package com.privacyguard.noroot.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 跨进程日志存储（UI 进程 �?Hook 进程共享�?
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "info",
    val message: String = ""
)

object LogStore {

    private const val PREFS_NAME = "privacy_guard_log_store"
    private const val KEY_LOGS = "logs"
    private const val KEY_COUNTER = "counter"
    private const val MAX_LOGS = 200

    private val gson = Gson()
    private val logListType = object : TypeToken<MutableList<LogEntry>>() {}.type
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            try { ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } catch (_: Throwable) { null }
        }
    }

    private fun p(): SharedPreferences? = prefs

    fun add(type: String, message: String) {
        try {
            val sp = p() ?: return
            val raw = sp.getString(KEY_LOGS, null)
            val list: MutableList<LogEntry> = if (raw.isNullOrEmpty()) {
                mutableListOf()
            } else {
                try {
                    gson.fromJson<MutableList<LogEntry>>(raw, logListType) ?: mutableListOf()
                } catch (_: Throwable) { mutableListOf() }
            }
            list.add(LogEntry(timestamp = System.currentTimeMillis(), type = type, message = message))
            if (list.size > MAX_LOGS) {
                val drop = list.size - MAX_LOGS
                repeat(drop) { if (list.isNotEmpty()) list.removeAt(0) }
            }
            sp.edit().putString(KEY_LOGS, gson.toJson(list)).apply()
        } catch (_: Throwable) {}
    }

    fun incrementCounter(amount: Long = 1L) {
        try {
            val sp = p() ?: return
            val cur = sp.getLong(KEY_COUNTER, 0L)
            sp.edit().putLong(KEY_COUNTER, cur + amount).apply()
        } catch (_: Throwable) {}
    }

    fun getCounter(): Long {
        return try { p()?.getLong(KEY_COUNTER, 0L) ?: 0L } catch (_: Throwable) { 0L }
    }

    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        return try {
            val sp = p() ?: return emptyList()
            val raw = sp.getString(KEY_LOGS, null) ?: return emptyList()
            val list: List<LogEntry> = gson.fromJson(raw, logListType) ?: return emptyList()
            if (count >= list.size) list else list.subList(list.size - count, list.size)
        } catch (_: Throwable) { emptyList() }
    }

    fun clear() {
        try {
            val sp = p() ?: return
            sp.edit().remove(KEY_LOGS).remove(KEY_COUNTER).apply()
        } catch (_: Throwable) {}
    }
}
