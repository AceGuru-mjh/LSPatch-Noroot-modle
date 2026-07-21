package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import com.vipunlock.noroot.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku VIP 数据库增强 Hook（adb-level ONLY — NO root）
 *
 * 通过 Shizuku 执行 sqlite3 / content / pm grant 命令
 * 修改目标 APP 的本地数据库实现 VIP 解锁持久化。
 *
 * 功能：
 *  [1] sqlite3 直接修改 user/is_vip 字段
 *  [2] content query/insert 操作 ContentProvider
 *  [3] pm grant 授予 BILLING 权限避免支付校验
 *
 * 硬性限制：
 *  - 仅使用 Shizuku adb-level 命令，不涉及 root
 *  - 所有命令 wrapped in try-catch + isAvailable() 检查
 *  - 不写 /sys/proc/system，不 setprop，不 mount
 *  - sqlite3 操作仅在 Shizuku 运行时有效
 */
object ShizukuVipDbHook {

    private var cfg: VipConfig? = null
    private var targetPkg: String? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.shizukuVipDbEnabled) return
        this.cfg = cfg
        this.targetPkg = lpparam.packageName
        LogX.i("Shizuku VIP DB Hook 已加载（adb-level）")

        try {
            if (!ShizukuHelper.isAvailable()) {
                LogX.w("Shizuku 不可用，跳过 VIP 数据库增强")
                return
            }
            LogX.i("Shizuku 可用，VIP 数据库增强就绪")
        } catch (e: Throwable) {
            LogX.e("Shizuku 检测异常", e)
        }
    }

    // ===== sqlite3 VIP 写入 =====

    /**
     * 修改 user 表的 is_vip 字段
     * 支持自动搜索目标 APP 的 databases 目录
     */
    fun setVipBySqlite(pkg: String): Boolean {
        if (!isReady()) return false
        return try {
            val dbPath = "/data/data/$pkg/databases"
            val cmd = "sqlite3 $dbPath/*.db 'UPDATE user SET is_vip=1'"
            ShizukuHelper.execShell(cmd)
            LogX.i("sqlite3 VIP 写入完成: $pkg")
            true
        } catch (e: Throwable) {
            LogX.e("sqlite3 VIP 写入失败: $pkg", e)
            false
        }
    }

    /**
     * 执行自定义 SQL（直接指定 db 路径和 SQL）
     */
    fun execCustomSql(dbPath: String, sql: String): String? {
        if (!isReady()) return null
        return try {
            ShizukuHelper.execSqlite(dbPath, sql)
        } catch (e: Throwable) {
            LogX.e("自定义 SQL 执行失败", e)
            null
        }
    }

    // ===== content provider 操作 =====

    /**
     * 通过 content query 查询 ContentProvider
     */
    fun queryContentProvider(pkg: String, path: String): String? {
        if (!isReady()) return null
        return try {
            val uri = "content://$pkg.provider/$path"
            val cmd = "content query --uri $uri"
            ShizukuHelper.execShell(cmd)
        } catch (e: Throwable) {
            LogX.e("content query 失败: $pkg/$path", e)
            null
        }
    }

    /**
     * 通过 content insert 写入 ContentProvider
     */
    fun insertContentProvider(pkg: String, path: String, values: Map<String, String>): Boolean {
        if (!isReady()) return false
        return try {
            val uri = "content://$pkg.provider/$path"
            val bindArgs = values.entries.joinToString(" ") { (k, v) -> "--bind $k:s:$v" }
            val cmd = "content insert --uri $uri $bindArgs"
            ShizukuHelper.execShell(cmd)
            LogX.i("content insert 完成: $uri")
            true
        } catch (e: Throwable) {
            LogX.e("content insert 失败: $pkg/$path", e)
            false
        }
    }

    // ===== pm grant 权限 =====

    /**
     * 授予 BILLING 权限（绕过支付校验）
     */
    fun grantBillingPermission(pkg: String): Boolean {
        if (!isReady()) return false
        return try {
            ShizukuHelper.execShell("pm grant $pkg android.permission.BILLING")
            LogX.i("已授予 BILLING 权限: $pkg")
            true
        } catch (e: Throwable) {
            LogX.e("授予 BILLING 权限失败: $pkg", e)
            false
        }
    }

    /**
     * 批量授予目标 APP 常用免校验权限
     */
    fun grantPremiumPermissions(pkg: String): Boolean {
        if (!isReady()) return false
        return try {
            val perms = listOf(
                "android.permission.BILLING",
                "android.permission.GET_ACCOUNTS",
                "android.permission.READ_PHONE_STATE"
            )
            perms.forEach { perm ->
                try { ShizukuHelper.execShell("pm grant $pkg $perm") } catch (_: Throwable) {}
            }
            LogX.i("批量授予权限完成: $pkg")
            true
        } catch (e: Throwable) {
            LogX.e("批量授予权限失败: $pkg", e)
            false
        }
    }

    /** 撤销指定权限 */
    fun revokePermission(pkg: String, perm: String): Boolean {
        if (!isReady()) return false
        return try {
            ShizukuHelper.execShell("pm revoke $pkg $perm")
            LogX.i("已撤销权限 $perm: $pkg")
            true
        } catch (e: Throwable) {
            LogX.e("撤销权限失败: $pkg/$perm", e)
            false
        }
    }

    private fun isReady(): Boolean {
        return cfg?.shizukuVipDbEnabled == true && ShizukuHelper.isAvailable()
    }
}
