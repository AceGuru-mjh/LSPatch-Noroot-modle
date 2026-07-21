package com.audioboost.noroot.utils

import android.util.Log

/**
 * 统一日志工具
 *  - i/d/w/e: 普通日志（同时输出 Logcat + Xposed 日志）
 *  - hookSuccess/hookFailed: Hook 调试日志
 */
object LogX {
    private const val TAG = "AudioBoost-NoRoot"

    fun i(msg: String) {
        Log.i(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG] $msg") } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:DEBUG] $msg") } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:WARN] $msg") } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:ERROR] $msg\n${t?.stackTraceToString()}") } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }

    fun hookSuccess(cls: String, method: String) {
        d("[Hook OK] $cls.$method")
    }

    fun hookFailed(cls: String, method: String, t: Throwable? = null) {
        w("[Hook FAIL] $cls.$method : ${t?.message ?: "unknown"}")
    }

    fun safeLog(msg: String) {
        val safe = msg.replace("Xposed", "X*****").replace("LSPosed", "L*****")
        d(safe)
    }
}
