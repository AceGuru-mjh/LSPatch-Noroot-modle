package com.microx.enhancer.hooks

import com.microx.enhancer.models.MicroXConfig
import com.microx.enhancer.utils.LogX
import com.microx.enhancer.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku sqlite3 数据库直读 Hook
 *
 * 通过 Shizuku adb 级执行 sqlite3 命令直接访问微信/QQ的数据库文件，
 * 用于消息导出、语音保存等高级操作。
 *
 * 硬性限制：
 *  - 仅通过 Shizuku adb 级执行 sqlite3 命令
 *  - 不调用 root 命令
 *  - 需要目标应用的数据库路径已知
 */
object ShizukuDbHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: MicroXConfig) {
        if (!cfg.shizukuDbAccessEnabled) return
        LogX.i("Shizuku sqlite3数据库直读启动（Shizuku adb级）")

        hookApplicationOnCreate(lpparam, cfg)
    }

    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: MicroXConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        accessDatabase(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate (sqlite3)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate (sqlite3)", e)
        }
    }

    private fun accessDatabase(cfg: MicroXConfig) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过 sqlite3 数据库访问")
            return
        }

        val dbPath = cfg.wechatDbPath.ifEmpty {
            "/data/data/com.tencent.mm/MicroMsg/EnMicroMsg.db"
        }

        try {
            val probeCmd = "sqlite3 \"$dbPath\" '.tables'"
            val result = ShizukuHelper.execShell(probeCmd)
            if (result != null) {
                LogX.i("sqlite3 数据库探针: $result")
            } else {
                LogX.w("sqlite3 数据库探针 执行失败（路径可能不正确或数据库加密）")
            }
        } catch (e: Throwable) {
            LogX.e("sqlite3 数据库探针 异常", e)
        }

        try {
            val msgCountCmd = "sqlite3 \"$dbPath\" 'SELECT COUNT(*) FROM message;'"
            val msgResult = ShizukuHelper.execShell(msgCountCmd)
            if (msgResult != null) {
                LogX.i("sqlite3 消息总数: $msgResult")
            }
        } catch (e: Throwable) {
            LogX.e("sqlite3 消息查询 异常", e)
        }
    }
}
