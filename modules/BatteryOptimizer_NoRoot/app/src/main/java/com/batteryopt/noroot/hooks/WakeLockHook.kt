package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogStore
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

/**
 * WakeLock 持有优化 Hook（应用层，仅优化当前 APP 自身�?
 *
 * 功能�?
 *  1. Hook PowerManager.WakeLock.acquire(timeout)/acquire()，记录持有时�?
 *  2. 对超长持�?>配置阈�? 默认60s)�?wake lock 自动 release
 *  3. 对明显冗余的 wake lock（如 SDK 统计类）�?acquire 后立�?release
 *  4. 统计并日志输�?
 *
 * 策略说明�?
 *  - 采用"记录 + 超时自动 release"策略，避免直�?release 持有它的 SDK 导致崩溃
 *  - 冗余 wake lock 不通过 setResult 跳过原方法（void 方法不可靠）�?
 *    改为�?afterHookedMethod 中立即调�?release()，更安全
 *  - 冗余 wake lock 通过堆栈特征匹配（包�?类名包含 umeng/jpush/baidu 等统计关键字�?
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP 进程内的 WakeLock，无法跨进程释放系统�?wake lock
 *  - 不能修改系统 PowerManagerService，无法清理其他进程持有的孤儿 wake lock
 */
object WakeLockHook {

    private val releaseExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "WLockAutoRelease").apply { isDaemon = true }
    }

    /** 记录每个 WakeLock 实例�?acquire 时间 */
    private val holdRecords = ConcurrentHashMap<String, Long>()

    /** 标记本次 acquire 应在 after 阶段立即 release（线程局部，避免并发污染�?*/
    private val immediateReleaseFlags = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    /** 已识别为冗余�?wake lock tag 关键字（统计/推送类 SDK�?*/
    private val redundantKeywords = arrayOf(
        "umeng", "jpush", "baidu", "tencent_mta", "mta",
        "getui", "huawei_push", "xiaomi_push", "oppo_push",
        "stats", "analytics", "tracking", "log_report"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("WakeLock 优化启动（应用层）| 最大持�?${cfg.wakeLockMaxHoldMs}ms 拦截冗余=${cfg.wakeLockBlockRedundant}")

        hookAcquireWithTimeout(lpparam, cfg)
        hookAcquireNoTimeout(lpparam, cfg)
        hookRelease(lpparam)
    }

    /** Hook acquire(timeout) */
    private fun hookAcquireWithTimeout(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: BatteryConfig
    ) {
        try {
            val wlCls = XposedHelpers.findClassIfExists(
                "android.os.PowerManager.WakeLock", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wlCls, "acquire",
                java.lang.Long.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val tag = readTag(p.thisObject)
                        if (cfg.wakeLockBlockRedundant && isRedundant(tag)) {
                            immediateReleaseFlags.add(identity(p.thisObject))
                            LogX.d("标记冗余 wake lock(acquire_timeout) 立即释放: $tag")
                            return
                        }
                        val inputTimeout = p.args[0] as Long
                        if (inputTimeout > cfg.wakeLockMaxHoldMs) {
                            p.args[0] = cfg.wakeLockMaxHoldMs
                            LogX.w("缩短 wake lock 超时: $tag $inputTimeout -> ${cfg.wakeLockMaxHoldMs}")
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = identity(p.thisObject)
                        val tag = readTag(p.thisObject)
                        if (immediateReleaseFlags.remove(id)) {
                            try {
                                val held = XposedHelpers.callMethod(p.thisObject, "isHeld") as? Boolean ?: false
                                if (held) {
                                    XposedHelpers.callMethod(p.thisObject, "release")
                                    LogX.w("已立即释放冗�?wake lock: $tag")
                                    try { LogStore.add("optimized", "释放 wake lock: $tag") } catch (_: Exception) { }
                                    try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                                }
                            } catch (e: Exception) {
                                LogX.e("立即释放冗余 wake lock 异常: $tag", e)
                            }
                            return
                        }
                        holdRecords[id] = System.currentTimeMillis()
                        scheduleAutoRelease(p.thisObject, id, tag, cfg.wakeLockMaxHoldMs)
                        LogX.d("WakeLock acquire(timeout): $tag")
                    }
                })
            LogX.hookSuccess("PowerManager.WakeLock", "acquire(timeout)")
        } catch (e: Exception) {
            LogX.e("Hook acquire(timeout) 异常", e)
        }
    }

    /** Hook acquire()（无超时�?*/
    private fun hookAcquireNoTimeout(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: BatteryConfig
    ) {
        try {
            val wlCls = XposedHelpers.findClassIfExists(
                "android.os.PowerManager.WakeLock", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wlCls, "acquire",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val tag = readTag(p.thisObject)
                        if (cfg.wakeLockBlockRedundant && isRedundant(tag)) {
                            immediateReleaseFlags.add(identity(p.thisObject))
                            LogX.d("标记冗余 wake lock(acquire) 立即释放: $tag")
                            return
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = identity(p.thisObject)
                        val tag = readTag(p.thisObject)
                        if (immediateReleaseFlags.remove(id)) {
                            try {
                                val held = XposedHelpers.callMethod(p.thisObject, "isHeld") as? Boolean ?: false
                                if (held) {
                                    XposedHelpers.callMethod(p.thisObject, "release")
                                    LogX.w("已立即释放冗�?wake lock: $tag")
                                    try { LogStore.add("optimized", "释放 wake lock: $tag") } catch (_: Exception) { }
                                    try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                                }
                            } catch (e: Exception) {
                                LogX.e("立即释放冗余 wake lock 异常: $tag", e)
                            }
                            return
                        }
                        holdRecords[id] = System.currentTimeMillis()
                        scheduleAutoRelease(p.thisObject, id, tag, cfg.wakeLockMaxHoldMs)
                        LogX.w("WakeLock acquire(无超�?: $tag | 已安�?${cfg.wakeLockMaxHoldMs}ms 后自动释�?)
                    }
                })
            LogX.hookSuccess("PowerManager.WakeLock", "acquire()")
        } catch (e: Exception) {
            LogX.e("Hook acquire() 异常", e)
        }
    }

    /** Hook release()，清理记�?*/
    private fun hookRelease(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wlCls = XposedHelpers.findClassIfExists(
                "android.os.PowerManager.WakeLock", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wlCls, "release",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = identity(p.thisObject)
                        val startTs = holdRecords.remove(id) ?: return
                        val held = System.currentTimeMillis() - startTs
                        LogX.d("WakeLock release: ${readTag(p.thisObject)} 持有 ${held}ms")
                    }
                })

            try {
                XposedHelpers.findAndHookMethod(
                    wlCls, "release",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val id = identity(p.thisObject)
                            val startTs = holdRecords.remove(id) ?: return
                            val held = System.currentTimeMillis() - startTs
                            LogX.d("WakeLock release(): ${readTag(p.thisObject)} 持有 ${held}ms")
                        }
                    })
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("Hook release 异常", e)
        }
    }

    private fun readTag(wakeLock: Any?): String {
        return try {
            XposedHelpers.getObjectField(wakeLock, "mTag") as? String ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun identity(obj: Any?): String = System.identityHashCode(obj).toString()

    private fun isRedundant(tag: String): Boolean {
        val lower = tag.lowercase()
        return redundantKeywords.any { lower.contains(it) }
    }

    /** 安排超时自动 release（单线程池调度，避免每wakelock创建Thread�?*/
    private fun scheduleAutoRelease(
        wakeLock: Any?, id: String, tag: String, delayMs: Long
    ) {
        releaseExecutor.schedule({
            try {
                if (holdRecords.containsKey(id)) {
                    val held = try {
                        XposedHelpers.callMethod(wakeLock, "isHeld") as? Boolean ?: false
                    } catch (_: Exception) { false }
                    if (held) {
                        try {
                            XposedHelpers.callMethod(wakeLock, "release")
                            LogX.w("自动释放超时 wake lock: $tag | 已持�?$delayMs ms")
                        } catch (e: Exception) {
                            LogX.e("自动释放 wake lock 异常: $tag", e)
                        }
                    }
                    holdRecords.remove(id)
                }
            } catch (e: Exception) {
                LogX.e("自动释放异常", e)
            }
        }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}