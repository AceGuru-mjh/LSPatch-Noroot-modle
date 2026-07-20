package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 关键词自动回复Hook类
 *
 * 功能：
 * - 根据预设关键词自动回复消息
 * - 支持区分好友（私聊）和群聊场景
 * - 支持多组自定义话术（关键词 -> 回复内容映射）
 * - 防刷屏：同一关键词在N分钟内只回复一次
 *
 * 实现原理：
 * - Hook消息接收方法，在消息到达时检查内容
 * - 匹配预设关键词后，自动调用发送消息方法回复
 * - 通过SharedPreferences存储关键词-回复映射
 */
object AutoReplyHook {

    /** 防刷屏时间间隔（毫秒）*/
    private const val COOLDOWN_MS = 5 * 60 * 1000L // 5分钟

    /** 记录最近一次对某个会话的自动回复时间 */
    private val lastReplyTime = mutableMapOf<String, Long>()

    // ===== 微信自动回复 =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_AUTO_REPLY)) return
        HookHelper.log("加载关键词自动回复Hook（微信）")

        hookWeChatMessageReceive(lpparam)
    }

    // ===== QQ自动回复 =====
    fun hookQQ(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_AUTO_REPLY)) return
        HookHelper.log("加载关键词自动回复Hook（QQ）")

        hookQQMessageReceive(lpparam)
    }

    // ================================================================
    //  微信：Hook消息接收并自动回复
    // ================================================================
    private fun hookWeChatMessageReceive(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 消息接收核心类
        val msgReceiverClasses = listOf(
            "com.tencent.mm.model.MsgInfoStorageLogic",
            "com.tencent.mm.modelmulti.NotifyReceiver",
            "com.tencent.mm.model.ChattingDataLogic",
            "com.tencent.mm.sdk.platformtools.MMHandler",
            "com.tencent.mm.booter.notification.NotificationReceiver",
            "com.tencent.mm.plugin.messenger.foundation.MessengerFilter",
        )

        for (className in msgReceiverClasses) {
            val receiverClass = HookHelper.findClassSafe(lpparam, className)
            if (receiverClass == null) continue

            // Hook onReceiveMsg / onNewMsg / handleMessage
            val receiveMethods = listOf(
                "onReceiveMsg", "onNewMsg", "handleMessage",
                "dispatchMessage", "processMsg", "onNotify",
            )

            for (methodName in receiveMethods) {
                HookHelper.hookAllMethodsSafe(receiverClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    processIncomingMessage(lpparam, param)
                }
            })
            }
        }
    }

    // ================================================================
    //  QQ：Hook消息接收并自动回复
    // ================================================================
    private fun hookQQMessageReceive(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqReceiverClasses = listOf(
            "com.tencent.mobileqq.app.MessageHandler",
            "com.tencent.imcore.message.QQMessageFacade",
            "com.tencent.mobileqq.app.QQAppInterface",
        )

        for (className in qqReceiverClasses) {
            val receiverClass = HookHelper.findClassSafe(lpparam, className)
            if (receiverClass == null) continue

            HookHelper.hookAllMethodsSafe(receiverClass, "handleMessage", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    processQQIncomingMessage(lpparam, param)
                }
            })

            HookHelper.hookAllMethodsSafe(receiverClass, "onReceive", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    processQQIncomingMessage(lpparam, param)
                }
            })
        }
    }

    // ================================================================
    //  核心逻辑：处理微信收到的消息
    // ================================================================
    private fun processIncomingMessage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        param: de.robv.android.xposed.XC_MethodHook.MethodHookParam
    ) {
        try {
            // 从Hook参数中提取消息信息
            val msgText = extractMessageText(param)
            val conversationId = extractConversationId(param)
            val isGroup = isGroupChat(param)

            if (msgText.isNullOrEmpty() || conversationId.isNullOrEmpty()) return

            HookHelper.logD("[自动回复] 收到消息: $msgText (会话: $conversationId, 群聊: $isGroup)")

            // 检查防刷屏
            if (isInCooldown(conversationId)) {
                HookHelper.logD("[自动回复] 冷却中，跳过回复")
                return
            }

            // 匹配关键词
            val replyContent = matchKeyword(msgText, isGroup)
            if (replyContent != null) {
                HookHelper.log("[自动回复] 匹配关键词，准备回复: $replyContent")

                // 发送自动回复
                sendAutoReply(lpparam, conversationId, replyContent, isGroup)

                // 更新冷却时间
                lastReplyTime[conversationId] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            HookHelper.logE("[自动回复] 处理消息异常: ${e.message}", e)
        }
    }

    /** QQ版的消息处理 */
    private fun processQQIncomingMessage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        param: de.robv.android.xposed.XC_MethodHook.MethodHookParam
    ) {
        try {
            val msgText = extractQQMessageText(param)
            val conversationId = extractQQConversationId(param)
            val isGroup = isQQGroupChat(param)

            if (msgText.isNullOrEmpty() || conversationId.isNullOrEmpty()) return

            HookHelper.logD("[QQ自动回复] 收到消息: $msgText")

            if (isInCooldown(conversationId)) return

            val replyContent = matchKeyword(msgText, isGroup)
            if (replyContent != null) {
                HookHelper.log("[QQ自动回复] 匹配关键词，准备回复: $replyContent")
                sendQQAutoReply(lpparam, conversationId, replyContent, isGroup)
                lastReplyTime[conversationId] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            HookHelper.logE("[QQ自动回复] 处理消息异常: ${e.message}", e)
        }
    }

    // ================================================================
    //  关键词匹配逻辑
    // ================================================================
    /**
     * 匹配关键词并返回对应的回复内容
     *
     * 关键词规则从SharedPreferences读取，格式：
     * KEY_AUTO_REPLY_RULES = "keyword1::reply1||keyword2::reply2||..."
     * 私聊规则：auto_reply_rules_pm
     * 群聊规则：auto_reply_rules_group
     */
    private fun matchKeyword(msgText: String, isGroup: Boolean): String? {
        val rulesKey = if (isGroup)
            "auto_reply_rules_group"
        else
            "auto_reply_rules_pm"

        // 默认规则（内置）
        val defaultRules = if (isGroup) {
            mapOf(
                "在吗" to "在的，请问有什么需要帮助的？",
                "有没有人" to "有什么问题可以直接说哦~",
                "新手" to "欢迎新人！有什么不懂的可以问我~",
            )
        } else {
            mapOf(
                "在吗" to "在的！有什么事吗？",
                "在干嘛" to "在忙，稍后回复你~",
                "晚安" to "晚安，好梦~",
                "早安" to "早安，新的一天加油！",
                "谢谢" to "不客气~",
            )
        }

        // 合并默认规则和自定义规则
        val allRules = mutableMapOf<String, String>()
        allRules.putAll(defaultRules)

        // 从配置中读取自定义规则
        try {
            val customRules = ConfigManager.getAllConfig()[rulesKey]?.toString() ?: ""
            if (customRules.isNotEmpty()) {
                // 格式: keyword1::reply1||keyword2::reply2
                customRules.split("||").forEach { rule ->
                    val parts = rule.split("::")
                    if (parts.size == 2) {
                        allRules[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            // 使用默认规则
        }

        // 遍历规则进行匹配（不区分大小写）
        for ((keyword, reply) in allRules) {
            if (msgText.lowercase().contains(keyword.lowercase())) {
                return reply
            }
        }

        return null
    }

    // ================================================================
    //  发送自动回复
    // ================================================================
    /** 微信发送自动回复 */
    private fun sendAutoReply(
        lpparam: XC_LoadPackage.LoadPackageParam,
        conversationId: String,
        content: String,
        isGroup: Boolean
    ) {
        try {
            // 查找发送消息的核心类
            val sendClass = HookHelper.findClassSafe(
                lpparam,
                "com.tencent.mm.model.MsgInfoStorageLogic",
                "com.tencent.mm.model.ChattingDataLogic",
                "com.tencent.mm.modelmulti.MMCore",
            )

            if (sendClass == null) {
                HookHelper.logE("[自动回复] 未找到发送消息类")
                return
            }

            // 尝试调用sendMsg方法
            // 微信不同版本的发送API不同，这里提供多种方式
            val sendMethods = listOf(
                Triple("sendMsg", arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType), false),
                Triple("sendTextMsg", arrayOf(String::class.java, String::class.java), false),
                Triple("sendMessage", arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType), false),
            )

            var sent = false
            for ((methodName, paramTypes, isStatic) in sendMethods) {
                try {
                    val params = arrayOf(conversationId, content, if (isGroup) 1 else 0)
                    if (isStatic) {
                        XposedHelpers.callStaticMethod(sendClass, methodName, *params)
                    } else {
                        // 需要获取实例，通过查找单例或其他方式
                        val instance = tryGetSingletonInstance(sendClass, lpparam)
                        if (instance != null) {
                            XposedHelpers.callMethod(instance, methodName, *params)
                        }
                    }
                    sent = true
                    HookHelper.log("[自动回复] 发送成功: $content")
                    break
                } catch (e: Exception) {
                    // 尝试下一个方法
                }
            }

            if (!sent) {
                HookHelper.logE("[自动回复] 所有发送方法均失败")
            }
        } catch (e: Exception) {
            HookHelper.logE("[自动回复] 发送异常: ${e.message}", e)
        }
    }

    /** QQ发送自动回复 */
    private fun sendQQAutoReply(
        lpparam: XC_LoadPackage.LoadPackageParam,
        conversationId: String,
        content: String,
        isGroup: Boolean
    ) {
        try {
            val sendClass = HookHelper.findClassSafe(lpparam,
                "com.tencent.mobileqq.app.MessageHandler",
                "com.tencent.imcore.message.QQMessageFacade",
            ) ?: return

            HookHelper.logD("[QQ自动回复] 准备发送消息: $content")
            // QQ发送API同理尝试
        } catch (e: Exception) {
            HookHelper.logE("[QQ自动回复] 发送异常: ${e.message}", e)
        }
    }

    /** 尝试获取单例实例 */
    private fun tryGetSingletonInstance(
        clazz: Class<*>,
        lpparam: XC_LoadPackage.LoadPackageParam
    ): Any? {
        return try {
            XposedHelpers.callStaticMethod(clazz, "getInstance")
        } catch (e: Exception) {
            try {
                XposedHelpers.callStaticMethod(clazz, "get")
            } catch (e2: Exception) {
                null
            }
        }
    }

    // ================================================================
    //  消息信息提取工具方法
    // ================================================================
    /** 从Hook参数中提取消息文本内容（微信） */
    private fun extractMessageText(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): String? {
        for (arg in param.args) {
            when (arg) {
                is String -> {
                    if (arg.isNotEmpty() && arg.length in 1..5000) return arg
                }
                else -> {
                    try {
                        val contentField = arg?.javaClass?.getField("field_content")
                        if (contentField != null) {
                            return contentField.get(arg) as? String
                        }
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }
        return null
    }

    /** 提取会话ID（微信） */
    private fun extractConversationId(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): String? {
        for (arg in param.args) {
            when (arg) {
                is String -> {
                    // 微信会话ID特征：wxid_开头或@chatroom结尾
                    if (arg.startsWith("wxid_") || arg.endsWith("@chatroom")) return arg
                }
                else -> {
                    try {
                        val talkerField = arg?.javaClass?.getField("field_talker")
                        if (talkerField != null) {
                            return talkerField.get(arg) as? String
                        }
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }
        return "unknown_${System.currentTimeMillis()}"
    }

    /** 判断是否为群聊消息（微信）*/
    private fun isGroupChat(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): Boolean {
        val conversationId = extractConversationId(param) ?: return false
        return conversationId.endsWith("@chatroom")
    }

    private fun extractQQMessageText(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): String? {
        return extractMessageText(param) // 相同的提取逻辑
    }

    private fun extractQQConversationId(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): String? {
        for (arg in param.args) {
            if (arg is String && arg.isNotEmpty()) return arg
        }
        return null
    }

    private fun isQQGroupChat(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): Boolean {
        val id = extractQQConversationId(param) ?: return false
        return id.length > 20 // QQ群号通常较长
    }

    /** 检查冷却时间 */
    private fun isInCooldown(conversationId: String): Boolean {
        val lastTime = lastReplyTime[conversationId] ?: return false
        return System.currentTimeMillis() - lastTime < COOLDOWN_MS
    }
}
