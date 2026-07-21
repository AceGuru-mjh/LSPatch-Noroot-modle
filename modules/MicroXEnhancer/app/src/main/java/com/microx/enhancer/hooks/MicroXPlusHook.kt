package com.microx.enhancer.hooks

import com.microx.enhancer.models.MicroXConfig
import com.microx.enhancer.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * v1.0.6 新增功能集合（对�?NewMiko�?
 *
 * 包含�?
 *  - 步数修改（Hook 微信运动步数上报�?
 *  - 禁用微信热更新（阻止 dex 加载热更新包�?
 *  - 朋友圈伪集赞（Hook 朋友圈点赞数显示�?
 *  - 去除9人转发限制（Hook 转发数量检查）
 *  - 自动发送原图（Hook 图片发送强制原图）
 */
object MicroXPlusHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: MicroXConfig) {
        if (cfg.stepModifierEnabled) hookStepCounter(lpparam, cfg)
        if (cfg.disableHotUpdateEnabled) hookDisableHotUpdate(lpparam)
        if (cfg.momentFakeLikeEnabled) hookMomentFakeLike(lpparam, cfg)
        if (cfg.unlimitedForwardEnabled) hookUnlimitedForward(lpparam)
        if (cfg.autoOriginalImageEnabled) hookAutoOriginalImage(lpparam)
    }

    /** 步数修改：Hook 微信运动步数上报，返回固定大�?*/
    private fun hookStepCounter(lpparam: XC_LoadPackage.LoadPackageParam, cfg: MicroXConfig) {
        val targetSteps = 10000L  // 固定1万步
        val candidates = listOf(
            "com.tencent.mm.plugin.sport.a",
            "com.tencent.mm.plugin.sport.model.SportService",
            "com.tencent.mm.plugin.sport.ui.SportMainUI"
        )
        for (cls in candidates) {
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("getStep", true) ||
                        method.name.contains("getCurrentStep", true) ||
                        method.name.contains("reportStep", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, method.name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(p: MethodHookParam) {
                                        try {
                                            if (p.result is Long) {
                                                p.result = targetSteps
                                            } else if (p.result is Int) {
                                                p.result = targetSteps.toInt()
                                            }
                                            LogX.d("[步数] 已修改为 $targetSteps")
                                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    }
                                })
                            LogX.hookSuccess(cls, method.name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }

        // Hook SensorManager 加速度计（微信运动用传感器计步�?
        try {
            val smCls = XposedHelpers.findClassIfExists("android.hardware.SensorManager", lpparam.classLoader)
            if (smCls != null) {
                XposedHelpers.findAndHookMethod(smCls, "getDefaultSensor", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // TYPE_STEP_COUNTER = 19, TYPE_STEP_DETECTOR = 18
                            val type = p.args[0] as Int
                            if (type == 18 || type == 19) {
                                LogX.d("[步数] 拦截步数传感器请�?)
                            }
                        }
                    })
                LogX.hookSuccess("SensorManager", "getDefaultSensor")
            }
        } catch (_: Throwable) {}
    }

    /** 禁用微信热更新：阻止 TinkerClassLoader 加载补丁 dex */
    private fun hookDisableHotUpdate(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.tencent.tinker.loader.TinkerLoader",
            "com.tencent.tinker.loader.app.TinkerApplication",
            "com.tencent.tinker.loader.TinkerDexLoader"
        )
        for (cls in candidates) {
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("loadTinker", true) ||
                        method.name.contains("tryLoad", true) ||
                        method.name.contains("loadDex", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, method.name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(p: MethodHookParam) {
                                        LogX.d("[热更新] 已拦�?Tinker 加载")
                                        p.result = null  // 阻止执行
                                    }
                                })
                            LogX.hookSuccess(cls, method.name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 朋友圈伪集赞：Hook 点赞数显示，追加虚假点赞�?*/
    private fun hookMomentFakeLike(lpparam: XC_LoadPackage.LoadPackageParam, cfg: MicroXConfig) {
        val fakeAdd = 88  // 追加88个赞
        val candidates = listOf(
            "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
            "com.tencent.mm.plugin.sns.model.ae",
            "com.tencent.mm.plugin.sns.ui.SnsLinearLayout"
        )
        for (cls in candidates) {
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("getLikeCount", true) ||
                        method.name.contains("bindLike", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, method.name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(p: MethodHookParam) {
                                        try {
                                            if (p.result is Int) {
                                                p.result = (p.result as Int) + fakeAdd
                                            }
                                            LogX.d("[伪集赞] 点赞�?${fakeAdd}")
                                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    }
                                })
                            LogX.hookSuccess(cls, method.name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 去除9人转发限制：Hook 转发数量检查，强制返回不限 */
    private fun hookUnlimitedForward(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.tencent.mm.modelmulti.a",
            "com.tencent.mm.plugin.forward.ui.ForwardUI",
            "com.tencent.mm.ui.contact.a"
        )
        for (cls in candidates) {
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("checkForward", true) ||
                        method.name.contains("getForwardLimit", true) ||
                        method.name.contains("maxSelected", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, method.name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(p: MethodHookParam) {
                                        try {
                                            if (p.result is Int) {
                                                p.result = 999  // 放大�?99
                                            }
                                            LogX.d("[转发] 限制已解�?)
                                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    }
                                })
                            LogX.hookSuccess(cls, method.name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 自动发送原图：Hook 图片发送，强制设置原图标志 */
    private fun hookAutoOriginalImage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.tencent.mm.modelimage",
            "com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI",
            "com.tencent.mm.modelimage.a"
        )
        for (cls in candidates) {
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("sendImage", true) ||
                        method.name.contains("isOriginal", true) ||
                        method.name.contains("setOriginal", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, method.name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(p: MethodHookParam) {
                                        try {
                                            // �?Boolean 参数设为 true
                                            for (i in p.args.indices) {
                                                if (p.args[i] is Boolean) {
                                                    p.args[i] = true
                                                }
                                            }
                                            LogX.d("[原图] 已强制原图发�?)
                                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    }
                                })
                            LogX.hookSuccess(cls, method.name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }
}
