package com.microx.enhancer.hooks

import com.microx.enhancer.models.MicroXConfig
import com.microx.enhancer.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 自动抢红包 + 自动收取转账 Hook
 *
 * 对标 FkWeChat：监听红包/转账消息到达，自动点击领取。
 *
 * 实现策略：
 *  - Hook 微信聊天消息接收方法（com.tencent.mm.plugin.message 相关）
 *  - 检测消息类型为红包/转账时，延迟短时间后自动调用打开逻辑
 *  - 多候选类名容错，适配不同微信版本
 *
 * 注意：微信反作弊可能检测自动领取，建议设置合理延迟（800-2000ms）
 */
object AutoRedPacketHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: MicroXConfig) {
        if (!cfg.autoRedPacketEnabled && !cfg.autoTransferEnabled) return
        LogX.i("自动红包/转账启动 | 红包=${cfg.autoRedPacketEnabled} 转账=${cfg.autoTransferEnabled}")

        if (cfg.autoRedPacketEnabled) hookRedPacketMessage(lpparam)
        if (cfg.autoTransferEnabled) hookTransferMessage(lpparam)
    }

    /** Hook 红包消息接收 */
    private fun hookRedPacketMessage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 微信红包消息处理类候选（多版本适配）
        val candidates = listOf(
            "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotifView",
            "com.tencent.mm.plugin.luckymoney.model.ab",
            "com.tencent.mm.plugin.luckymoney.b.g",
            "com.tencent.mm.modelmulti.a"
        )
        var hooked = false
        for (cls in candidates) {
            if (hooked) break
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                // Hook 所有可能的接收方法
                for (method in clazz.declaredMethods) {
                    val name = method.name
                    if (name.contains("onReceive", true) ||
                        name.contains("handleMessage", true) ||
                        name.contains("notify", true) ||
                        name.contains("addMsg", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(p: MethodHookParam) {
                                        try {
                                            LogX.d("[红包] 检测到红包消息，延迟领取")
                                            // 延迟后调用打开红包（通过反射调 LuckyMoneyUI）
                                            scheduleOpenLuckyMoney(lpparam, p.thisObject)
                                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    }
                                })
                            hooked = true
                            LogX.hookSuccess(cls, name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
        if (!hooked) LogX.d("[红包] 未匹配到红包处理类，可能微信版本不兼容")
    }

    /** Hook 转账消息接收 */
    private fun hookTransferMessage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.tencent.mm.plugin.wallet.pay.a",
            "com.tencent.mm.plugin.transfer.ui.TransferUI",
            "com.tencent.mm.modelmulti.a"
        )
        for (cls in candidates) {
            val clazz = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
            try {
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("onReceive", true) ||
                        method.name.contains("addMsg", true)) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, method.name,
                                *method.parameterTypes.map { it as Any }.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(p: MethodHookParam) {
                                        try {
                                            LogX.d("[转账] 检测到转账消息，延迟收取")
                                            scheduleAcceptTransfer(lpparam, p.thisObject)
                                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    }
                                })
                            LogX.hookSuccess(cls, method.name)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 延迟打开红包 */
    private fun scheduleOpenLuckyMoney(lpparam: XC_LoadPackage.LoadPackageParam, context: Any) {
        try {
            Thread {
                try {
                    Thread.sleep(1000)  // 延迟1秒，避免检测
                    // 反射调用打开红包逻辑
                    val uiCls = XposedHelpers.findClassIfExists(
                        "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotifView", lpparam.classLoader)
                    if (uiCls != null) {
                        XposedHelpers.callMethod(context, "onClick", context)
                    }
                } catch (_: InterruptedException) {
                } catch (e: Throwable) { LogX.d("[红包] 打开异常: ${e.message}") }
            }.start()
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    /** 延迟收取转账 */
    private fun scheduleAcceptTransfer(lpparam: XC_LoadPackage.LoadPackageParam, context: Any) {
        try {
            Thread {
                try {
                    Thread.sleep(1500)
                    XposedHelpers.callMethod(context, "onClick", context)
                } catch (_: InterruptedException) {
                } catch (e: Throwable) { LogX.d("[转账] 收取异常: ${e.message}") }
            }.start()
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
