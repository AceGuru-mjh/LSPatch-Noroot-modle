package com.mjh.shizukufix.hooks

import android.content.Context
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogStore
import com.mjh.shizukufix.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Shizuku 服务保活
 *
 * 工作原理�?
 *  - Hook Shizuku Application.onCreate / Service.onStartCommand
 *  - 周期检�?ShizukuService（rikka.shizuku.ShizukuService）存活状�?
 *  - 检测到服务死亡时，尝试通过 startService / bindService 重启 Shizuku 核心
 *
 * 注意�?
 *  - 实验性功能，可能�?Shizuku 版本变化失效
 *  - 仅在 Shizuku 自身进程内做保活，不修改系统 doze / 不调�?root
 *  - Shizuku �?adb 级服务，理论上不应被系统 kill；本 Hook 主要用于应对
 *    被第三方清理工具误杀的情�?
 *
 * 硬性限制：
 *  - 不修改系统进程优先级
 *  - 不调�?Shizuku.newProcess 执行 root 命令
 */
object ServiceWatchdogHook {

    /** Shizuku 服务候选类�?*/
    private val SHIZUKU_SERVICE_CLASSES = arrayOf(
        "rikka.shizuku.ShizukuService",
        "moe.shizuku.api.ShizukuService",
        "rikka.shizuku.service.ShizukuService"
    )

    /** Shizuku Application 候选类�?*/
    private val SHIZUKU_APP_CLASSES = arrayOf(
        "rikka.shizuku.ShizukuApplication",
        "moe.shizuku.api.ShizukuApplication",
        "moe.shizuku.manager.ShizukuApplication"
    )

    /** 进程级单次启动标�?*/
    private val watchdogStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.serviceWatchdogEnabled) return
        LogX.i("【实验性】Shizuku 服务保活启动（间�?${cfg.watchdogIntervalSec}s�?)
        try { LogStore.add("watchdog", "服务保活检�?) } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookShizukuServiceOnStart(lpparam, cfg)
        hookShizukuApplicationOnCreate(lpparam, cfg)
    }

    /** Hook ShizukuService.onStartCommand，确认服务存�?*/
    private fun hookShizukuServiceOnStart(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        for (clsName in SHIZUKU_SERVICE_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(cls, "onStartCommand",
                    android.content.Intent::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("  [Watchdog] ShizukuService.onStartCommand 被调用，服务存活")
                            startWatchdogIfNeeded(p.thisObject as? Context, cfg)
                        }
                    })
                LogX.hookSuccess(clsName, "onStartCommand")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook Shizuku Application.onCreate，启动保活线�?*/
    private fun hookShizukuApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        // 优先 Hook ShizukuApplication，失败回退通用 Application.onCreate
        for (clsName in SHIZUKU_APP_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(cls, "onCreate", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        LogX.i("  [Watchdog] Shizuku Application.onCreate 触发")
                        startWatchdogIfNeeded(p.thisObject as? Context, cfg)
                    }
                })
                LogX.hookSuccess(clsName, "onCreate")
                return
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }

        // 回退：通用 Application.onCreate
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        startWatchdogIfNeeded(p.thisObject as? Context, cfg)
                    }
                })
            LogX.hookSuccess("android.app.Application", "onCreate(Watchdog fallback)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** 启动保活线程（进程级单次�?*/
    private fun startWatchdogIfNeeded(context: Context?, cfg: ShizukuFixConfig) {
        if (watchdogStarted.get() || context == null) return
        watchdogStarted.set(true)
        Thread {
            val interval = (cfg.watchdogIntervalSec.coerceIn(10, 600)) * 1000L
            var attempts = 0
            val maxAttempts = cfg.watchdogRestartAttempts.coerceIn(0, 5)
            while (true) {
                try {
                    Thread.sleep(interval)
                    if (!isShizukuServiceAlive(context)) {
                        LogX.w("  [Watchdog] ShizukuService 不存活，尝试重启 #$attempts")
                        if (attempts < maxAttempts) {
                            restartShizukuService(context)
                            attempts++
                        } else {
                            LogX.w("  [Watchdog] 达到最大重启次�?$maxAttempts，停止重�?)
                            break
                        }
                    } else {
                        attempts = 0
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    LogX.e("  [Watchdog] 保活线程异常", t)
                }
            }
        }.start()
    }

    /** 检�?ShizukuService 是否在运行（通过 ActivityManager 进程列表�?*/
    private fun isShizukuServiceAlive(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = am.runningAppProcesses ?: return true
            processes.any {
                it.processName?.lowercase()?.contains("shizuku") == true
            }
        } catch (_: Throwable) { true }
    }

    /** 重启 Shizuku 服务（通过 startService 唤起，仅作用�?Shizuku 自身进程内） */
    private fun restartShizukuService(context: Context) {
        for (clsName in SHIZUKU_SERVICE_CLASSES) {
            try {
                val cls = Class.forName(clsName)
                val intent = android.content.Intent(context, cls)
                context.startService(intent)
                LogX.i("  [Watchdog] startService 重启 Shizuku 服务: $clsName")
                return
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
        LogX.w("  [Watchdog] 无法定位 Shizuku 服务类，重启失败")
    }
}
