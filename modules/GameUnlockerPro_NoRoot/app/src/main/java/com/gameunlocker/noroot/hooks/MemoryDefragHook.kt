package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内存整理 Hook（实验性）
 *
 * 功能：
 *  - Hook Debug.MemoryInfo 读取，让游戏看到更优的内存状态
 *  - Hook ActivityManager.getMemoryInfo 优化返回的内存压力指标
 *  - Hook低内存回调 onLowMemory / onTrimLevel，避免游戏主动降低画质
 *  - 启动时主动调用 System.gc 提示 JVM 整理堆
 *
 * 硬性限制：
 *  - 仅修改应用进程内的内存查询接口
 *  - 实际物理内存占用由 Linux 内核 OOM Killer 决定
 *  - 主动 System.gc 可能引发 Stop-The-World 抖动，频繁调用反而卡顿
 *
 * 实验性声明：默认关闭，仅在低内存设备 + 长时间游戏时考虑开启。
 */
object MemoryDefragHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.memoryDefragEnabled) return
        LogX.i("内存整理启动（实验性，仅应用层）")

        hookDebugMemoryInfo(lpparam)
        hookActivityManagerMemoryInfo(lpparam)
        hookTrimMemory(lpparam)
        hintGc()
    }

    /** Hook Debug.MemoryInfo 让可用内存显示更充足 */
    private fun hookDebugMemoryInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val dmi = XposedHelpers.findClassIfExists(
                "android.os.Debug.MemoryInfo", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(dmi, "getTotalPrivateDirty",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 让游戏看到更低的内存占用
                            p.result = 0
                        }
                    })
                LogX.hookSuccess("Debug.MemoryInfo", "getTotalPrivateDirty -> 0")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Debug.MemoryInfo", "getTotalPrivateDirty", e)
        }
    }

    /** Hook ActivityManager.getMemoryInfo 让系统看起来内存充足 */
    private fun hookActivityManagerMemoryInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val am = XposedHelpers.findClassIfExists(
                "android.app.ActivityManager", lpparam.classLoader) ?: return
            val miClass = XposedHelpers.findClassIfExists(
                "android.app.ActivityManager.MemoryInfo", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(am, "getMemoryInfo", miClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val mi = p.args[0] ?: return
                                // avalMem = 2GB, threshold = 512MB, lowMemory = false
                                mi.javaClass.getField("availMem").setLong(mi, 2L * 1024 * 1024 * 1024)
                                mi.javaClass.getField("threshold").setLong(mi, 512L * 1024 * 1024)
                                mi.javaClass.getField("lowMemory").setBoolean(mi, false)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("ActivityManager", "getMemoryInfo -> availMem=2GB")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("ActivityManager", "getMemoryInfo", e)
        }
    }

    /** Hook ComponentCallbacks2.onTrimMemory 阻止游戏降级 */
    private fun hookTrimMemory(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook android.app.Application.onTrimMemory（游戏 Application 多数继承自 Application）
            val app = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(app, "onTrimMemory",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 强制改为 TRIM_MEMORY_UI_HIDDEN(20)，避免游戏收到内存压力时降级
                            p.args[0] = 20
                        }
                    })
                LogX.hookSuccess("Application", "onTrimMemory -> UI_HIDDEN")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(app, "onLowMemory",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 拦截 onLowMemory 调用
                            p.result = null
                        }
                    })
                LogX.hookSuccess("Application", "onLowMemory -> skip")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onTrimMemory", e)
        }
    }

    /** 启动时主动调用 System.gc 提示 JVM 整理堆 */
    private fun hintGc() {
        try {
            System.gc()
            System.runFinalization()
            LogX.d("主动 System.gc 提示已发送")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
