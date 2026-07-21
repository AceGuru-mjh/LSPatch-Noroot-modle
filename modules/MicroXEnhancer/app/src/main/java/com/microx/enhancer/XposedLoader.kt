package com.microx.enhancer

import android.app.Application
import com.microx.enhancer.hooks.*
import com.microx.enhancer.models.MicroXConfig
import com.microx.enhancer.utils.AntiDetectionHelper
import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.EnvDetector
import com.microx.enhancer.utils.HookConfigReader
import com.microx.enhancer.utils.HookHelper
import com.microx.enhancer.utils.LogStore
import com.microx.enhancer.utils.LogX
import com.microx.enhancer.utils.ModuleConflictDetector
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * MicroX Enhancer - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断包名 + 主进程 ->
 *    读取全局配置 ->
 *    [微信] AdBlock / AntiRecall / Moment / UIMod / Privacy / BatchManager / AutoReply
 *          [实验] VoiceMessageExport / MessageSearchEnhance / CustomTheme
 *    [QQ]   AdBlock / AntiRecall / UIMod / Privacy / AutoReply
 *
 * 主进程判断（迁移自旧 MainHook）：
 *  - 微信主进程 = com.tencent.mm / com.tencent.mm:tools
 *  - QQ 主进程 = com.tencent.mobileqq
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("MicroX Enhancer v$VERSION 初始化 | LSPatch/LSPosed 兼容")
        try { HookHelper.log("模块路径: ${param.modulePath}") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            val processName = lpparam.processName ?: return

            when (pkg) {
                "com.tencent.mm" -> {
                    if (HookHelper.isWeChatMainProcess(processName)) onWeChatLoaded(lpparam)
                }
                "com.tencent.mobileqq" -> {
                    if (HookHelper.isQQMainProcess(processName)) onQQLoaded(lpparam)
                }
            }
        } catch (e: Throwable) {
            AntiDetectionHelper.sleepDuringVerify()
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
        }
    }

    /** 微信进程 Hook 入口 */
    private fun onWeChatLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        LogX.i("=== MicroXEnhancer v$VERSION starting | pkg=${lpparam.packageName} | process=${lpparam.processName} | mode=${if (EnvDetector.isLocalMode) "local" else "integrated"} ===")
        LogX.i("进程名: ${lpparam.processName}")
        LogX.i("环境: ${if (EnvDetector.isLocalMode) "LSPatch本地" else "LSPosed集成"}模式")
        if (ModuleConflictDetector.checkConflict()) {
            LogX.w("检测到模块冲突，跳过Hook")
            return
        }
        currentPkg = "com.tencent.mm"

        initConfig(lpparam)
        if (!EnvDetector.isLocalMode) {
            try { Thread.sleep(100) } catch (_: Throwable) { }
        }

        // 1. 安全适配必须最先执行
        if (ConfigManager.isEnabled(ConfigManager.KEY_BYPASS_DETECTION)) {
            try { SecurityBypassHook.hook(lpparam) } catch (e: Throwable) { LogX.e("SecurityBypass 异常", e) }
        }

        // 2. 加载所有功能模块
        val modules = listOf(
            "广告净化" to { AdBlockHook.hook(lpparam) },
            "消息防撤回" to { AntiRecallHook.hook(lpparam) },
            "防删朋友圈" to { MomentHook.hook(lpparam) },
            "界面美化" to { UIModHook.hook(lpparam) },
            "隐私增强" to { PrivacyHook.hook(lpparam) },
            "批量管理" to { BatchManagerHook.hook(lpparam) },
            "自动回复" to { AutoReplyHook.hook(lpparam) },
            // Shizuku 系统级（adb级 sqlite3 数据库直读）
            "Shizuku数据库直读" to {
                if (ConfigManager.isEnabled("shizuku_db_access")) {
                    ShizukuDbHook.apply(lpparam, MicroXConfig().also {
                        it.shizukuDbAccessEnabled = ConfigManager.isEnabled("shizuku_db_access")
                        it.wechatDbPath = ConfigManager.getString("wechat_db_path", "")
                    })
                }
            },
            // 实验性
            "语音消息导出" to { VoiceMessageExportHook.hook(lpparam) },
            "消息搜索增强" to { MessageSearchEnhanceHook.hook(lpparam) },
            "自定义主题" to { CustomThemeHook.hook(lpparam) },
            // v1.0.6 新增（从 ConfigManager 读取开关）
            "自动红包转账" to {
                if (ConfigManager.isEnabled("auto_red_packet") || ConfigManager.isEnabled("auto_transfer")) {
                    AutoRedPacketHook.apply(lpparam, MicroXConfig().also {
                        it.autoRedPacketEnabled = ConfigManager.isEnabled("auto_red_packet")
                        it.autoTransferEnabled = ConfigManager.isEnabled("auto_transfer")
                    })
                }
            },
            "步数修改/禁热更新/伪集赞/转发解除/自动原图" to {
                MicroXPlusHook.apply(lpparam, MicroXConfig().also {
                    it.stepModifierEnabled = ConfigManager.isEnabled("step_modifier")
                    it.disableHotUpdateEnabled = ConfigManager.isEnabled("disable_hot_update")
                    it.momentFakeLikeEnabled = ConfigManager.isEnabled("moment_fake_like")
                    it.unlimitedForwardEnabled = ConfigManager.isEnabled("unlimited_forward")
                    it.autoOriginalImageEnabled = ConfigManager.isEnabled("auto_original_image")
                })
            }
        )

        for ((name, hookAction) in modules) {
            try {
                hookAction.invoke()
                LogX.d("[$name] Hook加载完成")
            } catch (e: Throwable) {
                LogX.e("[$name] Hook加载失败: ${e.message}", e)
            }
        }

        hookAppLifecycle(lpparam)
        LogX.i("===== 微X增强模块微信注入完成 =====")
    }

    /** QQ进程 Hook 入口 */
    private fun onQQLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        LogX.i("=== MicroXEnhancer v$VERSION starting | pkg=${lpparam.packageName} | process=${lpparam.processName} | mode=${if (EnvDetector.isLocalMode) "local" else "integrated"} ===")
        LogX.i("进程名: ${lpparam.processName}")
        LogX.i("环境: ${if (EnvDetector.isLocalMode) "LSPatch本地" else "LSPosed集成"}模式")
        if (ModuleConflictDetector.checkConflict()) {
            LogX.w("检测到模块冲突，跳过Hook")
            return
        }
        currentPkg = "com.tencent.mobileqq"

        initConfig(lpparam)
        if (!EnvDetector.isLocalMode) {
            try { Thread.sleep(100) } catch (_: Throwable) { }
        }

        if (ConfigManager.isEnabled(ConfigManager.KEY_BYPASS_DETECTION)) {
            try { SecurityBypassHook.hook(lpparam) } catch (e: Throwable) { LogX.e("SecurityBypass 异常", e) }
        }

        val modules = listOf(
            "广告净化" to { AdBlockHook.hookQQ(lpparam) },
            "消息防撤回" to { AntiRecallHook.hookQQ(lpparam) },
            "界面美化" to { UIModHook.hookQQ(lpparam) },
            "隐私增强" to { PrivacyHook.hookQQ(lpparam) },
            "自动回复" to { AutoReplyHook.hookQQ(lpparam) },
            // 实验性
            "自定义主题" to { CustomThemeHook.hookQQ(lpparam) }
        )

        for ((name, hookAction) in modules) {
            try {
                hookAction.invoke()
                LogX.d("[$name-QQ] Hook加载完成")
            } catch (e: Throwable) {
                LogX.e("[$name-QQ] Hook加载失败: ${e.message}", e)
            }
        }

        hookAppLifecycle(lpparam)
        LogX.i("===== 微X增强模块QQ注入完成 =====")
    }

    /** 优先使用 XSharedPreferences 预读取，再延迟到 Application.onCreate 初始化 ConfigManager */
    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        EnvDetector.detect(lpparam)
        // 预读：尝试通过 XSharedPreferences 读取，让 Hook 早期能用
        try { HookConfigReader.readGlobal() } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // 反射当前 Application，立即初始化 ConfigManager
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
