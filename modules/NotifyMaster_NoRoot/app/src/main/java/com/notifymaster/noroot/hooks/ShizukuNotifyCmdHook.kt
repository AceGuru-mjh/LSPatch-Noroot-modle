package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import com.notifymaster.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 通知增强 Hook（adb-level ONLY — NO root）
 *
 * 通过 Shizuku 执行 cmd notification / dumpsys / settings 命令
 * 实现系统级通知管理（需 Shizuku 运行中）。
 *
 * 功能：
 *  [1] cmd notification list              — 获取所有通知
 *  [2] cmd notification cancel <pkg> <tag> <id> — 移除垃圾通知
 *  [3] dumpsys notification | grep <pkg>  — 按包名过滤
 *  [4] settings put global heads_up_notifications_enabled 0 — 关闭悬浮通知
 *  [5] settings put secure enabled_notification_listeners <component> — 设置通知监听
 *  [6] settings put global zen_mode 1     — 开启勿扰模式(DND)
 *  [7] settings put system notification_sound <uri> — 自定义通知声音
 *  [8] cmd statusbar expand-notifications — 展开通知栏
 *
 * 硬性限制：
 *  - 仅使用 Shizuku adb-level 命令，不涉及 root
 *  - 所有命令 wrapped in try-catch + isAvailable() 检查
 *  - 不写 /sys/proc/system，不 setprop，不 mount
 */
object ShizukuNotifyCmdHook {

    private var cfg: NotifyConfig? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.shizukuNotifyCmdEnabled) return
        this.cfg = cfg
        LogX.i("Shizuku 通知增强 Hook 已加载（adb-level）")

        try {
            if (!ShizukuHelper.isAvailable()) {
                LogX.w("Shizuku 不可用，跳过所有通知命令增强")
                return
            }
            LogX.i("Shizuku 可用，通知命令增强就绪")
        } catch (e: Throwable) {
            LogX.e("Shizuku 检测异常", e)
        }

        try {
            val appClass = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return
            XposedHelpers.findAndHookMethod(appClass, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    LogX.i("Application onCreate 触发 — Shizuku 通知命令就绪")
                    setHeadsUpEnabled(false)
                }
            })
        } catch (e: Throwable) {
            LogX.e("Hook Application.onCreate 失败", e)
        }
    }

    // ===== cmd notification 命令 =====

    /** 列出所有活动通知 */
    fun listNotifications(): String? {
        if (!isReady()) return null
        return try {
            ShizukuHelper.execShell("cmd notification list")
        } catch (e: Throwable) {
            LogX.e("cmd notification list 失败", e)
            null
        }
    }

    /** 取消指定通知 */
    fun cancelNotification(pkg: String, tag: String, id: Int): Boolean {
        if (!isReady()) return false
        return try {
            val cmd = "cmd notification cancel $pkg $tag $id"
            ShizukuHelper.execShell(cmd)
            LogX.i("已取消通知: $pkg/$tag/$id")
            true
        } catch (e: Throwable) {
            LogX.e("取消通知失败: $pkg/$tag/$id", e)
            false
        }
    }

    // ===== dumpsys 命令 =====

    /** dump 通知服务并按包名过滤 */
    fun dumpNotificationsByPackage(pkg: String): String? {
        if (!isReady()) return null
        return try {
            val cmd = "dumpsys notification | grep $pkg"
            ShizukuHelper.execShell(cmd)
        } catch (e: Throwable) {
            LogX.e("dumpsys notification 过滤失败: $pkg", e)
            null
        }
    }

    // ===== settings 控制 =====

    /** 关闭/开启悬浮通知 */
    fun setHeadsUpEnabled(enabled: Boolean): Boolean {
        if (!isReady()) return false
        return try {
            val value = if (enabled) "1" else "0"
            ShizukuHelper.execShell("settings put global heads_up_notifications_enabled $value")
            LogX.i("悬浮通知已${if (enabled) "开启" else "关闭"}")
            true
        } catch (e: Throwable) {
            LogX.e("设置 heads_up 失败", e)
            false
        }
    }

    /** 设置通知监听组件 */
    fun setNotificationListeners(component: String): Boolean {
        if (!isReady()) return false
        return try {
            ShizukuHelper.execShell("settings put secure enabled_notification_listeners '$component'")
            LogX.i("通知监听已设置: $component")
            true
        } catch (e: Throwable) {
            LogX.e("设置通知监听失败", e)
            false
        }
    }

    /** 勿扰模式(DND) */
    fun setZenMode(mode: Int): Boolean {
        if (!isReady()) return false
        return try {
            ShizukuHelper.execShell("settings put global zen_mode $mode")
            LogX.i("勿扰模式已设为: $mode")
            true
        } catch (e: Throwable) {
            LogX.e("设置 zen_mode 失败", e)
            false
        }
    }

    /** 自定义通知声音 */
    fun setNotificationSound(uri: String): Boolean {
        if (!isReady()) return false
        return try {
            ShizukuHelper.execShell("settings put system notification_sound '$uri'")
            LogX.i("通知声音已设为: $uri")
            true
        } catch (e: Throwable) {
            LogX.e("设置通知声音失败", e)
            false
        }
    }

    // ===== 系统界面 =====

    /** 展开通知栏 */
    fun expandNotifications(): Boolean {
        if (!isReady()) return false
        return try {
            ShizukuHelper.execShell("cmd statusbar expand-notifications")
            LogX.i("通知栏已展开")
            true
        } catch (e: Throwable) {
            LogX.e("展开通知栏失败", e)
            false
        }
    }

    private fun isReady(): Boolean {
        return cfg?.shizukuNotifyCmdEnabled == true && ShizukuHelper.isAvailable()
    }
}
