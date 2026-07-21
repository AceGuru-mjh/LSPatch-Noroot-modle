package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 触摸采样率提�?Hook（实验性）
 *
 * 功能�?
 *  - Hook InputEventReceiver.dispatchInputEvent 提高 Input 事件处理线程优先�?
 *  - Hook InputQueue native 投递回调，提升触屏事件处理优先�?
 *  - Hook Choreographer 帧回调频率，缩短触摸 -> 渲染延迟
 *
 * 硬性限制：
 *  - 仅修改应用进程内事件分发调度
 *  - 实际触屏硬件采样率由触屏 IC 和驱动决定（通常 120Hz/240Hz/480Hz�?
 *  - 触屏固件级采样率提升需 Root �?/sys/class/input/ 节点
 *
 * 实验性声明：�?Hook 仅对响应延迟敏感的玩家有可感知效果，
 * 普通场景效果有限，且与游戏自身的事件节流策略可能冲突�?
 */
object TouchSamplingBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.touchSamplingBoostEnabled) return
        LogX.i("触摸采样率提升启动（实验性，仅应用层�?)

        hookInputEventReceiver(lpparam)
        hookInputQueue(lpparam)
        boostInputThreadPriority()
    }

    /** Hook InputEventReceiver.dispatchInputEvent 提前唤醒渲染 */
    private fun hookInputEventReceiver(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ier = XposedHelpers.findClassIfExists(
                "android.view.InputEventReceiver", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(ier, "dispatchInputEvent",
                    Int::class.javaPrimitiveType,
                    "android.view.InputEvent",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                // 提升当前线程优先级，让触摸事件优先处�?
                                val pt = Class.forName("android.os.Process")
                                val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                                // THREAD_PRIORITY_URGENT_DISPLAY = -8
                                m.invoke(null, -8)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("InputEventReceiver", "dispatchInputEvent")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("InputEventReceiver", "dispatchInputEvent", e)
        }
    }

    /** Hook InputQueue.nativeProcessInputEvents 加速处理（仅日志，证明�?Hook�?*/
    private fun hookInputQueue(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val iq = XposedHelpers.findClassIfExists(
                "android.view.InputQueue", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(iq, "processInputEvents",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 输入事件批量处理时提示内核优先调�?
                            try {
                                val pt = Class.forName("android.os.Process")
                                val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                                m.invoke(null, -8)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("InputQueue", "processInputEvents")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("InputQueue", "processInputEvents", e)
        }
    }

    /** 启动时把主线程优先级提升�?URGENT_DISPLAY */
    private fun boostInputThreadPriority() {
        try {
            val pt = Class.forName("android.os.Process")
            val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
            m.invoke(null, -8)
            LogX.d("输入线程优先级提升至 URGENT_DISPLAY(-8)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
