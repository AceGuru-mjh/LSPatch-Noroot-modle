package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogStore
import com.gameunlocker.noroot.utils.LogX
import com.gameunlocker.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 游戏渲染帧率强制解锁 Hook
 *
 * 硬性限制（NoRoot 版）：
 *  - 全部 Hook 在游戏应用进程内执行，不修改系统级 SurfaceFlinger
 *  - 屏幕硬件刷新率上限由 Shizuku settings put system 提示（可选，需 Shizuku adb 授权）
 *  - 无 Root 无法直接写入 /sys/class/graphics/fb0/ 节点
 *
 * 拦截路径：
 *  1. Display.getMode/getSupportedModes/getRefreshRate -> 报告目标帧率
 *  2. Surface.setFrameRate -> 覆盖游戏请求的帧率
 *  3. Unity 引擎 Application.targetFrameRate -> 强制目标帧率
 *  4. Unreal 引擎 GameActivity 初始化 -> 注入帧率参数
 *  5. 各厂商游戏加速器帧率锁定 -> 空实现屏蔽
 */
object FrameRateUnlockHook {

    private var fps = 120

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.frameRateUnlockEnabled) return
        // 自动模式取屏幕最高刷，否则取用户设定值
        fps = if (cfg.targetFps <= 0) detectMaxRefreshRate() else cfg.targetFps
        LogX.i("帧率解锁: ${fps}fps（应用层）")
        try { LogStore.add("unlocked", "解锁帧率: ${fps}fps") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookDisplay(lpparam)
        hookSurface(lpparam)
        hookUnity(lpparam)
        hookUnreal(lpparam)
        hookOemLockers(lpparam)

        // 通过 Shizuku 提示系统刷新率（可选，需 Shizuku adb 级授权）
        applyRefreshRateHint(fps)
    }

    private fun hookDisplay(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val dc = XposedHelpers.findClassIfExists(
                "android.view.Display", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(dc, "getMode", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val mode = p.result ?: return
                            mode.javaClass.getField("refreshRate").setFloat(mode, fps.toFloat())
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(dc, "getSupportedModes", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val modes = p.result as? Array<*> ?: return
                        for (m in modes) {
                            try {
                                m?.javaClass?.getField("refreshRate")?.setFloat(m, fps.toFloat())
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(dc, "getRefreshRate", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = fps.toFloat() }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("Display", "getMode/getSupportedModes/getRefreshRate -> ${fps}fps")
        } catch (e: Throwable) {
            LogX.hookFailed("Display", "frameRate", e)
        }
    }

    private fun hookSurface(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sc = XposedHelpers.findClassIfExists("android.view.Surface", lpparam.classLoader) ?: return
            // Android 11+ setFrameRate(float, int)
            try {
                XposedHelpers.findAndHookMethod(sc, "setFrameRate",
                    Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val rq = p.args[0] as Float
                            if (rq > 0 && rq < fps) p.args[0] = fps.toFloat()
                        }
                    })
                LogX.hookSuccess("Surface", "setFrameRate")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Surface", "setFrameRate", e)
        }
    }

    private fun hookUnity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val unityPlayer = XposedHelpers.findClassIfExists(
                "com.unity3d.player.UnityPlayer", lpparam.classLoader) ?: return
            LogX.i("检测到 Unity 引擎")
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            XposedHelpers.callStaticMethod(unityPlayer, "setTargetFrameRate", fps)
                            LogX.d("Unity targetFrameRate = $fps")
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookUnreal(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ga = XposedHelpers.findClassIfExists(
                "com.epicgames.unreal.GameActivity", lpparam.classLoader) ?: return
            LogX.i("检测到 Unreal 引擎")
            XposedHelpers.findAndHookMethod(ga, "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            XposedHelpers.callStaticMethod(ga,
                                "nativeSetGlobalActivity",
                                p.thisObject,
                                "FullscreenFrameRate=$fps")
                            LogX.d("Unreal FullscreenFrameRate=$fps")
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookOemLockers(lpparam: XC_LoadPackage.LoadPackageParam) {
        val targets = listOf(
            "com.miui.gamebooster.service.GameBoosterService" to "onFrameRateLimit",
            "com.xiaomi.joyose.JoyoseManager" to "getPerformanceLevel",
            "com.miui.powerkeeper.PowerKeeperService" to "notifyFrameRateLimit",
            "com.vivo.gamewatch.GameWatchService" to "setMaxFrameRate",
            "com.vivo.pem.PowerExpertService" to "onPerformanceMode",
            "com.oplus.games.GameSpaceService" to "lockFrameRate",
            "com.oplus.hyperboost.HyperBoostEngine" to "getMaxRefreshRate",
            "com.samsung.android.game.gametools.GameBoosterService" to "setFrameRateCap",
            "com.samsung.android.gos.GameOptimizingService" to "onPerformanceCheck"
        )
        for ((cls, method) in targets) {
            try {
                val c = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
                XposedHelpers.findAndHookMethod(c, method, XC_MethodReplacement.DO_NOTHING)
                LogX.d("OEM 锁屏蔽: $cls.$method")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 检测屏幕硬件最大刷新率（应用层方式） */
    private fun detectMaxRefreshRate(): Int {
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val max = XposedHelpers.callStaticMethod(sp, "getInt",
                "ro.vendor.dfps.refresh_rate.max", 120) as? Int ?: 120
            LogX.d("屏幕最大刷新率检测: ${max}Hz")
            max
        } catch (_: Throwable) { 120 }
    }

    /**
     * 通过 Shizuku settings put system 提示系统刷新率（可选，Shizuku 不可用则跳过）
     * 不进行 setprop 系统属性修改
     */
    private fun applyRefreshRateHint(targetFps: Int) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku 不可用，跳过系统刷新率提示（应用层 Hook 仍生效）")
            return
        }
        ShizukuHelper.execShell("settings put system peak_refresh_rate $targetFps")
        ShizukuHelper.execShell("settings put system min_refresh_rate $targetFps")
        LogX.i("Shizuku 系统刷新率提示已设置: peak=$targetFps")
    }
}
