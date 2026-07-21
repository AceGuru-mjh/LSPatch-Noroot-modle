package com.adblockerx.noroot

import android.app.Application
import com.adblockerx.noroot.hooks.AdViewHideHook
import com.adblockerx.noroot.hooks.AdClosePlusHook
import com.adblockerx.noroot.hooks.CookieCleanHook
import com.adblockerx.noroot.hooks.HostsFilterHook
import com.adblockerx.noroot.hooks.IntentInterceptorHook
import com.adblockerx.noroot.hooks.OkHttpAdHook
import com.adblockerx.noroot.hooks.RedirectBlockHook
import com.adblockerx.noroot.hooks.ShizukuDnsHook
import com.adblockerx.noroot.hooks.TrackerBlockHook
import com.adblockerx.noroot.hooks.URLConnectionAdHook
import com.adblockerx.noroot.hooks.WebViewAdHook
import com.adblockerx.noroot.utils.ConfigManager
import com.adblockerx.noroot.utils.HookConfigReader
import com.adblockerx.noroot.utils.LogStore
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AdBlockerX NoRoot - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 内存 hosts 过滤器初始化（最先执行，供其他 Hook 查询）
 *    [2] WebView 广告拦截
 *    [3] OkHttp 广告拦截
 *    [4] URLConnection 广告拦截
 *    [5] 广告 SDK View 隐藏
 *    [实验] 追踪拦截 / Cookie 清理 / 重定向拦截 / Intent 拦截
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅拦截本 APP 进程内网络请求，绝不修改系统hosts/DNS
 *  - 不持久化 hosts 文件，所有数据存于内存
 *  - 广告 SDK 类名被混淆时自动跳过，绝不抛异常
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("AdBlockerX NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            LogX.i("===== APP启动: $pkg =====")
            currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        LogX.debugEnabled = cfg.logEnabled
        LogX.i("配置: 总开关=${cfg.masterEnabled} WebView=${cfg.webviewAdEnabled} OkHttp=${cfg.okHttpAdEnabled} " +
                "URLConnection=${cfg.urlConnectionAdEnabled} Hosts=${cfg.hostsFilterEnabled} " +
                "AdView=${cfg.adViewHideEnabled} DNS=${cfg.dnsAdBlockEnabled} " +
                "[实验]Tracker=${cfg.trackerBlockEnabled} " +
                "Cookie=${cfg.cookieCleanEnabled} Redirect=${cfg.redirectBlockEnabled} Intent=${cfg.intentInterceptorEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== [1] 内存 hosts 过滤器（最先初始化） =====
        if (cfg.hostsFilterEnabled) {
            HostsFilterHook.apply(lpparam, cfg)
        }

        // ===== [2]-[5] 应用层基础 Hook =====
        if (cfg.webviewAdEnabled) WebViewAdHook.apply(lpparam, cfg)
        if (cfg.okHttpAdEnabled) OkHttpAdHook.apply(lpparam, cfg)
        if (cfg.urlConnectionAdEnabled) URLConnectionAdHook.apply(lpparam, cfg)
        if (cfg.adViewHideEnabled) AdViewHideHook.apply(lpparam, cfg)

        // ===== 应用层实验性 =====
        if (cfg.trackerBlockEnabled) TrackerBlockHook.apply(lpparam, cfg)
        if (cfg.cookieCleanEnabled) CookieCleanHook.apply(lpparam, cfg)
        if (cfg.redirectBlockEnabled) RedirectBlockHook.apply(lpparam, cfg)
        if (cfg.intentInterceptorEnabled) IntentInterceptorHook.apply(lpparam, cfg)

        // ===== v1.0.6 新增（对标 AdClose） =====
        if (cfg.screenshotUnlockEnabled || cfg.shakeAdBlockEnabled || cfg.vpnDetectBypassEnabled) {
            AdClosePlusHook.apply(lpparam, cfg)
        }

        // ===== Shizuku 系统级（adb级 Private DNS） =====
        if (cfg.dnsAdBlockEnabled) ShizukuDnsHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
        }
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.android.chrome",
        "com.mi.globalbrowser",
        "com.huawei.browser",
        "com.sec.android.app.sbrowser",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone",
        "com.zhihu.android",
        "com.netease.cloudmusic",
        "com.tencent.wmusic"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): com.adblockerx.noroot.models.AdBlockConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { com.adblockerx.noroot.models.AdBlockConfig() }
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
