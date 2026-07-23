package com.vipunlock.noroot

import android.util.Log
import com.vipunlock.noroot.core.ConfigClient
import com.vipunlock.noroot.hooks.*
import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.CrashGuard
import com.vipunlock.noroot.utils.EnvDetector
import com.vipunlock.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        const val TAG = "LSP-VipUnlocker"
        const val MODULE_PKG = "com.vipunlock.noroot"
        var currentPkg: String? = null
        var isIntegratedMode: Boolean = false
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        isIntegratedMode = try {
            Class.forName("org.lsposed.lspatch.LSPatch")
            false
        } catch (_: Throwable) {
            true
        }

        if (isIntegratedMode) {
            Log.e(TAG, "Integrated mode: UI stripped, hooks only")
        } else {
            Log.e(TAG, "VipUnlocker NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.vipunlock.noroot.ui.UiInitializer")
                    .getDeclaredMethod("initAllUi", android.content.Context::class.java)
                    .invoke(null, Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null))
            } catch (t: Throwable) {
                Log.e(TAG, "UI init failed: ${t.message}")
            }
            return
        }

        if (lpparam.processName != lpparam.packageName) return

        try {
            try { CrashGuard.init(null) } catch (_: Throwable) { }
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            EnvDetector.detect(lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) ConfigClient.readMasterSwitch(ctx) else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            cfg.packageName = pkg

            Log.e(TAG, "Loading NetEaseMusicVipHook...")
            try { if (cfg.netEaseVipEnabled && pkg == "com.netease.cloudmusic") NetEaseMusicVipHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "NetEaseMusicVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading QQMusicVipHook...")
            try { if (cfg.qqMusicVipEnabled && pkg == "com.tencent.wmusic") QQMusicVipHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "QQMusicVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading KugouVipHook...")
            try { if (cfg.kugouVipEnabled && pkg == "com.kugou.android") UniversalVipHook.applyForKugou(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "KugouVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading KuwoVipHook...")
            try { if (cfg.kuwoVipEnabled && pkg == "com.kuwo.player") UniversalVipHook.applyForKuwo(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "KuwoVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading IqiyiVipHook...")
            try { if (cfg.iqiyiVipEnabled && pkg == "com.qiyi.video") IqiyiVipHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "IqiyiVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading YoukuVipHook...")
            try { if (cfg.youkuVipEnabled && pkg == "com.youku.phone") UniversalVipHook.applyForYouku(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "YoukuVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading TencentVideoVipHook...")
            try { if (cfg.tencentVideoVipEnabled && pkg == "com.tencent.qqlive") UniversalVipHook.applyForTencentVideo(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "TencentVideoVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BilibiliVipHook...")
            try { if (cfg.biliVipEnabled && pkg == "tv.danmaku.bili") BilibiliVipHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BilibiliVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading XimalayaVipHook...")
            try { if (cfg.ximalayaVipEnabled && pkg == "com.ximalaya.ting.android") UniversalVipHook.applyForXimalaya(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "XimalayaVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ToutiaoVipHook...")
            try { if (cfg.toutiaoVipEnabled && pkg == "com.ss.android.article.news") UniversalVipHook.applyForToutiao(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ToutiaoVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ZhihuVipHook...")
            try { if (cfg.zhihuVipEnabled && pkg == "com.zhihu.android") UniversalVipHook.applyForZhihu(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ZhihuVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BaiduNetdiskHook...")
            try { if (cfg.baiduNetdiskVipEnabled && pkg == "com.baidu.netdisk") UniversalVipHook.applyForBaiduNetdisk(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BaiduNetdiskHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading WpsVipHook...")
            try { if (cfg.wpsVipEnabled && pkg == "com.wps.moffice_eng") UniversalVipHook.applyForWps(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "WpsVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading WereadVipHook...")
            try { if (cfg.wereadVipEnabled && pkg == "com.tencent.weread") UniversalVipHook.applyForWeread(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "WereadVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading UniversalVipHook...")
            try { if (cfg.universalVipTryEnabled) UniversalVipHook.applyForCommon(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "UniversalVipHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading RemoveAdsHook...")
            try { if (cfg.removeAdsEnabled) RemoveAdsHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "RemoveAdsHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BypassVerifyHook...")
            try { if (cfg.bypassVerifyEnabled) BypassVerifyHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BypassVerifyHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ShizukuVipDbHook...")
            try { if (cfg.shizukuVipDbEnabled) ShizukuVipDbHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuVipDbHook FAIL: ${e.message}") }

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            CrashGuard.log("FATAL: ${e.stackTraceToString()}")
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android", "com.kuwo.player",
        "com.qiyi.video", "com.youku.phone", "com.tencent.qqlive", "tv.danmaku.bili",
        "com.ximalaya.ting.android", "com.ss.android.article.news", "com.zhihu.android",
        "com.baidu.netdisk", "com.wps.moffice_eng", "com.tencent.weread",
        "com.sdu.didi.psnger", "com.eg.android.AlipayGphone"
    )

    private fun loadConfig(): VipConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.vipunlock.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { VipConfig(packageName = "global") }
    }
}
