package com.mjh.shizukufix.hooks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mjh.shizukufix.XposedLoader
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import com.mjh.shizukufix.utils.PackageHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Path A: Hook Scene 申请 Shizuku 权限流程
 *
 * 原 scene/ScenePermissionRequester.java 转 Kotlin。
 *
 * 工作原理：
 *  - Hook Scene 的 Application.onCreate / MainActivity.onCreate
 *  - 启动后延迟 2 秒，主动向 Shizuku Manager 发送 REQUEST_PERMISSION Intent
 *  - 三层降级：startActivity 显式 Intent -> 广播 -> 显式 Activity Action
 *
 * 适用场景：Scene 启动后未自动弹出 Shizuku 授权对话框时，
 *          本 Hook 主动触发授权请求。
 */
object ScenePermissionRequesterHook {

    private const val ACTION_REQUEST_PERMISSION = "moe.shizuku.manager.intent.action.REQUEST_PERMISSION"
    private const val PERMISSION_ACTIVITY_ACTION = "moe.shizuku.manager.action.MANAGE_PERMISSION"

    /** Shizuku 管理器候选包名（含变体） */
    private val SHIZUKU_MANAGER_PACKAGES = arrayOf(
        "rikka.shizuku.manager",
        "moe.shizuku.manager",
        "moe.shizuku.privileged.api"
    )

    /** Scene 主 Activity 候选类名 */
    private val SCENE_ACTIVITY_CANDIDATES = arrayOf(
        "com.omarea.vtools.activities.MainActivity",
        "com.omarea.vtools.MainActivity",
        "com.omarea.vtools.ui.MainActivity"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.sceneFixEnabled) return
        LogX.i("Path A: Hook Scene 申请 Shizuku 权限流程启动")

        hookApplication(lpparam)
        hookMainActivity(lpparam)
    }

    /** 优先 Hook com.omarea.vtools.App.onCreate，失败回退 android.app.Application.onCreate */
    private fun hookApplication(lpparam: XC_LoadPackage.LoadPackageParam) {
        val appCls = XposedHelpers.findClassIfExists("com.omarea.vtools.App", lpparam.classLoader)
        if (appCls != null) {
            try {
                XposedHelpers.findAndHookMethod(appCls, "onCreate", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        LogX.i("Scene Application.onCreate() called, scheduling permission request...")
                        schedulePermissionRequest(p.thisObject as? Context)
                    }
                })
                LogX.hookSuccess("com.omarea.vtools.App", "onCreate")
                return
            } catch (t: Throwable) {
                LogX.hookFailed("com.omarea.vtools.App", "onCreate", t)
            }
        }
        // 回退：通用 Application.onCreate 判定包名
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val ctx = p.thisObject as? Context ?: return
                        if (XposedLoader.SCENE_PACKAGE == ctx.packageName) {
                            LogX.i("Scene android.app.Application.onCreate()")
                            schedulePermissionRequest(ctx)
                        }
                    }
                })
            LogX.hookSuccess("android.app.Application", "onCreate(fallback)")
        } catch (t: Throwable) {
            LogX.hookFailed("android.app.Application", "onCreate", t)
        }
    }

    /** Hook Scene MainActivity.onCreate */
    private fun hookMainActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in SCENE_ACTIVITY_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(cls, "onCreate", Bundle::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        LogX.i("Scene MainActivity.onCreate() called")
                        schedulePermissionRequest(p.thisObject as? Activity)
                    }
                })
                LogX.hookSuccess(clsName, "onCreate")
                return
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
        LogX.i("未找到 Scene 主 Activity 候选类，跳过 MainActivity Hook")
    }

    /** 延迟 2 秒发送授权请求（避免 onCreate 阶段过早触发） */
    private fun schedulePermissionRequest(context: Context?) {
        if (context == null) return
        Thread {
            try { Thread.sleep(2000) } catch (_: InterruptedException) {}
            try {
                LogX.i("Executing Scene permission request...")
                requestShizukuPermissionViaIntent(context)
            } catch (t: Throwable) {
                LogX.e("Failed to request Shizuku permission", t)
            }
        }.start()
    }

    /** Layer 1: 显式 Intent 按包名逐个尝试 startActivity */
    private fun requestShizukuPermissionViaIntent(context: Context) {
        for (managerPkg in SHIZUKU_MANAGER_PACKAGES) {
            val intent = Intent(ACTION_REQUEST_PERMISSION).apply {
                setPackage(managerPkg)
                putExtra("package_name", XposedLoader.SCENE_PACKAGE)
                putExtra("uid", PackageHelper.getUid(context, XposedLoader.SCENE_PACKAGE))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                LogX.i("  Sent REQUEST_PERMISSION intent to: $managerPkg")
                return
            } catch (_: Exception) {
                LogX.d("  Failed to send intent to: $managerPkg")
            }
        }
        tryRequestViaBroadcast(context)
    }

    /** Layer 2: 广播兜底 */
    private fun tryRequestViaBroadcast(context: Context) {
        LogX.d("  Trying broadcast approach...")
        try {
            val broadcast = Intent(ACTION_REQUEST_PERMISSION).apply {
                putExtra("package_name", XposedLoader.SCENE_PACKAGE)
                putExtra("uid", PackageHelper.getUid(context, XposedLoader.SCENE_PACKAGE))
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(broadcast)
            LogX.i("  Sent broadcast REQUEST_PERMISSION")
        } catch (t: Throwable) {
            LogX.e("  Broadcast approach failed", t)
        }
        tryRequestViaExplicitActivity(context)
    }

    /** Layer 3: 显式 MANAGE_PERMISSION Activity Action 兜底 */
    private fun tryRequestViaExplicitActivity(context: Context) {
        LogX.d("  Trying explicit activity approach...")
        for (managerPkg in SHIZUKU_MANAGER_PACKAGES) {
            val intent = Intent(PERMISSION_ACTIVITY_ACTION).apply {
                setPackage(managerPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", XposedLoader.SCENE_PACKAGE)
                putExtra("uid", PackageHelper.getUid(context, XposedLoader.SCENE_PACKAGE))
            }
            try {
                context.startActivity(intent)
                LogX.i("  Opened permission activity in: $managerPkg")
                return
            } catch (_: Exception) {
                LogX.d("  Failed to open activity in: $managerPkg")
            }
        }
    }
}
