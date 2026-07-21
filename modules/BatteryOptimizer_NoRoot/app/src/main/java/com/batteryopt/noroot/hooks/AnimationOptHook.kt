package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 动画关闭优化 Hook（应用层�?
 *
 * 功能�?
 *  - 关闭 APP 内不必要的动画（scale 设为 0），降低 GPU 渲染负载省电
 *  - 通过 Hook ValueAnimator/ObjectAnimator �?start，按配置 scale 调整时长
 *
 * 硬性限制（NoRoot 版）�?
 *  - 仅作用于当前 APP 内部动画，不能修改系统全局动画 scale
 *  - 默认关闭，用户开启后可能影响部分交互动画体验
 */
object AnimationOptHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Animation 动画优化启动 | scale=${cfg.animationScale}")

        hookValueAnimator(lpparam, cfg)
        hookObjectAnimator(lpparam)
        hookViewAnimation(lpparam, cfg)
    }

    private fun hookValueAnimator(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        try {
            val vaCls = XposedHelpers.findClassIfExists(
                "android.animation.ValueAnimator", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                vaCls, "setDuration",
                java.lang.Long.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val duration = p.args[0] as Long
                        if (cfg.animationScale <= 0f) {
                            p.args[0] = 0L
                        } else if (cfg.animationScale < 1f) {
                            p.args[0] = (duration * cfg.animationScale).toLong()
                        }
                    }
                })
            LogX.hookSuccess("ValueAnimator", "setDuration")
        } catch (e: Exception) {
            LogX.e("Hook ValueAnimator 异常", e)
        }
    }

    private fun hookObjectAnimator(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val oaCls = XposedHelpers.findClassIfExists(
                "android.animation.ObjectAnimator", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                oaCls, "start",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("ObjectAnimator start")
                    }
                })
            LogX.hookSuccess("ObjectAnimator", "start")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun hookViewAnimation(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        try {
            val viewCls = XposedHelpers.findClassIfExists(
                "android.view.View", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                viewCls, "startAnimation",
                "android.view.animation.Animation",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val anim = p.args[0] ?: return
                        try {
                            val animCls = anim.javaClass
                            val durMethod = animCls.getMethod("setDuration", java.lang.Long.TYPE)
                            val getDurMethod = animCls.getMethod("getDuration")
                            val cur = getDurMethod.invoke(anim) as Long
                            if (cfg.animationScale <= 0f) {
                                durMethod.invoke(anim, 0L)
                            } else if (cfg.animationScale < 1f) {
                                durMethod.invoke(anim, (cur * cfg.animationScale).toLong())
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("View", "startAnimation")
        } catch (e: Exception) {
            LogX.e("Hook View.startAnimation 异常", e)
        }
    }
}
