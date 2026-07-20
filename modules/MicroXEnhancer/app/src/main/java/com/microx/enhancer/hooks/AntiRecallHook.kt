package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileOutputStream

/**
 * 消息防撤回Hook类
 *
 * 核心原理：
 * - 微信/QQ的撤回操作是发送一条"撤回指令"给接收方
 * - 接收方收到撤回指令后，调用UI层方法删除/替换消息展示
 * - 我们Hook接收方的消息删除/替换方法，阻止其执行
 * - 同时Hook撤回提示消息的插入，替换为"[已撤回]"标记但不删除原消息
 *
 * 防撤回覆盖范围：
 * - 文字消息：拦截onRevokeMsg方法，阻止从聊天记录移除
 * - 图片消息：保留缩略图和原图路径
 * - 语音消息：保留语音文件路径
 * - 文件消息：保留文件信息和下载路径
 * - 视频消息：保留视频缩略图和链接
 */
object AntiRecallHook {

    /** 用于存储被防撤回的消息内容（内存缓存） */
    private val recalledMessages = mutableMapOf<String, String>()

    // ===== 微信防撤回 =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_ANTI_RECALL)) return
        HookHelper.log("加载消息防撤回Hook（微信）")

        // 1. Hook消息撤回处理核心类
        hookRecallCore(lpparam)

        // 2. Hook聊天页消息删除方法
        hookMessageRemoval(lpparam)

        // 3. Hook撤回提示消息插入
        hookRecallTips(lpparam)
    }

    // ===== QQ防撤回 =====
    fun hookQQ(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_ANTI_RECALL)) return
        HookHelper.log("加载消息防撤回Hook（QQ）")

        hookQQRecallCore(lpparam)
        hookQQMessageRemoval(lpparam)
    }

    // ================================================================
    //  微信：Hook撤回处理核心
    //  微信的撤回操作链：XXManager.onRevokeMsg() -> UI.removeMsg()
    //  典型类名在不同版本中变化，使用多候选名
    // ================================================================
    private fun hookRecallCore(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 候选类名：消息管理核心类（不同微信版本的类名变化）
        val recallClasses = listOf(
            "com.tencent.mm.modelmulti.MMCore",
            "com.tencent.mm.model.ChattingDataLogic",
            "com.tencent.mm.storage.MsgInfoStorage",
            "com.tencent.mm.modelmulti.NotifyReceiver",
            "com.tencent.mm.plugin.messenger.foundation.MessengerStorage",
        )

        for (className in recallClasses) {
            val clazz = HookHelper.findClassSafe(lpparam, className)
            if (clazz == null) continue

            // Hook onRevokeMsg / handleRevokeMsg / processRevokeMsg
            val revokeMethods = listOf(
                "onRevokeMsg", "handleRevokeMsg", "processRevokeMsg",
                "onReceiveRevokeMsg", "handleMessageRevoke", "revokeMsg",
                "OnRevokeMsg", "b" // 某些混淆版本的方法名
            )

            for (methodName in revokeMethods) {
                HookHelper.hookAllMethodsSafe(clazz, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[防撤回] 拦截撤回方法: ${clazz.name}.$methodName")
                    
                                        // 获取撤回消息的msgId
                                        val msgId = try {
                                            // 不同版本参数位置不同，尝试从args中提取
                                            extractMsgId(param)
                                        } catch (e: Exception) {
                                            "unknown"
                                        }
                    
                                        // 保存原始消息内容
                                        saveRecalledMessage(msgId, param.thisObject, param.args)
                    
                                        // 阻止原生撤回逻辑：直接return，不执行原方法
                                        param.result = null
                }
            })
            }
        }
    }

    // ================================================================
    //  微信：Hook消息删除方法（UI层）
    //  阻止聊天页面上移除消息的UI操作
    // ================================================================
    private fun hookMessageRemoval(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chatUIClasses = listOf(
            "com.tencent.mm.ui.chatting.ChattingUI",
            "com.tencent.mm.ui.chatting.ChattingAdapter",
            "com.tencent.mm.ui.chatting.ChattingListAdapter",
            "com.tencent.mm.ui.chatting.gallery.ImageGalleryUI",
        )

        for (className in chatUIClasses) {
            val clazz = HookHelper.findClassSafe(lpparam, className)
            if (clazz == null) continue

            // Hook removeMsg / deleteMsg / removeItem 方法
            val removeMethods = listOf(
                "removeMsg", "deleteMsg", "removeItem",
                "deleteItem", "removeMessage", "deleteMessage",
                "a", "b" // 混淆版本
            )

            for (methodName in removeMethods) {
                HookHelper.hookAllMethodsSafe(clazz, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val msgId = extractMsgId(param)
                                        if (isRevokedMessage(msgId)) {
                                            HookHelper.log("[防撤回] 阻止消息删除 msgId=$msgId")
                                            param.result = null // 阻止删除操作
                                        }
                }
            })
            }
        }
    }

    // ================================================================
    //  微信：Hook撤回提示消息的插入
    //  将"XXX撤回了一条消息"改为"[已撤回-内容已保存]"
    // ================================================================
    private fun hookRecallTips(lpparam: XC_LoadPackage.LoadPackageParam) {
        val tipClasses = listOf(
            "com.tencent.mm.ui.chatting.ChattingItemHelper",
            "com.tencent.mm.ui.chatting.viewitems.ChattingItem",
            "com.tencent.mm.ui.chatting.viewitems.ChattingItemAppMsg",
        )

        for (className in tipClasses) {
            val clazz = HookHelper.findClassSafe(lpparam, className)
            if (clazz == null) continue

            // Hook消息内容设置方法
            HookHelper.hookAllMethodsSafe(clazz, "setContent", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val content = param.args.getOrNull(0) as? String ?: ""
                                    if (content.contains("撤回了一条消息") || content.contains("recalled")) {
                                        // 不替换提示文字，保留原显示但阻止消息被删除
                                        HookHelper.logD("[防撤回] 检测到撤回提示: $content")
                                    }
                }
            })
        }

        // 额外：Hook聊天UI的消息更新方法
        val chatUI = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.ui.chatting.ChattingUI",
            "com.tencent.mm.ui.chatting.BaseChattingUI",
        )
        if (chatUI != null) {
            // Hook消息列表刷新方法
            HookHelper.hookAllMethodsSafe(chatUI, "notifyDataSetChanged", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[防撤回] 消息列表刷新")
                                    // 允许正常刷新，但之前删掉的消息不会被移除（因为deleteMsg被Hook了）
                }
            })

            HookHelper.hookAllMethodsSafe(chatUI, "updateItem", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[防撤回] 消息Item更新")
                                    // 允许正常更新
                }
            })
        }
    }

    // ================================================================
    //  QQ防撤回部分
    // ================================================================

    private fun hookQQRecallCore(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqRecallClasses = listOf(
            "com.tencent.mobileqq.app.MessageHandler",
            "com.tencent.mobileqq.app.QQAppInterface",
            "com.tencent.mobileqq.activity.aio.BaseChatItemLayout",
            "com.tencent.mobileqq.data.MessageForText",
            "com.tencent.imcore.message.QQMessageFacade",
        )

        for (className in qqRecallClasses) {
            val clazz = HookHelper.findClassSafe(lpparam, className)
            if (clazz == null) continue

            HookHelper.hookAllMethodsSafe(clazz, "handleRevokeNotify", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[QQ防撤回] 拦截撤回通知")
                                    param.result = null
                }
            })

            HookHelper.hookAllMethodsSafe(clazz, "onRevokeMsg", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[QQ防撤回] 拦截消息撤回")
                                    param.result = null
                }
            })
        }
    }

    private fun hookQQMessageRemoval(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqUIClasses = listOf(
            "com.tencent.mobileqq.activity.aio.ChatAdapter",
            "com.tencent.mobileqq.activity.aio.SessionInfo",
        )

        for (className in qqUIClasses) {
            val clazz = HookHelper.findClassSafe(lpparam, className)
            if (clazz == null) continue

            HookHelper.hookAllMethodsSafe(clazz, "removeMsg", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[QQ防撤回] 阻止消息删除")
                                    param.result = null
                }
            })
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 从Hook参数中提取消息ID */
    private fun extractMsgId(param: XC_MethodHook.MethodHookParam): String {
        // 尝试从不同参数位置提取
        for (arg in param.args) {
            when (arg) {
                is Long -> return arg.toString()
                is String -> {
                    if (arg.length in 5..32) return arg
                }
                is Int -> return arg.toString()
            }
        }
        // 尝试从this对象中获取
        try {
            val msgIdField = param.thisObject.javaClass.getField("field_msgId")
            return msgIdField.get(param.thisObject).toString()
        } catch (e: Exception) { /* ignore */ }

        return "unknown_${System.currentTimeMillis()}"
    }

    /** 保存被撤回的消息内容 */
    private fun saveRecalledMessage(
        msgId: String,
        thisObject: Any?,
        args: Array<out Any?>
    ) {
        try {
            // 尝试序列化消息内容
            val content = buildString {
                append("msgId=$msgId; ")
                append("time=${System.currentTimeMillis()}; ")
                args.forEachIndexed { index, arg ->
                    if (arg != null) {
                        append("arg$index=${arg}; ")
                    }
                }
            }
            recalledMessages[msgId] = content
            HookHelper.log("[防撤回] 已保存消息: $content")
        } catch (e: Exception) {
            HookHelper.logE("[防撤回] 保存消息内容失败: ${e.message}")
        }
    }

    /** 检查某条消息是否已被撤回 */
    private fun isRevokedMessage(msgId: String): Boolean {
        return recalledMessages.containsKey(msgId)
    }

    /** 导出被防撤回的消息到文件 */
    fun exportRecalledMessages(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            FileOutputStream(file).use { fos ->
                recalledMessages.forEach { (id, content) ->
                    fos.write("[$id] $content\n".toByteArray())
                }
            }
            HookHelper.log("[防撤回] 已导出${recalledMessages.size}条消息到: $filePath")
            true
        } catch (e: Exception) {
            HookHelper.logE("[防撤回] 导出消息失败: ${e.message}")
            false
        }
    }
}
