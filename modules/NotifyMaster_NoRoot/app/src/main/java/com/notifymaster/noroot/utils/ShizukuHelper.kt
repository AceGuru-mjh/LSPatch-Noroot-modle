package com.notifymaster.noroot.utils

/**
 * Shizuku 反射调用助手（NoRoot 版 — adb-level ONLY）
 *
 * 允许的命令：
 *  - cmd notification list/cancel
 *  - dumpsys notification
 *  - settings put/get global/secure/system
 *  - am start/stop/broadcast, pm grant/revoke/list
 *  - input tap/swipe, wm size/density
 *  - screencap, screenrecord, content query/insert
 *  - monkey, logcat, tinymix, sqlite3, getprop(read)
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

    fun reset() { available = null }
}
