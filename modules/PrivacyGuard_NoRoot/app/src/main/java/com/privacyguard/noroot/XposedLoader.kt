package com.privacyguard.noroot

import android.app.Application
import com.privacyguard.noroot.hooks.*
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.ConfigManager
import com.privacyguard.noroot.utils.HookConfigReader
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * PrivacyGuard NoRoot - Xposed 模块唯一入口
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
 *    [1] 设备ID伪造  [2] 剪贴板保护  [3] 权限欺骗
 *    [4] 位置伪造    [5] 传感器伪造  [6] 广告ID屏蔽
 *    [实验] 包可见性/网络信息/屏幕参数/存储路径
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅应用进程内 Java 层 Hook
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不调用 Shizuku 做真Root操作
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.10"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("PrivacyGuard NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
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
        LogX.i("配置: 总开关=${cfg.masterEnabled} 设备ID=${cfg.deviceIdSpoofEnabled} " +
                "剪贴板=${cfg.clipboardGuardEnabled} 位置=${cfg.locationSpoofEnabled} " +
                "[实验]包可见=${cfg.packageVisibilitySpoofEnabled} 网络=${cfg.networkInfoSpoofEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能 =====
        if (cfg.deviceIdSpoofEnabled) DeviceIdSpoofHook.apply(lpparam, cfg)
        if (cfg.clipboardGuardEnabled) ClipboardGuardHook.apply(lpparam, cfg)
        if (cfg.permissionSpoofEnabled) PermissionSpoofHook.apply(lpparam, cfg)
        if (cfg.locationSpoofEnabled) LocationSpoofHook.apply(lpparam, cfg)
        if (cfg.sensorFakerEnabled) SensorFakerHook.apply(lpparam, cfg)
        if (cfg.advertisingIdBlockEnabled) AdvertisingIdHook.apply(lpparam, cfg)

        // ===== 实验性功能 =====
        if (cfg.packageVisibilitySpoofEnabled) PackageVisibilitySpoofHook.apply(lpparam, cfg)
        if (cfg.networkInfoSpoofEnabled) NetworkInfoSpoofHook.apply(lpparam, cfg)
        if (cfg.screenMetricsSpoofEnabled) ScreenMetricsSpoofHook.apply(lpparam, cfg)
        if (cfg.storagePathSpoofEnabled) StoragePathSpoofHook.apply(lpparam, cfg)

        // ===== v1.0.6 新增（对标 HideMyAndroid） =====
        if (cfg.installStatusSpoofEnabled || cfg.mockLocationSystemLevelEnabled) {
            PrivacyPlusHook.apply(lpparam, cfg)
        }

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq", "com.tencent.mobileqqi",
        "com.android.settings", "com.android.chrome", "com.mi.globalbrowser",
        "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.xunmeng.pinduoduo",
        "com.jingdong.app.mall", "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.sina.weibo"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): PrivacyConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { PrivacyConfig(packageName = "global") }
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
