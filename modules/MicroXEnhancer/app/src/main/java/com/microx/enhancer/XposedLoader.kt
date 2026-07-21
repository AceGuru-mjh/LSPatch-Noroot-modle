package com.microx.enhancer

import android.util.Log
import com.microx.enhancer.core.ConfigClient
import com.microx.enhancer.hooks.*
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        const val TAG = "LSP-MicroX"
        const val MODULE_PKG = "com.microx.enhancer"
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
            Log.e(TAG, "MicroX Enhancer v$VERSION initZygote (local mode)")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "handleLoadPackage entry: pkg=${lpparam.packageName}")

        if (lpparam.packageName == MODULE_PKG) {
            Log.e(TAG, "Module own process: loading UI")
            try {
                Class.forName("com.microx.enhancer.ui.UiInitializer")
                    .getDeclaredMethod("initAllUi", android.content.Context::class.java)
                    .invoke(null, Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null))
            } catch (t: Throwable) {
                Log.e(TAG, "UI init failed: ${t.message}")
            }
            return
        }

        try {
            val pkg = lpparam.packageName ?: return
            val processName = lpparam.processName ?: return

            if (pkg == "android") return

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

            when (pkg) {
                "com.tencent.mm" -> {
                    if (HookHelper.isWeChatMainProcess(processName)) onWeChatLoaded(lpparam)
                }
                "com.tencent.mobileqq" -> {
                    if (HookHelper.isQQMainProcess(processName)) onQQLoaded(lpparam)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: ${e.message}", e)
        }
    }

    private fun onWeChatLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "Loading WeChat hooks (integrated=${isIntegratedMode})")
        currentPkg = "com.tencent.mm"

        val modules = listOf(
            "SecurityBypass" to { SecurityBypassHook.hook(lpparam) },
            "AdBlock" to { AdBlockHook.hook(lpparam) },
            "AntiRecall" to { AntiRecallHook.hook(lpparam) },
            "Moment" to { MomentHook.hook(lpparam) },
            "UIMod" to { UIModHook.hook(lpparam) },
            "Privacy" to { PrivacyHook.hook(lpparam) },
            "BatchManager" to { BatchManagerHook.hook(lpparam) },
            "AutoReply" to { AutoReplyHook.hook(lpparam) },
            "VoiceMessageExport" to { VoiceMessageExportHook.hook(lpparam) },
            "MessageSearchEnhance" to { MessageSearchEnhanceHook.hook(lpparam) },
            "CustomTheme" to { CustomThemeHook.hook(lpparam) }
        )

        for ((name, hookAction) in modules) {
            try {
                hookAction.invoke()
                Log.e(TAG, "[$name] OK")
            } catch (e: Throwable) {
                Log.e(TAG, "[$name] FAIL: ${e.message}")
            }
        }

        Log.e(TAG, "===== WeChat hooks loaded =====")
    }

    private fun onQQLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.e(TAG, "Loading QQ hooks (integrated=${isIntegratedMode})")
        currentPkg = "com.tencent.mobileqq"

        val modules = listOf(
            "SecurityBypass" to { SecurityBypassHook.hook(lpparam) },
            "AdBlock-QQ" to { AdBlockHook.hookQQ(lpparam) },
            "AntiRecall-QQ" to { AntiRecallHook.hookQQ(lpparam) },
            "UIMod-QQ" to { UIModHook.hookQQ(lpparam) },
            "Privacy-QQ" to { PrivacyHook.hookQQ(lpparam) },
            "AutoReply-QQ" to { AutoReplyHook.hookQQ(lpparam) },
            "CustomTheme-QQ" to { CustomThemeHook.hookQQ(lpparam) }
        )

        for ((name, hookAction) in modules) {
            try {
                hookAction.invoke()
                Log.e(TAG, "[$name] OK")
            } catch (e: Throwable) {
                Log.e(TAG, "[$name] FAIL: ${e.message}")
            }
        }

        Log.e(TAG, "===== QQ hooks loaded =====")
    }
}
