package com.notifymaster.noroot

import android.app.Application
import com.notifymaster.noroot.hooks.*
import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.ShizukuHelper
import com.notifymaster.noroot.utils.ConfigManager
import com.notifymaster.noroot.utils.HookConfigReader
import com.notifymaster.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * NotifyMaster NoRoot - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 通知过滤  [2] 防通知撤回  [3] 通知历史  [4] 通知美化
 *    [实验] 通知分组 / 优先级覆盖 / 静默通知
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅 Hook 应用进程内通知调用，不修改系统通知服务
 *  - 不 Hook system_server / NotificationManagerService
 *  - 不调用 Shizuku 做系统级通知策略修改
 *  - 通知历史仅保存在内存中，进程重启后消失
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("NotifyMaster NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // LSPatch 合规: 跳过系统进程 + 仅主进程加载(避免子进程ClassLoader隔离问题)
        if (lpparam.packageName == "android") return
        if (!lpparam.isFirstApplication) return
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        cfg.packageName = pkg
        LogX.i("配置: 总开关=${cfg.masterEnabled} 过滤=${cfg.notifyFilterEnabled} " +
                "防撤回=${cfg.antiRecallNotifyEnabled} 历史=${cfg.notifyHistoryEnabled} " +
                "美化=${cfg.notifyBeautifyEnabled} [实验]分组=${cfg.batchNotifyEnabled} " +
                "优先级=${cfg.priorityOverrideEnabled} 静默=${cfg.silentNotifyEnabled} " +
                "[Shizuku]通知命令=${cfg.shizukuNotifyCmdEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能 =====
        if (cfg.notifyFilterEnabled) NotifyFilterHook.apply(lpparam, cfg)
        if (cfg.antiRecallNotifyEnabled) AntiRecallNotifyHook.apply(lpparam, cfg)
        if (cfg.notifyHistoryEnabled) NotifyHistoryHook.apply(lpparam, cfg)
        if (cfg.notifyBeautifyEnabled) NotifyBeautifyHook.apply(lpparam, cfg)

        // ===== 实验性功能 =====
        if (cfg.batchNotifyEnabled) BatchNotifyHook.apply(lpparam, cfg)
        if (cfg.priorityOverrideEnabled) PriorityOverrideHook.apply(lpparam, cfg)
        if (cfg.silentNotifyEnabled) SilentNotifyHook.apply(lpparam, cfg)

        // ===== Shizuku 增强 =====
        try {
            if (cfg.shizukuNotifyCmdEnabled) ShizukuNotifyCmdHook.apply(lpparam, cfg)
        } catch (e: Throwable) {
            LogX.w("Shizuku 通知命令 Hook 加载失败: ${e.message}")
        }

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.netease.cloudmusic", "com.tencent.wmusic",
        "com.sina.weibo", "com.zhihu.android",
        "com.baidu.searchbox", "com.ss.android.article.news"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): NotifyConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { NotifyConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) ConfigManager.init(app)
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
