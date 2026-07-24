package com.notifymaster.noroot.hooks

import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通知美化 Hook（NoRoot 版 - 仅应用进程内）
 *
 * 功能：Hook Notification.Builder.build，修改通知样式：
 *  - 设置颜色（colorAccent）
 *  - 修改标题前缀
 *  - 可选覆盖图标
 *
 * 拦截路径：
 *  1. Notification.Builder.build()
 *  2. Notification.Builder.Builder(Context, String channelId) 构造时拦截
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Notification.Builder
 *  - 不修改系统通知渠道配置（NoRoot 版无系统权限）
 *  - 仅对应用自己 build 的通知生效
 */
object NotifyBeautifyHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notifyBeautifyEnabled) return
        LogX.i("通知美化启动（颜色=${Integer.toHexString(cfg.beautifyColor)} 前缀=\"${cfg.beautifyTitlePrefix}\"）")

        hookBuild(lpparam, cfg)
    }

    private fun hookBuild(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return

        // Hook build() 方法
        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            val notif = p.result ?: return
                            // 应用美化参数到 builder（部分参数仅 build 前生效，这里也修改已构建的 Notification）
                            applyColorToNotification(notif, cfg)
                            applyTitlePrefixToNotification(notif, cfg)
                        } catch (e: Throwable) {
                            LogX.w("通知美化 build 后处理异常: ${e.message}")
                        }
                    }

                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            // 在 build 前调用 setColor
                            try {
                                XposedHelpers.callMethod(builder, "setColor", cfg.beautifyColor)
                            } catch (e: Throwable) { LogX.w("setColor 异常: ${e.message}") }
                            // 修改标题前缀
                            if (cfg.beautifyTitlePrefix.isNotEmpty()) {
                                try {
                                    val extras = XposedHelpers.callMethod(builder, "getExtras")
                                    val origTitle = XposedHelpers.callMethod(extras, "getCharSequence", "android.title") as? CharSequence
                                    if (origTitle != null) {
                                        val newTitle = cfg.beautifyTitlePrefix + origTitle.toString()
                                        XposedHelpers.callMethod(extras, "putCharSequence", "android.title", newTitle as CharSequence)
                                    }
                                } catch (e: Throwable) { LogX.w("标题前缀设置异常: ${e.message}") }
                            }
                        } catch (e: Throwable) {
                            LogX.w("通知美化 build 前处理异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build", e) }
    }

    /** 直接修改 Notification 的 color 字段（兜底） */
    private fun applyColorToNotification(notif: Any, cfg: NotifyConfig) {
        try {
            XposedHelpers.setIntField(notif, "color", cfg.beautifyColor)
        } catch (e: Throwable) { LogX.w("通知颜色修改失败: ${e.message}") }
    }

    /** 修改 Notification extras 中的 title */
    private fun applyTitlePrefixToNotification(notif: Any, cfg: NotifyConfig) {
        if (cfg.beautifyTitlePrefix.isEmpty()) return
        try {
            val extras = XposedHelpers.callMethod(notif, "getExtras") ?: return
            val origTitle = XposedHelpers.callMethod(extras, "getCharSequence", "android.title") as? CharSequence ?: return
            val newTitle = cfg.beautifyTitlePrefix + origTitle.toString()
            XposedHelpers.callMethod(extras, "putCharSequence", "android.title", newTitle as CharSequence)
        } catch (e: Throwable) { LogX.w("标题前缀注入失败: ${e.message}") }
    }
}
