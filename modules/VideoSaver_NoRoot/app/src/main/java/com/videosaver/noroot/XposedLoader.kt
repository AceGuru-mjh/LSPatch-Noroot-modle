package com.videosaver.noroot

import android.app.Application
import com.videosaver.noroot.hooks.*
import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.utils.AntiDetectionHelper
import com.videosaver.noroot.utils.ConfigManager
import com.videosaver.noroot.utils.EnvDetector
import com.videosaver.noroot.utils.HookConfigReader
import com.videosaver.noroot.utils.LogStore
import com.videosaver.noroot.utils.LogX
import com.videosaver.noroot.utils.ModuleConflictDetector
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * VideoSaver NoRoot - Xposed 模块唯一入口
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
 *    [1] 抖音无水印  [2] 快手无水印  [3] 小红书无水印  [4] B站下载解锁
 *    [实验] 自动下载 / 去广告 / 原画质 / 批量下载
 *
 * 硬性限制（NoRoot 版严格遵守）：
 *  - 仅 Hook 应用进程内 Java 层方法
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不调用 Shizuku 做真 Root 操作
 *  - 不 Hook system_server
 *
 * LSPatch 合规（强制）：
 *  - xposedminversion = "93"
 *  - Manifest 声明 FOREGROUND_SERVICE 权限
 *  - handleLoadPackage 开头加 android/系统进程过滤 + isFirstApplication 过滤
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("VideoSaver NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return

            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            LogX.i("=== VideoSaver v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (EnvDetector.isLocalMode) "local" else "integrated"} ===")
            currentPkg = pkg

        initConfig(lpparam)
        if (!EnvDetector.isLocalMode) {
            try { Thread.sleep(100) } catch (_: Throwable) { }
        }
        LogX.i("环境: ${if (EnvDetector.isLocalMode) "LSPatch本地" else "LSPosed集成"}模式")
        if (ModuleConflictDetector.checkConflict()) {
            LogX.w("检测到模块冲突，跳过Hook")
            return
        }

        val cfg = loadConfig()
        LogX.i("配置: 总开关=${cfg.masterEnabled} 抖音=${cfg.douyinNoWatermark} " +
                "快手=${cfg.kuaishouNoWatermark} 小红书=${cfg.xhsNoWatermark} B站=${cfg.biliDownload} " +
                "capture=${cfg.shizukuCaptureEnabled} " +
                "[实验]自动下载=${cfg.autoDownloadEnabled} 去广告=${cfg.removeAdsEnabled} " +
                "原画质=${cfg.saveOriginalQualityEnabled} 批量下载=${cfg.batchDownloadEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能 =====
        if (cfg.douyinNoWatermark) DouyinNoWatermarkHook.apply(lpparam, cfg)
        if (cfg.kuaishouNoWatermark) KuaishouNoWatermarkHook.apply(lpparam, cfg)
        if (cfg.xhsNoWatermark) XhsNoWatermarkHook.apply(lpparam, cfg)
        if (cfg.biliDownload) BiliDownloadHook.apply(lpparam, cfg)

        // ===== Shizuku 系统级（adb级 screencap/input tap） =====
        if (cfg.shizukuCaptureEnabled) ShizukuCaptureHook.apply(lpparam, cfg)

        // ===== 实验性功能 =====
        if (cfg.autoDownloadEnabled) AutoDownloadHook.apply(lpparam, cfg)
        if (cfg.removeAdsEnabled) RemoveVideoAdsHook.apply(lpparam, cfg)
        if (cfg.saveOriginalQualityEnabled) SaveOriginalQualityHook.apply(lpparam, cfg)
        if (cfg.batchDownloadEnabled) BatchDownloadHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            AntiDetectionHelper.sleepDuringVerify()
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
        }
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.ss.android.ugc.aweme",       // 抖音
        "com.ss.android.ugc.aweme.lite",  // 抖音极速版
        "com.smile.gifmaker",             // 快手
        "com.kuaishou.nebula",            // 快手极速版
        "com.xingin.xhs",                 // 小红书
        "com.xingin.xhscircle",           // 小红书圈子
        "tv.danmaku.bili",                // B站
        "com.tencent.qqlive",             // 腾讯视频
        "com.ss.android.article.video",   // 西瓜视频
        "com.hihonor.cloudmusic"          // 华为音乐
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): VideoConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { VideoConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        EnvDetector.detect(lpparam)
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
