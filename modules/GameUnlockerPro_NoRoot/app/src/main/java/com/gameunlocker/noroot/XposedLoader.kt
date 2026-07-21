package com.gameunlocker.noroot

import android.app.Application
import com.gameunlocker.noroot.hooks.*
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.ConfigManager
import com.gameunlocker.noroot.utils.HookConfigReader
import com.gameunlocker.noroot.utils.LogStore
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GameUnlocker NoRoot - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程：
 *  游戏启动 -> handleLoadPackage ->
 *    判断是否为目标游戏 ->
 *    读取全局配置 ->
 *    [1] 环境隐藏(最先执行，防检测)
 *    [2] 机型伪装(Build属性)
 *    [3] 帧率解锁(Display/Surface/引擎)
 *    [4] 进程优化(线程优先级 + 热状态)
 *    [5] 分辨率伪装(可选)
 *    [实验] 触摸采样/网络延迟/音频优先/内存整理
 *
 * 硬性限制（NoRoot 版严格遵守）：
 *  - 仅在游戏进程内做 Java 层 Hook
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不屏蔽系统温控、不修改 CPU/GPU 调频
 *  - 不调用 Shizuku 做真 Root 操作（仅 settings put system 帧率提示）
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("GameUnlocker NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetGame(pkg)) return

            LogX.i("===== 游戏启动: $pkg =====")
            currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        LogX.i("配置: 总开关=${cfg.masterEnabled} 伪装=${cfg.deviceSpoofEnabled} " +
                "帧率=${cfg.targetFps}fps 隐藏=${cfg.detectionHideEnabled} 优化=${cfg.processOptimizeEnabled} " +
                "Shizuku调优=${cfg.shizukuSystemTuneEnabled} " +
                "[实验]触摸=${cfg.touchSamplingBoostEnabled} 网络=${cfg.networkLatencyOptEnabled} " +
                "音频=${cfg.audioPriorityBoostEnabled} 内存=${cfg.memoryDefragEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有 Hook")
            return
        }

        // ===== [1] 环境隐藏（最先执行，防检测）=====
        if (cfg.detectionHideEnabled) GameDetectionHideHook.apply(lpparam, cfg)

        // ===== [2] 机型伪装 =====
        if (cfg.deviceSpoofEnabled) DeviceSpoofHook.apply(lpparam, cfg)

        // ===== [3] 帧率解锁 =====
        if (cfg.frameRateUnlockEnabled) FrameRateUnlockHook.apply(lpparam, cfg)

        // ===== [4] 进程性能优化 =====
        if (cfg.processOptimizeEnabled) ProcessOptimizerHook.apply(lpparam, cfg)

        // ===== [5] 分辨率伪装（可选）=====
        if (cfg.resolutionSpoofEnabled) ResolutionSpoofHook.apply(lpparam, cfg)

        // ===== Shizuku 系统级调优（adb级 dumpsys/wm/cmd） =====
        if (cfg.shizukuSystemTuneEnabled) ShizukuSystemTuneHook.apply(lpparam, cfg)

        // ===== 实验性功能 =====
        if (cfg.touchSamplingBoostEnabled) TouchSamplingBoostHook.apply(lpparam, cfg)
        if (cfg.networkLatencyOptEnabled) NetworkLatencyOptHook.apply(lpparam, cfg)
        if (cfg.audioPriorityBoostEnabled) AudioPriorityBoostHook.apply(lpparam, cfg)
        if (cfg.memoryDefragEnabled) MemoryDefragHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部 Hook 就绪: $pkg =====")
        } catch (e: Throwable) {
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
        }
    }

    /** 目标游戏包名白名单 */
    private fun isTargetGame(pkg: String) = pkg in listOf(
        "com.tencent.tmgp.sgame",                  // 王者荣耀
        "com.miHoYo.Yuanshen",                     // 原神国内版
        "com.miHoYo.GenshinImpact",                // 原神国际版
        "com.tencent.tmgp.pubgmhd",                // 和平精英
        "com.tencent.ig",                          // PUBG Mobile
        "com.miHoYo.hkrpg",                        // 崩坏星穹铁道
        "com.tencent.tmgp.cod",                    // 使命召唤国内版
        "com.activision.callofduty.shooter",       // CODM 国际版
        "com.tencent.tmgp.gnyx",                   // 高能英雄
        "com.gameblackmyth.mobile",                // 黑神话手游
        "com.miHoYo.ZenlessZoneZero",              // 绝区零
        "com.kurogame.kjq"                         // 鸣潮
    )

    /** 读取配置：优先 XSharedPreferences，回退 Context */
    private fun loadConfig(): GameConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { GameConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) { ConfigManager.init(app); LogStore.init(app) }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app); LogStore.init(app) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
