package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.Socket

/**
 * 网络延迟优化 Hook（实验性）
 *
 * 功能：
 *  - Hook Socket 构造函数，对所有新建 TCP 连接自动设置 TCP_NODELAY=1
 *  - Hook Socket.setReceiveBufferSize / setSendBufferSize 强制扩大缓冲区
 *  - Hook Socket.setTcpNoDelay 强制为 true（取消 Nagle 算法）
 *  - Hook DatagramSocket 提升数据报优先级
 *
 * 硬性限制：
 *  - 仅修改应用进程内的 Socket 配置
 *  - 实际网络延迟由物理链路和运营商路由决定
 *  - 取消 Nagle 算法会增加小包数量，弱网下可能反而降低吞吐
 *
 * 实验性声明：对延迟敏感的游戏（FPS/MOBA）有效，
 * 对长连接流式游戏（云游戏）可能效果不明显。
 */
object NetworkLatencyOptHook {

    private const val RECV_BUFFER = 256 * 1024  // 256KB
    private const val SEND_BUFFER = 256 * 1024  // 256KB

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.networkLatencyOptEnabled) return
        LogX.i("网络延迟优化启动（实验性，仅应用层）")

        hookSocketConstructors(lpparam)
        hookSocketOptions(lpparam)
        hookDatagramSocket(lpparam)
    }

    /** Hook Socket 构造函数，连接后自动设置 TCP_NODELAY */
    private fun hookSocketConstructors(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sk = XposedHelpers.findClassIfExists("java.net.Socket", lpparam.classLoader) ?: return

            // 无参构造：默认 Socket()
            try {
                XposedHelpers.findAndHookConstructor(sk, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        applyTcpNoDelay(p.thisObject as? Socket)
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Socket(String host, int port)
            try {
                XposedHelpers.findAndHookConstructor(sk,
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            applyTcpNoDelay(p.thisObject as? Socket)
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Socket(InetAddress address, int port)
            try {
                XposedHelpers.findAndHookConstructor(sk,
                    "java.net.InetAddress", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            applyTcpNoDelay(p.thisObject as? Socket)
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("Socket", "constructors -> TCP_NODELAY")
        } catch (e: Throwable) {
            LogX.hookFailed("Socket", "constructors", e)
        }
    }

    /** Hook setTcpNoDelay / setReceiveBufferSize / setSendBufferSize 强制值 */
    private fun hookSocketOptions(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sk = XposedHelpers.findClassIfExists("java.net.Socket", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(sk, "setTcpNoDelay",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 强制 on
                            p.args[0] = true
                        }
                    })
                LogX.hookSuccess("Socket", "setTcpNoDelay -> true")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(sk, "setReceiveBufferSize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = p.args[0] as Int
                            if (v < RECV_BUFFER) p.args[0] = RECV_BUFFER
                        }
                    })
                LogX.hookSuccess("Socket", "setReceiveBufferSize -> >= $RECV_BUFFER")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(sk, "setSendBufferSize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = p.args[0] as Int
                            if (v < SEND_BUFFER) p.args[0] = SEND_BUFFER
                        }
                    })
                LogX.hookSuccess("Socket", "setSendBufferSize -> >= $SEND_BUFFER")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Socket", "setSocketOptions", e)
        }
    }

    /** 对一个 Socket 应用 TCP_NODELAY + 缓冲区 */
    private fun applyTcpNoDelay(socket: Socket?) {
        try {
            socket ?: return
            if (!socket.isConnected) return
            socket.tcpNoDelay = true
            try { socket.receiveBufferSize = RECV_BUFFER } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            try { socket.sendBufferSize = SEND_BUFFER } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** Hook DatagramSocket 提升数据报处理（仅日志） */
    private fun hookDatagramSocket(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ds = XposedHelpers.findClassIfExists(
                "java.net.DatagramSocket", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(ds, "setReceiveBufferSize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = p.args[0] as Int
                            if (v < RECV_BUFFER) p.args[0] = RECV_BUFFER
                        }
                    })
                LogX.hookSuccess("DatagramSocket", "setReceiveBufferSize")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("DatagramSocket", "setReceiveBufferSize", e)
        }
    }
}
