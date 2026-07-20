package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 进程性能优化 Hook（NoRoot 版，替代 Root 版温控屏蔽）
 *
 * 可实现优化（应用层）：
 *  1. 提升游戏渲染线程优先级 -> 减少 CPU 调度延迟
 *  2. Hook PowerManager 热状态回调 -> 返回 STATUS_NONE
 *
 * 硬性限制（无法实现）：
 *  - 不能修改内核温控节点（/sys/class/thermal 等节点）
 *  - 不能禁用系统 thermal-engine 服务
 *  - 不能修改 CPU/GPU 调频策略
 *  - 高温下 SOC 硬件保护降频无法阻止
 *
 * 高温风险声明：本模块仅缓解轻度发热场景的降频，
 * 长时间高负载游戏仍会触发 SOC 硬件级保护（约 70-80℃），这是正常安全机制。
 */
object ProcessOptimizerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.processOptimizeEnabled) return
        LogX.i("进程性能优化启动（仅应用层）")

        boostRenderThread(lpparam)
        hookPowerThermalStatus(lpparam)
    }

    /**
     * 提升游戏渲染线程优先级
     * 线程优先级：THREAD_PRIORITY_URGENT_DISPLAY(-8) > THREAD_PRIORITY_DISPLAY(-4) > 默认(0)
     * 注意：这只是建议内核调度器优先调度，实际效果取决于内核
     */
    private fun boostRenderThread(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val glsv = XposedHelpers.findClassIfExists(
                "android.opengl.GLSurfaceView", lpparam.classLoader)
            if (glsv != null) {
                try {
                    XposedHelpers.findAndHookMethod(glsv, "setRenderMode",
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                // 设置为连续渲染模式(RENDERMODE_CONTINUOUSLY=1)避免帧率波动
                                if (p.args[0] as Int != 1) p.args[0] = 1
                            }
                        })
                    LogX.hookSuccess("GLSurfaceView", "setRenderMode -> CONTINUOUSLY")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }

            // 提升主线程优先级
            try {
                val pt = Class.forName("android.os.Process")
                val setThreadPriority = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                // THREAD_PRIORITY_URGENT_DISPLAY = -8
                setThreadPriority.invoke(null, -8)
                LogX.d("主线程优先级提升至 URGENT_DISPLAY(-8)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("渲染线程优先级提升异常", e)
        }
    }

    /**
     * Hook PowerManager 热状态
     * 返回 STATUS_NONE(0) 告诉游戏温度正常，避免游戏主动降画质
     * 注意：这只是欺骗游戏，不影响系统实际温控
     */
    private fun hookPowerThermalStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.os.PowerManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(pm, "getCurrentThermalStatus",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 0 // STATUS_NONE
                        }
                    })
                LogX.hookSuccess("PowerManager", "getCurrentThermalStatus -> STATUS_NONE")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("PowerManager", "getCurrentThermalStatus", e)
        }
    }
}
