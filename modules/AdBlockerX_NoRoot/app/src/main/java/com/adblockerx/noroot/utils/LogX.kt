package com.adblockerx.noroot.utils

import android.util.Log

/**
 * 统一日志工具
 * TAG: AdBlockerX-NoRoot，可�?adb logcat -s AdBlockerX:V 查看
 */
object LogX {
    private const val TAG = "AdBlockerX-NoRoot"
    var debugEnabled = true

    fun d(msg: String) {
        if (debugEnabled) {
            Log.d(TAG, msg)
            try { de.robv.android.xposed.XposedBridge.log("[$TAG:DEBUG] $msg") } catch (_: Throwable) {}
        }
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG] $msg") } catch (_: Throwable) {}
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:WARN] $msg") } catch (_: Throwable) {}
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:ERROR] $msg") } catch (_: Throwable) {}
    }

    fun e(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:ERROR] $msg\n${t.stackTraceToString()}") } catch (_: Throwable) {}
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
