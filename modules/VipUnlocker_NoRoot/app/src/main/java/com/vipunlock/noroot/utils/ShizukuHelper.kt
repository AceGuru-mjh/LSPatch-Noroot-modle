package com.vipunlock.noroot.utils

/**
 * Shizuku 反射调用助手（NoRoot 版 — adb-level ONLY）
 *
 * 允许的命令：
 *  - sqlite3 <db> '<SQL>'        — 执行 SQL 修改
 *  - content query/insert         — ContentProvider 操作
 *  - pm grant/revoke/list         — 权限管理
 *  - cmd notification, dumpsys, settings
 *  - am start/stop/broadcast, monkey, logcat
 *
 * FORBIDDEN（不在本类中实现）：
 *  - setprop(write), mount/umount, iptables
 *  - 写 /sys/proc/vendor/system
 *  - chcon, kill system_server, Magisk overlay
 */
object ShizukuHelper {

    private var available: Boolean? = null

    fun isAvailable(): Boolean {
        if (available != null) return available!!
        available = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getMethod("pingBinder")
            (m.invoke(null) as? Boolean) ?: false
        } catch (_: Throwable) {
            false
        }
        return available!!
    }

    /**
     * 通过 Shizuku 执行 Shell 命令（adb-level）
     */
    fun execShell(cmd: String): String? {
        if (!isAvailable()) return null
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) ?: return null
            val isField = process.javaClass.getMethod("getInputStream")
            val isStr = isField.invoke(process) as? java.io.InputStream
            isStr?.bufferedReader()?.readText()
        } catch (e: Throwable) {
            LogX.e("Shizuku Shell 异常: $cmd", e)
            null
        }
    }

    /**
     * 通过 Shizuku sqlite3 执行数据库修改
     * 格式: "sqlite3 /path/to/db 'SQL_STATEMENT'"
     */
    fun execSqlite(dbPath: String, sql: String): String? {
        if (!isAvailable()) return null
        return try {
            execShell("sqlite3 '$dbPath' '$sql'")
        } catch (e: Throwable) {
            LogX.e("Shizuku sqlite3 异常: $dbPath", e)
            null
        }
    }

    fun reset() { available = null }
}
