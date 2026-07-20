package com.microx.enhancer.utils

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook辅助工具类（保留兼容旧 Hook 代码）
 *
 * 提供安全的类查找、方法 Hook、日志输出等通用功能。
 * 所有操作仅作用于当前被修补的应用进程。
 */
object HookHelper {

    const val TAG = "MicroXEnhancer"
    private var isDebug = false

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    fun logD(msg: String) {
        if (isDebug) {
            Log.d(TAG, msg)
            try { XposedBridge.log("[$TAG-D] $msg") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        try { XposedBridge.log("[$TAG] $msg") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    fun logE(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        try { XposedBridge.log("[$TAG-E] $msg: ${t?.message ?: ""}") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /**
     * 安全查找类：尝试多个可能的类名，返回第一个找到的
     */
    fun findClassSafe(
        lpparam: XC_LoadPackage.LoadPackageParam,
        vararg candidateNames: String
    ): Class<*>? {
        for (name in candidateNames) {
            try {
                return XposedHelpers.findClass(name, lpparam.classLoader)
            } catch (_: XposedHelpers.ClassNotFoundError) {
                logD("类未找到(尝试下一个): $name")
            } catch (_: Exception) {
                logD("查找类异常: $name")
            }
        }
        logE("所有候选类名均未找到: ${candidateNames.joinToString()}")
        return null
    }

    /**
     * 安全 Hook 方法：封装 try-catch，防止单个 Hook 失败影响其他功能
     */
    fun hookMethodSafe(
        clazz: Class<*>?,
        methodName: String,
        hookCallback: XC_MethodHook,
        vararg paramTypes: Any?
    ): Boolean {
        if (clazz == null) {
            logE("Hook失败: 目标类为空 ($methodName)")
            return false
        }
        return try {
            if (paramTypes.isNotEmpty()) {
                XposedHelpers.findAndHookMethod(clazz, methodName, *paramTypes, hookCallback)
            } else {
                XposedHelpers.findAndHookMethod(clazz, methodName, hookCallback)
            }
            logD("Hook成功: ${clazz.name}.$methodName")
            true
        } catch (_: NoSuchMethodError) {
            logE("Hook失败(方法不存在): ${clazz.name}.$methodName")
            false
        } catch (e: Exception) {
            logE("Hook失败(异常): ${clazz.name}.$methodName — ${e.message}", e)
            false
        }
    }

    /**
     * Hook 所有匹配名称的方法（不限参数类型）
     */
    fun hookAllMethodsSafe(
        clazz: Class<*>?,
        methodName: String,
        hookCallback: XC_MethodHook
    ): Boolean {
        if (clazz == null) {
            logE("HookAll失败: 目标类为空 ($methodName)")
            return false
        }
        return try {
            XposedBridge.hookAllMethods(clazz, methodName, hookCallback)
            logD("HookAll成功: ${clazz.name}.$methodName(*)")
            true
        } catch (e: Exception) {
            logE("HookAll失败: ${clazz.name}.$methodName — ${e.message}", e)
            false
        }
    }

    /** 判断当前进程是否为微信主进程 */
    fun isWeChatMainProcess(processName: String): Boolean {
        return processName == "com.tencent.mm" ||
                processName == "com.tencent.mm:tools"
    }

    /** 判断当前进程是否为QQ主进程 */
    fun isQQMainProcess(processName: String): Boolean {
        return processName == "com.tencent.mobileqq"
    }

    /** 获取布尔参数，带默认值处理 */
    fun getBooleanArg(args: XC_MethodHook.MethodHookParam, index: Int, default: Boolean): Boolean {
        return try {
            args.args[index] as? Boolean ?: default
        } catch (_: Exception) {
            default
        }
    }

    /** 获取字符串参数 */
    fun getStringArg(args: XC_MethodHook.MethodHookParam, index: Int): String? {
        return try {
            args.args[index] as? String
        } catch (_: Exception) {
            null
        }
    }
}
