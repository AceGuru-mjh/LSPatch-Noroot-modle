package com.batteryopt.noroot.hooks

import android.app.ActivityManager
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】后台相机调用阻断 Hook（应用层）
 *
 * 功能：
 *  - Hook Camera2 (CameraManager.openCamera) 与 Camera.open
 *  - 当 APP 处于后台时，直接抛出异常阻止相机打开
 *  - 减少 APP 后台偷偷打开摄像头导致的耗电和隐私风险
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅作用于当前 APP 的相机调用
 *  - 不影响系统相机服务
 *  - 后台判断使用 ActivityManager.RunningAppProcessInfo，
 *    IMPORTANCE_FOREGROUND 才放行
 *
 * 注意：
 *  - 部分视频通话 APP 在通话时可能"后台"打开摄像头，开启本功能会影响通话
 *  - 默认关闭，建议仅在确认无相机后台使用需求时启用
 */
object CameraBackgroundBlockHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】后台相机阻断启动")
        hookCamera2(lpparam)
        hookCameraLegacy(lpparam)
    }

    /** Hook CameraManager.openCamera (Android 5.0+) */
    private fun hookCamera2(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists(
            "android.hardware.camera2.CameraManager", lpparam.classLoader
        ) ?: return

        // openCamera(String cameraId, DeviceStateCallback, Handler)
        try {
            XposedHelpers.findAndHookMethod(
                cls, "openCamera",
                String::class.java,
                "android.hardware.camera2.CameraDevice.StateCallback",
                "android.os.Handler",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isAppInBackground(p.thisObject)) {
                            val cameraId = p.args[0] as? String ?: "?"
                            LogX.w("后台 openCamera 被拦截: cameraId=$cameraId")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("CameraManager", "openCamera")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook android.hardware.Camera.open（旧 API） */
    private fun hookCameraLegacy(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists(
            "android.hardware.Camera", lpparam.classLoader
        ) ?: return

        // open(int cameraId)
        try {
            XposedHelpers.findAndHookMethod(
                cls, "open",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isAppInBackground(null)) {
                            val id = p.args[0] as Int
                            LogX.w("后台 Camera.open($id) 被拦截")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("Camera", "open(int)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // open() 无参
        try {
            XposedHelpers.findAndHookMethod(
                cls, "open",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isAppInBackground(null)) {
                            LogX.w("后台 Camera.open() 被拦截")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("Camera", "open()")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** 判断当前 APP 是否在后台 */
    private fun isAppInBackground(any: Any?): Boolean {
        return try {
            // 反射获取 ActivityThread.currentApplication
            val atCls = Class.forName("android.app.ActivityThread")
            val app = atCls.getMethod("currentApplication").invoke(null) ?: return false
            val ctx = app as? android.content.Context ?: return false
            val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return false
            val pid = android.os.Process.myPid()
            val processes = am.runningAppProcesses ?: return false
            val mine = processes.firstOrNull { it.pid == pid } ?: return false
            mine.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } catch (_: Throwable) {
            // 无法判断时不要阻断，避免影响正常功能
            false
        }
    }
}
