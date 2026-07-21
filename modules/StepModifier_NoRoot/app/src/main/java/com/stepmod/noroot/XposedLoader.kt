package com.stepmod.noroot

import android.app.Application
import com.stepmod.noroot.hooks.*
import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.ConfigManager
import com.stepmod.noroot.utils.HookConfigReader
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * StepModifier NoRoot - Xposed 模块唯一入口
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
 *    [1] StepSensorHook   [2] StepReportHook   [3] StepCounterHook
 *    [实验] SensorBlockHook / MultiAppSyncHook / StepHistoryFakeHook
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅应用进程内 Java 层 Hook
 *  - 不修改系统传感器服务（system_server）
 *  - 不写 /sys /proc 内核节点
 *  - 不调用 Shizuku 做真Root操作
 *  - 伪造值仅在当前进程生命周期内有效
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("StepModifier NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
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
        LogX.i("配置: 总开关=${cfg.masterEnabled} 步数修改=${cfg.stepModifyEnabled} " +
                "目标步数=${cfg.customSteps} 波动±${cfg.randomFluctuation} " +
                "[实验]传感器阻断=${cfg.sensorBlockEnabled} 多APP同步=${cfg.multiAppSyncEnabled} 历史伪造=${cfg.stepHistoryFakeEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能 =====
        if (cfg.stepModifyEnabled) {
            StepSensorHook.apply(lpparam, cfg)
            StepReportHook.apply(lpparam, cfg)
            StepCounterHook.apply(lpparam, cfg)
        }

        // ===== 实验性功能 =====
        if (cfg.sensorBlockEnabled) SensorBlockHook.apply(lpparam, cfg)
        if (cfg.multiAppSyncEnabled) MultiAppSyncHook.apply(lpparam, cfg)
        if (cfg.stepHistoryFakeEnabled) StepHistoryFakeHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.eg.android.AlipayGphone",     // 支付宝
        "com.tencent.mm",                   // 微信
        "com.tencent.mobileqq",             // QQ
        "com.tencent.tim",                  // TIM
        "com.xiaomi.hm.health",             // 小米运动健康
        "com.huawei.health",                // 华为运动健康
        "com.codoon.gps",                   // 咕咚
        "com.joyrun.gps",                   // 悦跑圈
        "com.keepfitness",                  // Keep
        "com.ss.android.ugc.aweme",         // 抖音
        "com.smile.gifmaker",               // 快手
        "com.netease.cloudmusic",           // 网易云音乐
        "com.tencent.wmusic",               // QQ音乐
        "com.taobao.taobao",                // 淘宝
        "com.jingdong.app.mall"             // 京东
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): StepConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { StepConfig(packageName = "global") }
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
