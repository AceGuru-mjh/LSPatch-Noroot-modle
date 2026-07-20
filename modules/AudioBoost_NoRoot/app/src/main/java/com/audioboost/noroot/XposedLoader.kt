package com.audioboost.noroot

import android.app.Application
import com.audioboost.noroot.hooks.*
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.ConfigManager
import com.audioboost.noroot.utils.HookConfigReader
import com.audioboost.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AudioBoost NoRoot - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标音乐/音频APP ->
 *    读取全局配置 ->
 *    [1] 音量增强(AudioTrack/MediaPlayer)  [2] 低音增强(BassBoost)
 *    [3] 均衡器(Equalizer)                  [实验]
 *    [实验] 扬声器增强 / 麦克风增益 / 音质增强
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅 Hook 应用进程内音频 API，不修改系统音量服务
 *  - 不调用 Shizuku 设置系统音量（setStreamVolume 系统级）
 *  - 不写 /sys/class/audio 等系统节点
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.10"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("AudioBoost NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
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
        LogX.i("配置: 总开关=${cfg.masterEnabled} 音量=${cfg.volumeBoostEnabled} " +
                "低音=${cfg.bassBoostEnabled} 均衡器=${cfg.equalizerEnabled} " +
                "[实验]扬声器=${cfg.speakerBoostEnabled} 麦克风=${cfg.micBoostEnabled} 音质=${cfg.audioQualityEnhanceEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能 =====
        if (cfg.volumeBoostEnabled) VolumeBoostHook.apply(lpparam, cfg)
        if (cfg.bassBoostEnabled) BassBoostHook.apply(lpparam, cfg)
        if (cfg.equalizerEnabled) EqualizerHook.apply(lpparam, cfg)

        // ===== 实验性功能 =====
        if (cfg.speakerBoostEnabled) SpeakerBoostHook.apply(lpparam, cfg)
        if (cfg.micBoostEnabled) MicBoostHook.apply(lpparam, cfg)
        if (cfg.audioQualityEnhanceEnabled) AudioQualityEnhanceHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单（音乐/语音/短视频类） */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android",
        "com.kuwo.player", "com.netease.cloudmusic.player", "com.spotify.music",
        "com.google.android.apps.youtube.music", "com.tencent.mm",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.miui.player", "com.hihonor.cloudmusic"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): AudioConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { AudioConfig(packageName = "global") }
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
