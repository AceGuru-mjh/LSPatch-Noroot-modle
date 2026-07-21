package com.mjh.shizukufix

import android.app.Application
import com.mjh.shizukufix.hooks.AutoGrantHelperHook
import com.mjh.shizukufix.hooks.HideFromSceneHook
import com.mjh.shizukufix.hooks.ScenePermissionRequesterHook
import com.mjh.shizukufix.hooks.ServiceWatchdogHook
import com.mjh.shizukufix.hooks.ShizukuGrantHook
import com.mjh.shizukufix.hooks.ShizukuListInjectorHook
import com.mjh.shizukufix.hooks.ShizukuVariantDetectorHook
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.AntiDetectionHelper
import com.mjh.shizukufix.utils.ConfigManager
import com.mjh.shizukufix.utils.EnvDetector
import com.mjh.shizukufix.utils.HookConfigReader
import com.mjh.shizukufix.utils.LogStore
import com.mjh.shizukufix.utils.LogX
import com.mjh.shizukufix.utils.ModuleConflictDetector
import com.mjh.shizukufix.utils.PackageHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku Scene Fix - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程（双路径）：
 *  - Path A（Scene 进程 com.omarea.vtools）：
 *      [1] ScenePermissionRequesterHook 主动向 Shizuku 申请权限
 *      [实验] HideFromSceneHook 隐藏模块自身存在
 *
 *  - Path B（Shizuku 进程 moe.shizuku.privileged.api / rikka.shizuku.manager / 变体）：
 *      [2] ShizukuListInjectorHook 向 Shizuku 授权列表注入 Scene
 *      [3] ShizukuVariantDetectorHook 检测 Shizuku 变体包名
 *      [实验] ServiceWatchdogHook 服务保活
 *      [实验] AutoGrantHelperHook 自动授权辅助
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"

        /** Scene 主包名 */
        const val SCENE_PACKAGE = "com.omarea.vtools"

        /** 默认 Shizuku 包名集合 */
        val DEFAULT_SHIZUKU_PACKAGES = setOf(
            "moe.shizuku.privileged.api",
            "rikka.shizuku.manager"
        )

        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("ShizukuSceneFix v$VERSION 初始化 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        val pkg = lpparam.packageName ?: return
        val proc = lpparam.processName ?: pkg
        LogX.i(">>> Module loaded in process: $proc (package: $pkg)")

        currentPkg = pkg
        LogX.i("=== ShizukuSceneFix v$VERSION starting | pkg=$pkg | process=$proc | mode=${if (EnvDetector.isLocalMode) "local" else "integrated"} ===")

        try {
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

            if (!cfg.masterEnabled) {
                LogX.i("总开关关闭，跳过所有 Hook")
                return
            }

            LogX.i("配置: 总开关=${cfg.masterEnabled} Scene修复=${cfg.sceneFixEnabled} " +
                    "列表注入=${cfg.listInjectorEnabled} 变体检测=${cfg.variantDetectEnabled} " +
                    "pmGrant=${cfg.pmGrantEnabled} " +
                    "[实验]保活=${cfg.serviceWatchdogEnabled} 自动授权=${cfg.autoGrantHelperEnabled} " +
                    "隐藏自身=${cfg.hideFromSceneEnabled}")

            // ===== Path A: Scene 进程 =====
            if (pkg == SCENE_PACKAGE) {
                LogX.i("=== Path A: Hooking Scene process ===")
                if (cfg.sceneFixEnabled) ScenePermissionRequesterHook.apply(lpparam, cfg)
                if (cfg.pmGrantEnabled) ShizukuGrantHook.apply(lpparam, cfg)
                if (cfg.hideFromSceneEnabled) HideFromSceneHook.apply(lpparam, cfg)
                hookAppLifecycle(lpparam)
                return
            }

            // ===== Path B: Shizuku 进程 =====
            if (isShizukuTarget(pkg)) {
                LogX.i("=== Path B: Hooking Shizuku process: $proc ===")
                if (cfg.variantDetectEnabled) ShizukuVariantDetectorHook.apply(lpparam, cfg)
                if (cfg.listInjectorEnabled) ShizukuListInjectorHook.apply(lpparam, cfg)
                if (cfg.serviceWatchdogEnabled) ServiceWatchdogHook.apply(lpparam, cfg)
                if (cfg.autoGrantHelperEnabled) AutoGrantHelperHook.apply(lpparam, cfg)
                hookAppLifecycle(lpparam)
                return
            }

            // ===== 晚期检测：在 Application.onCreate 后再次确认是否为 Shizuku 变体 =====
            tryLateDetection(lpparam, cfg)

        } catch (e: Throwable) {
            AntiDetectionHelper.sleepDuringVerify()
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
        }
    }

    /** 判断是否为 Shizuku 目标（默认包名 + 变体检测） */
    private fun isShizukuTarget(pkg: String): Boolean {
        if (pkg in DEFAULT_SHIZUKU_PACKAGES) return true
        return ShizukuVariantDetectorHook.isShizukuProcess(pkg)
    }

    /** 读取配置：优先 XSharedPreferences，回退 Context */
    private fun loadConfig(): ShizukuFixConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) {
            ShizukuFixConfig(packageName = "global")
        }
    }

    /** 通过 ActivityThread 拿 Application 初始化 ConfigManager */
    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        EnvDetector.detect(lpparam)
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) { ConfigManager.init(app); LogStore.init(app) }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook Application.onCreate 在 Application 重建时补初始化 ConfigManager */
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

    /** 晚期变体检测：基于 Context 重新判定 */
    private fun tryLateDetection(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val ctx = p.thisObject as? Application ?: return
                            val currentPkg = ctx.packageName ?: return
                            if (currentPkg != SCENE_PACKAGE &&
                                currentPkg !in DEFAULT_SHIZUKU_PACKAGES &&
                                ShizukuVariantDetectorHook.isShizukuProcess(currentPkg)) {
                                LogX.i("Late-detected Shizuku variant: $currentPkg")
                                if (cfg.variantDetectEnabled) ShizukuVariantDetectorHook.apply(lpparam, cfg)
                                if (cfg.listInjectorEnabled) ShizukuListInjectorHook.apply(lpparam, cfg)
                                if (cfg.serviceWatchdogEnabled) ServiceWatchdogHook.apply(lpparam, cfg)
                                if (cfg.autoGrantHelperEnabled) AutoGrantHelperHook.apply(lpparam, cfg)
                            }
                            ConfigManager.init(ctx)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
