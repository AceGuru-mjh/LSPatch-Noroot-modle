package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogStore
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object CompetitionModeHook {

    private const val PREFS_NAME = "step_competition_sync"
    private var sharedSteps = 0

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.competitionModeEnabled) return

        LogX.i("竞赛模式 Hook 启动 | 同步间隔=${cfg.syncInterval}ms 主账号数=${cfg.leaderAccounts.size}")
        try { LogStore.add("competition", "竞赛模式已启用") } catch (_: Exception) { }

        hookSharedPreferencesRead(lpparam, cfg)
        hookContentResolverQuery(lpparam, cfg)
    }

    private fun hookSharedPreferencesRead(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: StepConfig
    ) {
        try {
            val spCls = XposedHelpers.findClassIfExists(
                "android.app.SharedPreferencesImpl", lpparam.classLoader)
            if (spCls != null) {
                XposedHelpers.findAndHookMethod(spCls, "getInt",
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val key = p.args[0] as? String ?: return
                                val stepKeys = listOf("steps", "step", "steps_count", "daily_steps",
                                    "today_steps", "stepCount", "step_count")
                                if (stepKeys.any { key.lowercase().contains(it) }) {
                                    if (sharedSteps > 0) {
                                        p.result = sharedSteps
                                        LogX.d("竞赛Sync SP读取: $key -> $sharedSteps")
                                    }
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CompetitionMode", "SharedPreferencesImpl.getInt")
            }
        } catch (e: Exception) { LogX.w("SharedPreferences未找到: ${e.message}") }

        try {
            val spCls = XposedHelpers.findClassIfExists(
                "android.app.SharedPreferencesImpl", lpparam.classLoader)
            if (spCls != null) {
                XposedHelpers.findAndHookMethod(spCls, "getLong",
                    String::class.java, Long::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val key = p.args[0] as? String ?: return
                                val stepKeys = listOf("steps", "step", "steps_count", "daily_steps",
                                    "today_steps", "stepCount", "step_count")
                                if (stepKeys.any { key.lowercase().contains(it) }) {
                                    if (sharedSteps > 0) {
                                        p.result = sharedSteps.toLong()
                                        LogX.d("竞赛Sync SP读取(long): $key -> $sharedSteps")
                                    }
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CompetitionMode", "SharedPreferencesImpl.getLong")
            }
        } catch (e: Exception) { LogX.w("SharedPreferences.getLong未找到: ${e.message}") }
    }

    private fun hookContentResolverQuery(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: StepConfig
    ) {
        try {
            val crCls = XposedHelpers.findClassIfExists(
                "android.content.ContentResolver", lpparam.classLoader)
            if (crCls != null) {
                val stepUris = listOf("steps", "fitness", "health", "sport", "activity")
                XposedHelpers.findAndHookMethod(crCls, "query",
                    android.net.Uri::class.java, Array<String>::class.java,
                    String::class.java, Array<String>::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val uri = p.args[0] as? android.net.Uri ?: return
                                val uriStr = uri.toString().lowercase()
                                if (stepUris.any { uriStr.contains(it) }) {
                                    sharedSteps = cfg.customSteps
                                    LogX.d("竞赛Sync URI命中: $uriStr")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("CompetitionMode", "ContentResolver.query(sync)")
            }
        } catch (e: Exception) { LogX.w("ContentResolver未找到: ${e.message}") }
    }
}
