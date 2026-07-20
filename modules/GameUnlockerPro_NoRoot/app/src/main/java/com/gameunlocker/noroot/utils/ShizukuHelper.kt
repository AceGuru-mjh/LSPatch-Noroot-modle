package com.gameunlocker.noroot.utils

/**
 * Shizuku 反射调用助手（NoRoot 版仅用于轻量刷新率设置 / 后台冻结提示，不进行系统级 setprop）
 *
 * 硬性限制：
 *  - LSPatch 本地模式下 Shizuku 未必在运行，所有调用通过 try-catch 保护
 *  - 仅用于设置 SurfaceFlinger 刷新率提示属性 + settings put system 帧率
 *  - 不修改 /sys 节点、不修改 CPU/GPU 调频、不进行真 Root 操作
 */
object ShizukuHelper {

    private var available: Boolean? = null

    /** 检测 Shizuku 是否可用 */
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
     * 通过 Shizuku 执行 Shell 命令
     * 用于：settings put system 帧率属性、am force-stop 冻结后台
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
