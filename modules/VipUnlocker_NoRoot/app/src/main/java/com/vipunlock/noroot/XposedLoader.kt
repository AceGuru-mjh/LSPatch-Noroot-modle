package com.vipunlock.noroot

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.vipunlock.noroot.models.VipConfig

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
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
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            try {
                Class.forName("com.vipunlock.noroot.utils.EnvDetector")
                    .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                    .invoke(null, lpparam)
            } catch (_: Throwable) { }

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) {
                try { Class.forName("com.vipunlock.noroot.core.ConfigClient").getDeclaredMethod("readMasterSwitch", android.content.Context::class.java).invoke(null, ctx) as? Boolean ?: true } catch (_: Throwable) { true }
            } else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            cfg.packageName = pkg
            val loader = lpparam.classLoader

            if (cfg.netEaseVipEnabled && pkg == "com.netease.cloudmusic") tryInvoke("com.vipunlock.noroot.hooks.NetEaseMusicVipHook", "apply", loader, lpparam, cfg)
            if (cfg.qqMusicVipEnabled && pkg == "com.tencent.wmusic") tryInvoke("com.vipunlock.noroot.hooks.QQMusicVipHook", "apply", loader, lpparam, cfg)
            if (cfg.kugouVipEnabled && pkg == "com.kugou.android") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForKugou", loader, lpparam, cfg)
            if (cfg.kuwoVipEnabled && pkg == "com.kuwo.player") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForKuwo", loader, lpparam, cfg)
            if (cfg.iqiyiVipEnabled && pkg == "com.qiyi.video") tryInvoke("com.vipunlock.noroot.hooks.IqiyiVipHook", "apply", loader, lpparam, cfg)
            if (cfg.youkuVipEnabled && pkg == "com.youku.phone") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForYouku", loader, lpparam, cfg)
            if (cfg.tencentVideoVipEnabled && pkg == "com.tencent.qqlive") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForTencentVideo", loader, lpparam, cfg)
            if (cfg.biliVipEnabled && pkg == "tv.danmaku.bili") tryInvoke("com.vipunlock.noroot.hooks.BilibiliVipHook", "apply", loader, lpparam, cfg)
            if (cfg.ximalayaVipEnabled && pkg == "com.ximalaya.ting.android") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForXimalaya", loader, lpparam, cfg)
            if (cfg.toutiaoVipEnabled && pkg == "com.ss.android.article.news") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForToutiao", loader, lpparam, cfg)
            if (cfg.zhihuVipEnabled && pkg == "com.zhihu.android") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForZhihu", loader, lpparam, cfg)
            if (cfg.baiduNetdiskVipEnabled && pkg == "com.baidu.netdisk") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForBaiduNetdisk", loader, lpparam, cfg)
            if (cfg.wpsVipEnabled && pkg == "com.wps.moffice_eng") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForWps", loader, lpparam, cfg)
            if (cfg.wereadVipEnabled && pkg == "com.tencent.weread") tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForWeread", loader, lpparam, cfg)
            if (cfg.universalVipTryEnabled) tryInvoke("com.vipunlock.noroot.hooks.UniversalVipHook", "applyForCommon", loader, lpparam, cfg)
            if (cfg.removeAdsEnabled) tryInvoke("com.vipunlock.noroot.hooks.RemoveAdsHook", "apply", loader, lpparam, cfg)
            if (cfg.bypassVerifyEnabled) tryInvoke("com.vipunlock.noroot.hooks.BypassVerifyHook", "apply", loader, lpparam, cfg)
            if (cfg.shizukuVipDbEnabled) tryInvoke("com.vipunlock.noroot.hooks.ShizukuVipDbHook", "apply", loader, lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
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

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, VipConfig::class.java)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    private fun loadConfig(): VipConfig {
        try {
            val reader = Class.forName("com.vipunlock.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? VipConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        try {
            val mgr = Class.forName("com.vipunlock.noroot.utils.ConfigManager")
            return mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as? VipConfig
                ?: VipConfig()
        } catch (_: Throwable) {
            return VipConfig()
        }
    }
}
