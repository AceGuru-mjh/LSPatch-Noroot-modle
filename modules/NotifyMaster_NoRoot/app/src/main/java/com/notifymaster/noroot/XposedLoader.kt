package com.notifymaster.noroot

import android.util.Log
import com.notifymaster.noroot.hooks.*
import com.notifymaster.noroot.models.NotifyConfig
import com.notifymaster.noroot.utils.EnvDetector
import com.notifymaster.noroot.utils.HookConfigReader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        const val TAG = "LSP-NotifyMaster"
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
            Log.e(TAG, "NotifyMaster NoRoot v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entered: pkg=${lpparam.packageName}")

        if (lpparam.processName != lpparam.packageName) return

        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            currentPkg = pkg
            Log.e(TAG, "Loading hooks for $pkg (integrated=${isIntegratedMode})")

            EnvDetector.detect(lpparam)

            val cfg = loadConfig()
            cfg.packageName = pkg
            if (!cfg.masterEnabled) {
                Log.e(TAG, "Master disabled, skipping hooks")
                return
            }

            Log.e(TAG, "Loading NotifyFilterHook...")
            try { if (cfg.notifyFilterEnabled) NotifyFilterHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "NotifyFilterHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading AntiRecallNotifyHook...")
            try { if (cfg.antiRecallNotifyEnabled) AntiRecallNotifyHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "AntiRecallNotifyHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading NotifyHistoryHook...")
            try { if (cfg.notifyHistoryEnabled) NotifyHistoryHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "NotifyHistoryHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading NotifyBeautifyHook...")
            try { if (cfg.notifyBeautifyEnabled) NotifyBeautifyHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "NotifyBeautifyHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading BatchNotifyHook...")
            try { if (cfg.batchNotifyEnabled) BatchNotifyHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "BatchNotifyHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading PriorityOverrideHook...")
            try { if (cfg.priorityOverrideEnabled) PriorityOverrideHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "PriorityOverrideHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading SilentNotifyHook...")
            try { if (cfg.silentNotifyEnabled) SilentNotifyHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "SilentNotifyHook FAIL: ${e.message}") }

            Log.e(TAG, "Loading ShizukuNotifyCmdHook...")
            try { if (cfg.shizukuNotifyCmdEnabled) ShizukuNotifyCmdHook.apply(lpparam, cfg) } catch (e: Throwable) { Log.e(TAG, "ShizukuNotifyCmdHook FAIL: ${e.message}") }

            Log.e(TAG, "===== All hooks loaded for $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.netease.cloudmusic", "com.tencent.wmusic",
        "com.sina.weibo", "com.zhihu.android",
        "com.baidu.searchbox", "com.ss.android.article.news"
    )

    private fun loadConfig(): NotifyConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { com.notifymaster.noroot.utils.ConfigManager.getGlobalConfig() } catch (_: Throwable) { NotifyConfig(packageName = "global") }
    }
}
