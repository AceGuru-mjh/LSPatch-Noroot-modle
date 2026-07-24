package com.vipunlock.noroot

import android.util.Log
import com.vipunlock.noroot.models.VipConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.15"
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
            tryInvokeVoid("com.vipunlock.noroot.utils.CrashGuard", "init", null)

            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            tryInvoke("com.vipunlock.noroot.utils.EnvDetector", "detect", lpparam)

            val ctx = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? android.content.Context
            } catch (_: Throwable) { null }

            val masterSwitch = if (ctx != null) tryInvokeCtx<Boolean>("com.vipunlock.noroot.core.ConfigClient", "readMasterSwitch", ctx) ?: true else true
            if (!masterSwitch) {
                Log.e(TAG, "Master switch OFF via ContentProvider, skipping hooks")
                return
            }

            val cfg = loadConfig()
            cfg.packageName = pkg

            if (cfg.netEaseVipEnabled && pkg == "com.netease.cloudmusic") tryInvokeHook("com.vipunlock.noroot.hooks.NetEaseMusicVipHook", lpparam, cfg)
            if (cfg.qqMusicVipEnabled && pkg == "com.tencent.wmusic") tryInvokeHook("com.vipunlock.noroot.hooks.QQMusicVipHook", lpparam, cfg)
            if (cfg.kugouVipEnabled && pkg == "com.kugou.android") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.kuwoVipEnabled && pkg == "com.kuwo.player") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.iqiyiVipEnabled && pkg == "com.qiyi.video") tryInvokeHook("com.vipunlock.noroot.hooks.IqiyiVipHook", lpparam, cfg)
            if (cfg.youkuVipEnabled && pkg == "com.youku.phone") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.tencentVideoVipEnabled && pkg == "com.tencent.qqlive") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.biliVipEnabled && pkg == "tv.danmaku.bili") tryInvokeHook("com.vipunlock.noroot.hooks.BilibiliVipHook", lpparam, cfg)
            if (cfg.ximalayaVipEnabled && pkg == "com.ximalaya.ting.android") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.toutiaoVipEnabled && pkg == "com.ss.android.article.news") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.zhihuVipEnabled && pkg == "com.zhihu.android") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.baiduNetdiskVipEnabled && pkg == "com.baidu.netdisk") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.wpsVipEnabled && pkg == "com.wps.moffice_eng") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.wereadVipEnabled && pkg == "com.tencent.weread") tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.universalVipTryEnabled) tryInvokeHook("com.vipunlock.noroot.hooks.UniversalVipHook", lpparam, cfg)
            if (cfg.removeAdsEnabled) tryInvokeHook("com.vipunlock.noroot.hooks.RemoveAdsHook", lpparam, cfg)
            if (cfg.bypassVerifyEnabled) tryInvokeHook("com.vipunlock.noroot.hooks.BypassVerifyHook", lpparam, cfg)
            if (cfg.shizukuVipDbEnabled) tryInvokeHook("com.vipunlock.noroot.hooks.ShizukuVipDbHook", lpparam, cfg)

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            tryInvokeVoid("com.vipunlock.noroot.utils.CrashGuard", "log", "FATAL: ${e.stackTraceToString()}")
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
        try {
            val reader = Class.forName("com.vipunlock.noroot.utils.HookConfigReader")
            val result = reader.getDeclaredMethod("readGlobal").invoke(null) as? VipConfig
            if (result != null) return result
        } catch (_: Throwable) { }
        return try {
            val mgr = Class.forName("com.vipunlock.noroot.utils.ConfigManager")
            mgr.getDeclaredMethod("getGlobalConfig").invoke(null) as VipConfig
        } catch (_: Throwable) { VipConfig(packageName = "global") }
    }

    private fun tryInvoke(className: String, methodName: String, vararg args: Any?) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == args.size }
            method?.invoke(null, *args)
        } catch (e: Throwable) {
            Log.e(TAG, "${className.substringAfterLast('.')}#$methodName FAIL: ${e.message}")
        }
    }

    private fun tryInvokeVoid(className: String, methodName: String, arg: Any?) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            method?.invoke(null, arg)
        } catch (_: Throwable) { }
    }

    private fun <T> tryInvokeCtx(className: String, methodName: String, ctx: android.content.Context): T? {
        return try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
            method?.invoke(null, ctx) as? T
        } catch (_: Throwable) { null }
    }

    private fun tryInvokeHook(className: String, lpparam: XC_LoadPackage.LoadPackageParam, cfg: Any?) {
        try {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.firstOrNull { it.parameterCount == 2 }
            method?.invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "${className.substringAfterLast('.')} FAIL: ${e.message}")
        }
    }
}
