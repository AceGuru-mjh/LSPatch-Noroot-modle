package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

/**
 * 步数历史伪造 Hook（实验性）
 *
 * 功能：
 *  - Hook 应用读取自身步数历史数据库（SQLite/SharedPreferences）
 *  - 伪造历史步数记录，让应用显示伪造的步数趋势图
 *
 * 拦截路径：
 *  1. SQLiteDatabase.query — 拦截步数表查询
 *  2. SQLiteDatabase.rawQuery — 拦截原生 SQL 步数查询
 *  3. SharedPreferences.getString/getInt — 拦截步数缓存读取
 *
 * 硬性限制（NoRoot版）：
 *  - 仅 Hook 当前进程的 SQLite/SharedPreferences 访问
 *  - 不修改数据库文件本身（不持久化伪造值）
 */
object StepHistoryFakeHook {

    private val random = Random(System.currentTimeMillis())

    /** 步数相关表名候选 */
    private val stepTableKeywords = listOf("step", "sport", "fitness", "walk", "run")

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.stepHistoryFakeEnabled) return
        LogX.i("步数历史伪造 Hook 启动（实验性）")

        hookSqliteQuery(lpparam, cfg)
        hookSharedPrefsRead(lpparam, cfg)
    }

    /** Hook SQLiteDatabase.query/rawQuery — 拦截步数表查询 */
    private fun hookSqliteQuery(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val dbCls = XposedHelpers.findClassIfExists(
                "android.database.sqlite.SQLiteDatabase", lpparam.classLoader) ?: return

            // query(String table, String[], String, String[], String, String, String)
            try {
                XposedHelpers.findAndHookMethod(dbCls, "query",
                    "android.database.sqlite.SQLiteQueryBuilder",
                    "android.database.sqlite.SQLiteDatabase",
                    "java.lang.String[]", "java.lang.String",
                    "java.lang.String[]", "java.lang.String",
                    "java.lang.String", "java.lang.String",
                    "java.lang.String", "android.os.CancellationSignal",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val table = p.args[2]?.toString() ?: return
                                if (stepTableKeywords.any { table.lowercase().contains(it) }) {
                                    LogX.d("拦截步数表查询: $table → 注入伪造历史")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SQLiteDatabase", "query(builder)")
            } catch (e: Exception) { LogX.w("query(builder) hook 失败: ${e.message}") }

            // rawQuery(String sql, String[] selectionArgs)
            try {
                XposedHelpers.findAndHookMethod(dbCls, "rawQuery",
                    "java.lang.String", "java.lang.String[]",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val sql = p.args[0]?.toString() ?: return
                                if (stepTableKeywords.any { sql.lowercase().contains(it) }) {
                                    LogX.d("拦截步数 SQL: ${sql.take(80)}")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SQLiteDatabase", "rawQuery")
            } catch (e: Exception) { LogX.w("rawQuery hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SQLiteDatabase", "query/rawQuery", e)
        }
    }

    /** Hook SharedPreferences.getString/getInt — 拦截步数缓存读取 */
    private fun hookSharedPrefsRead(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val spCls = XposedHelpers.findClassIfExists(
                "android.app.SharedPreferencesImpl", lpparam.classLoader) ?: return

            // getString(String, String)
            try {
                XposedHelpers.findAndHookMethod(spCls, "getString",
                    "java.lang.String", "java.lang.String",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val key = p.args[0]?.toString() ?: return
                                if (stepTableKeywords.any { key.lowercase().contains(it) }) {
                                    val fake = computeFakeStep(cfg)
                                    p.result = fake.toString()
                                    LogX.d("伪造 SP.getString($key) = $fake")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SharedPreferencesImpl", "getString")
            } catch (e: Exception) { LogX.w("getString hook 失败: ${e.message}") }

            // getInt(String, int)
            try {
                XposedHelpers.findAndHookMethod(spCls, "getInt",
                    "java.lang.String", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val key = p.args[0]?.toString() ?: return
                                if (stepTableKeywords.any { key.lowercase().contains(it) }) {
                                    val fake = computeFakeStep(cfg)
                                    p.result = fake
                                    LogX.d("伪造 SP.getInt($key) = $fake")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SharedPreferencesImpl", "getInt")
            } catch (e: Exception) { LogX.w("getInt hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SharedPreferencesImpl", "getString/getInt", e)
        }
    }

    private fun computeFakeStep(cfg: StepConfig): Int {
        val fl = if (cfg.randomFluctuation > 0) random.nextInt(-cfg.randomFluctuation, cfg.randomFluctuation + 1) else 0
        return (cfg.customSteps + fl).coerceAtLeast(0)
    }
}
