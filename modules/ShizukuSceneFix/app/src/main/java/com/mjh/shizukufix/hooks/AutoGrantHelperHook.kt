package com.mjh.shizukufix.hooks

import android.app.Activity
import android.app.Dialog
import com.mjh.shizukufix.XposedLoader
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Shizuku 授权对话框自动确认辅助
 *
 * 工作原理：
 *  - Hook Shizuku 授权管理 Activity 的 onCreate / onResume
 *  - 检测到当前请求来自 Scene (com.omarea.vtools) 时，
 *    延迟 cfg.autoGrantDelayMs 后自动调用 findViewById 找到「允许」按钮并 performClick
 *
 * 注意：
 *  - 实验性功能，Shizuku 不同版本按钮 ID 可能变化
 *  - 自动授权有风险，请谨慎开启，避免被恶意 APP 利用
 *  - 仅在用户主动开启本功能时生效，默认关闭
 *
 * 硬性限制：
 *  - 仅在 Shizuku 自身进程内 Hook
 *  - 不修改 Shizuku 权限存储数据，仅在 UI 层模拟点击
 */
object AutoGrantHelperHook {

    /** Shizuku 授权管理 Activity 候选类名 */
    private val AUTH_ACTIVITY_CANDIDATES = arrayOf(
        "rikka.shizuku.manager.AuthorizationActivity",
        "moe.shizuku.manager.AuthorizationActivity",
        "rikka.shizuku.manager.ui.AuthorizationActivity",
        "moe.shizuku.manager.ui.AuthorizationActivity",
        "rikka.shizuku.manager.RequestPermissionActivity",
        "moe.shizuku.manager.RequestPermissionActivity"
    )

    /** 「允许」按钮候选 ID 资源名 */
    private val ALLOW_BUTTON_RES_NAMES = arrayOf(
        "allow_button", "btn_allow", "allow", "ok", "positive_button", "btn_ok"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.autoGrantHelperEnabled) return
        LogX.i("【实验性】Shizuku 自动授权辅助启动（延迟 ${cfg.autoGrantDelayMs}ms）")

        hookAuthorizationActivityOnResume(lpparam, cfg)
    }

    /** Hook 授权 Activity 的 onResume，在 UI 渲染完成后模拟点击允许按钮 */
    private fun hookAuthorizationActivityOnResume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        for (clsName in AUTH_ACTIVITY_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(cls, "onResume", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val activity = p.thisObject as? Activity ?: return
                        if (!isSceneRequest(activity)) return
                        LogX.i("  [AutoGrant] 检测到 Scene 授权请求，调度自动确认")
                        scheduleAutoAllowClick(activity, cfg)
                    }
                })
                LogX.hookSuccess(clsName, "onResume")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // 同时 Hook onCreate 作为冗余
            try {
                XposedHelpers.findAndHookMethod(cls, "onCreate", android.os.Bundle::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val activity = p.thisObject as? Activity ?: return
                        if (!isSceneRequest(activity)) return
                        scheduleAutoAllowClick(activity, cfg)
                    }
                })
                LogX.hookSuccess(clsName, "onCreate")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 判断当前授权请求是否来自 Scene */
    private fun isSceneRequest(activity: Activity): Boolean {
        val intent = activity.intent ?: return false
        val pkg = intent.getStringExtra("package_name")
            ?: intent.getStringExtra("packageName")
            ?: return false
        return pkg == XposedLoader.SCENE_PACKAGE
    }

    /** 延迟后查找允许按钮并 performClick */
    private fun scheduleAutoAllowClick(activity: Activity, cfg: ShizukuFixConfig) {
        Thread {
            try { Thread.sleep(cfg.autoGrantDelayMs) } catch (_: InterruptedException) {}
            try {
                val view = findAllowButton(activity) ?: run {
                    LogX.d("  [AutoGrant] 未找到允许按钮")
                    null
                }
                activity.runOnUiThread {
                    try {
                        view?.performClick()
                        LogX.i("  [AutoGrant] 已自动点击允许按钮")
                    } catch (t: Throwable) {
                        LogX.e("  [AutoGrant] 自动点击失败", t)
                    }
                }
            } catch (t: Throwable) {
                LogX.e("  [AutoGrant] 自动授权异常", t)
            }
        }.start()
    }

    /** 通过资源名查找允许按钮 */
    private fun findAllowButton(activity: Activity): android.view.View? {
        val res = activity.resources
        val pkg = activity.packageName
        for (name in ALLOW_BUTTON_RES_NAMES) {
            val id = res.getIdentifier(name, "id", pkg)
            if (id != 0) {
                val v = activity.findViewById<android.view.View>(id)
                if (v != null) return v
            }
        }
        return null
    }
}
