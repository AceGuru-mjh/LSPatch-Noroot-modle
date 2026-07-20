package com.vipunlock.noroot

import android.app.Application
import com.vipunlock.noroot.hooks.*
import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.ConfigManager
import com.vipunlock.noroot.utils.HookConfigReader
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * VipUnlocker NoRoot - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    LSPatch 合规过滤（跳过 android / 仅主进程） ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 网易云VIP   [2] QQ音乐VIP   [3] 酷狗VIP   [4] 酷我VIP
 *    [5] 爱奇艺VIP   [6] 优酷VIP     [7] 腾讯视频VIP [8] B站大会员
 *    [9] 喜马拉雅VIP [10] 今日头条   [11] 知乎盐选   [12] 百度网盘SVIP
 *    [13] WPS超级会员 [14] 微信读书   [实验] 通用VIP/去广告/绕过校验
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅 Hook 应用进程内 VIP 状态查询方法，不修改系统
 *  - 不调用 su / setenforce / 写 /system /sys
 *  - 不调用 Shizuku 做真Root操作
 *  - 解锁效果依赖 APP 把 VIP 状态缓存在本地，强制停止后可能失效
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.10"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("VipUnlocker NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
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
        LogX.i("配置: 总开关=${cfg.masterEnabled} 网易云=${cfg.netEaseVipEnabled} QQ音乐=${cfg.qqMusicVipEnabled} " +
                "爱奇艺=${cfg.iqiyiVipEnabled} B站=${cfg.biliVipEnabled} 知乎=${cfg.zhihuVipEnabled} " +
                "[实验]通用VIP=${cfg.universalVipTryEnabled} 去广告=${cfg.removeAdsEnabled} 绕过校验=${cfg.bypassVerifyEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 音乐类 VIP =====
        if (cfg.netEaseVipEnabled && pkg == "com.netease.cloudmusic") NetEaseMusicVipHook.apply(lpparam, cfg)
        if (cfg.qqMusicVipEnabled && pkg == "com.tencent.wmusic") QQMusicVipHook.apply(lpparam, cfg)
        if (cfg.kugouVipEnabled && pkg == "com.kugou.android") UniversalVipHook.applyForKugou(lpparam, cfg)
        if (cfg.kuwoVipEnabled && pkg == "com.kuwo.player") UniversalVipHook.applyForKuwo(lpparam, cfg)

        // ===== 视频类 VIP =====
        if (cfg.iqiyiVipEnabled && pkg == "com.qiyi.video") IqiyiVipHook.apply(lpparam, cfg)
        if (cfg.youkuVipEnabled && pkg == "com.youku.phone") UniversalVipHook.applyForYouku(lpparam, cfg)
        if (cfg.tencentVideoVipEnabled && pkg == "com.tencent.qqlive") UniversalVipHook.applyForTencentVideo(lpparam, cfg)
        if (cfg.biliVipEnabled && pkg == "tv.danmaku.bili") BilibiliVipHook.apply(lpparam, cfg)

        // ===== 阅读/资讯类 VIP =====
        if (cfg.ximalayaVipEnabled && pkg == "com.ximalaya.ting.android") UniversalVipHook.applyForXimalaya(lpparam, cfg)
        if (cfg.toutiaoVipEnabled && pkg == "com.ss.android.article.news") UniversalVipHook.applyForToutiao(lpparam, cfg)
        if (cfg.zhihuVipEnabled && pkg == "com.zhihu.android") UniversalVipHook.applyForZhihu(lpparam, cfg)

        // ===== 工具类 VIP =====
        if (cfg.baiduNetdiskVipEnabled && pkg == "com.baidu.netdisk") UniversalVipHook.applyForBaiduNetdisk(lpparam, cfg)
        if (cfg.wpsVipEnabled && pkg == "com.wps.moffice_eng") UniversalVipHook.applyForWps(lpparam, cfg)
        if (cfg.wereadVipEnabled && pkg == "com.tencent.weread") UniversalVipHook.applyForWeread(lpparam, cfg)

        // ===== 实验性（跨APP通用） =====
        if (cfg.universalVipTryEnabled) UniversalVipHook.applyForCommon(lpparam, cfg)
        if (cfg.removeAdsEnabled) RemoveAdsHook.apply(lpparam, cfg)
        if (cfg.bypassVerifyEnabled) BypassVerifyHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单（与 arrays.xml xposed_scope 一致） */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        // 音乐类
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android", "com.kuwo.player",
        // 视频类
        "com.qiyi.video", "com.youku.phone", "com.tencent.qqlive", "tv.danmaku.bili",
        // 阅读/资讯类
        "com.ximalaya.ting.android", "com.ss.android.article.news", "com.zhihu.android",
        // 工具类
        "com.baidu.netdisk", "com.wps.moffice_eng", "com.tencent.weread",
        // 出行/支付类
        "com.sdu.didi.psnger", "com.eg.android.AlipayGphone"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): VipConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { VipConfig(packageName = "global") }
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
