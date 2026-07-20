package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 隐私增强Hook类
 *
 * 功能：
 * 1. 隐藏"正在输入..."状态  —  不发送正在输入通知
 * 2. 隐藏已读状态          —  阅读消息后不返回已读回执
 * 3. 无限制转发            —  移除转发限制
 * 4. 强制原图发送          —  关闭图片压缩
 * 5. 无水印保存            —  移除下载图片/视频的水印
 *
 * 实现原理：
 * - 所有隐私功能通过Hook发送/接收相关方法实现
 * - 不涉及网络协议修改，仅在客户端进行拦截
 * - "正在输入"状态通过Hook输入框监听方法实现
 */
object PrivacyHook {

    // ===== 微信隐私增强 =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载隐私增强Hook（微信）")

        // 1. 隐藏正在输入状态
        if (ConfigManager.isEnabled(ConfigManager.KEY_HIDE_TYPING)) {
            hookHideTyping(lpparam)
        }

        // 2. 隐藏已读状态
        if (ConfigManager.isEnabled(ConfigManager.KEY_HIDE_READ_STATUS)) {
            hookHideReadStatus(lpparam)
        }

        // 3. 无限制转发
        if (ConfigManager.isEnabled(ConfigManager.KEY_UNLIMITED_FORWARD)) {
            hookUnlimitedForward(lpparam)
        }

        // 4. 强制原图发送
        if (ConfigManager.isEnabled(ConfigManager.KEY_FORCE_ORIGINAL)) {
            hookForceOriginalImage(lpparam)
        }

        // 5. 无水印保存
        if (ConfigManager.isEnabled(ConfigManager.KEY_NO_WATERMARK_SAVE)) {
            hookNoWatermarkSave(lpparam)
        }
    }

    // ===== QQ隐私增强 =====
    fun hookQQ(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载隐私增强Hook（QQ）")

        if (ConfigManager.isEnabled(ConfigManager.KEY_HIDE_TYPING)) {
            hookQQHideTyping(lpparam)
        }
        if (ConfigManager.isEnabled(ConfigManager.KEY_HIDE_READ_STATUS)) {
            hookQQHideReadStatus(lpparam)
        }
    }

    // ================================================================
    //  1. 隐藏"正在输入..."状态
    //  原理：
    //  - 微信/QQ在输入框内容变化时，向服务端发送"正在输入"通知
    //  - Hook发送通知的方法，阻止其执行
    //  - 关键类：输入框监听、网络发送、状态管理
    // ================================================================
    private fun hookHideTyping(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 输入状态通知相关类（多版本候选）
        val typingClasses = listOf(
            "com.tencent.mm.model.MsgInfoStorageLogic",
            "com.tencent.mm.model.ChattingDataLogic",
            "com.tencent.mm.modelmulti.NotifyManager",
            "com.tencent.mm.modelvoice.MediaRecorder", // 语音输入状态
            "com.tencent.mm.plugin.messenger.foundation.MessengerFilter",
        )

        for (className in typingClasses) {
            val typingClass = HookHelper.findClassSafe(lpparam, className)
            if (typingClass == null) continue

            // 候选方法名
            val methods = listOf(
                "sendTypingState", "notifyTyping", "onTyping",
                "sendInputStatus", "setTypingState", "reportTyping",
            )

            for (methodName in methods) {
                HookHelper.hookAllMethodsSafe(typingClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[隐私] 拦截正在输入状态发送")
                                        param.result = null  // 阻止发送
                }
            })
            }
        }

        // 方案2：Hook EditText文本变化监听
        hookEditTextListener(lpparam)
    }

    /** Hook EditText的InputConnection/TextWatcher来阻止发送输入状态 */
    private fun hookEditTextListener(lpparam: XC_LoadPackage.LoadPackageParam) {
        val editTextClasses = listOf(
            "com.tencent.mm.ui.widget.MMEditText",
            "com.tencent.mm.ui.chatting.ChatFooter",
            "com.tencent.mm.ui.chatting.SmileyPanel",
        )

        for (className in editTextClasses) {
            val editClass = HookHelper.findClassSafe(lpparam, className)
            if (editClass == null) continue

            // Hook TextWatcher的添加方法
            HookHelper.hookAllMethodsSafe(editClass, "addTextChangedListener", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[隐私] 拦截TextWatcher添加")
                                    // 不阻止添加，但清空textWatcher的实现
                                    val watcher = param.args.getOrNull(0)
                                    if (watcher != null) {
                                        try {
                                            // 用自定义的watcher包装原watcher
                                            // 在onTextChanged中拦截输入状态发送
                                            val watcherClass = watcher.javaClass
                                            watcherClass.getDeclaredField("mTypingNotify").let { field ->
                                                field.isAccessible = true
                                                field.set(watcher, false)
                                            }
                                        } catch (e: Exception) {
                                            // 兼容处理
                                        }
                                    }
                }
            })
        }
    }

    // ================================================================
    //  2. 隐藏已读状态
    //  原理：
    //  - 消息被查看后，客户端发送已读回执(ACK)
    //  - Hook已读回执的发送方法
    //  - 关键：reportRead / markRead / sendReadReceipt等方法
    // ================================================================
    private fun hookHideReadStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        val readStatusClasses = listOf(
            "com.tencent.mm.model.ChattingDataLogic",
            "com.tencent.mm.model.MsgInfoStorageLogic",
            "com.tencent.mm.modelmulti.MMCore",
            "com.tencent.mm.ui.chatting.ChattingUI",
            "com.tencent.mm.plugin.readerapp.ReaderAppService",
        )

        for (className in readStatusClasses) {
            val readClass = HookHelper.findClassSafe(lpparam, className)
            if (readClass == null) continue

            // 已读相关的所有方法
            val methods = listOf(
                "reportRead", "markRead", "sendReadReceipt",
                "onRead", "reportMsgRead", "setRead",
                "markAsRead", "sendAck",
            )

            for (methodName in methods) {
                HookHelper.hookAllMethodsSafe(readClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[隐私] 拦截已读回执发送: ${readClass?.name}.$methodName")
                                        param.result = null
                }
            })
            }
        }

        // 额外：Hook消息会话的未读数清零方法
        val conversationClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.storage.ConversationStorage",
            "com.tencent.mm.model.Conversation",
        )
        if (conversationClass != null) {
            HookHelper.hookAllMethodsSafe(conversationClass, "setUnreadCount", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[隐私] 拦截未读数清零（用于已读状态）")
                                    // 不阻止，但要同时防止已读回执发送
                                    // 这里允许清零本地未读数，但上面的Hook已经阻止了已读回执发送
                }
            })
        }
    }

    // ================================================================
    //  3. 无限制转发
    //  原理：
    //  - 微信对部分内容有限制：语音不能转发、大文件不能转发等
    //  - Hook转发限制检查方法，始终返回true（允许转发）
    // ================================================================
    private fun hookUnlimitedForward(lpparam: XC_LoadPackage.LoadPackageParam) {
        val forwardClasses = listOf(
            "com.tencent.mm.ui.transmit.MsgRetransmitUI",
            "com.tencent.mm.ui.transmit.SendAppMessageWrapperUI",
            "com.tencent.mm.ui.chatting.ForwardingUI",
            "com.tencent.mm.model.ForwardInfo",
            "com.tencent.mm.plugin.fav.ui.FavForwardHelper",
        )

        for (className in forwardClasses) {
            val forwardClass = HookHelper.findClassSafe(lpparam, className)
            if (forwardClass == null) continue

            // Hook转发限制判断方法
            HookHelper.hookAllMethodsSafe(forwardClass, "canForward", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[隐私] 拦截转发限制检查 -> 允许转发")
                                    param.result = true
                }
            })

            HookHelper.hookAllMethodsSafe(forwardClass, "isForwardable", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = true
                }
            })

            HookHelper.hookAllMethodsSafe(forwardClass, "checkForwardPermission", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = true
                }
            })

            // Hook语音消息是否可转发的判断
            HookHelper.hookAllMethodsSafe(forwardClass, "isVoiceMsg", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = false  // 将语音标记为非语音，允许转发
                }
            })

            // 收藏内容转发限制
            HookHelper.hookAllMethodsSafe(forwardClass, "isFavItemForwardable", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = true
                }
            })
        }
    }

    // ================================================================
    //  4. 强制原图发送
    //  原理：
    //  - 微信发送图片时默认压缩
    //  - Hook图片压缩参数设置，强制压缩率为100%（无损）
    // ================================================================
    private fun hookForceOriginalImage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val imageClasses = listOf(
            "com.tencent.mm.modelimage.ImgInfoStorage",
            "com.tencent.mm.modelimage.ImageService",
            "com.tencent.mm.plugin.image.ImageCompress",
            "com.tencent.mm.algorithm.UIN",
            "com.tencent.mm.sdk.platformtools.BitmapUtil",
            "com.tencent.mm.ui.chatting.SendImgTask",
        )

        for (className in imageClasses) {
            val imageClass = HookHelper.findClassSafe(lpparam, className)
            if (imageClass == null) continue

            // Hook压缩质量参数
            HookHelper.hookAllMethodsSafe(imageClass, "setQuality", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[隐私] 强制图片质量为100%")
                                    when {
                                        param.args.isNotEmpty() && param.args[0] is Int -> {
                                            param.args[0] = 100  // 最高质量
                                        }
                                        param.args.isNotEmpty() && param.args[0] is Float -> {
                                            param.args[0] = 1.0f  // 无损
                                        }
                                    }
                }
            })

            // Hook是否压缩的判断
            HookHelper.hookAllMethodsSafe(imageClass, "isCompress", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = false  // 不压缩
                }
            })

            HookHelper.hookAllMethodsSafe(imageClass, "shouldCompress", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = false
                }
            })

            // Hook图片最大尺寸限制
            HookHelper.hookAllMethodsSafe(imageClass, "setMaxWidth", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.args[0] = 99999  // 极大值，实际不限制
                }
            })

            HookHelper.hookAllMethodsSafe(imageClass, "setMaxHeight", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.args[0] = 99999
                }
            })

            // Hook压缩率设置
            val compressMethods = listOf(
                "setCompressRate", "setCompressQuality",
                "compressBitmap", "compressImage",
            )
            for (methodName in compressMethods) {
                HookHelper.hookAllMethodsSafe(imageClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[隐私] 拦截图片压缩")
                                        param.result = param.args.getOrNull(0) // 返回原图（不压缩）
                }
            })
            }
        }

        // 额外：Hook发送图片时的原图选项
        val sendClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.ui.chatting.SendImgTask",
            "com.tencent.mm.ui.transmit.MsgRetransmitUI",
        )
        if (sendClass != null) {
            HookHelper.hookAllMethodsSafe(sendClass, "isSendOriginal", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = true  // 始终使用原图
                }
            })
        }
    }

    // ================================================================
    //  5. 无水印保存图片/视频
    //  原理：
    //  - 某些来源的图片下载时会附加水印
    //  - Hook下载URL处理，移除水印参数
    // ================================================================
    private fun hookNoWatermarkSave(lpparam: XC_LoadPackage.LoadPackageParam) {
        val downloadClasses = listOf(
            "com.tencent.mm.modelvideo.VideoService",
            "com.tencent.mm.modelimage.ImageServiceDownloader",
            "com.tencent.mm.plugin.sns.model.SnsImageDownloader",
            "com.tencent.mm.plugin.sns.model.SnsVideoDownloader",
            "com.tencent.mm.modelcdn.CdnTransportService",
        )

        for (className in downloadClasses) {
            val downloadClass = HookHelper.findClassSafe(lpparam, className)
            if (downloadClass == null) continue

            // Hook下载URL生成方法
            HookHelper.hookAllMethodsSafe(downloadClass, "getDownloadUrl", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val url = param.result as? String ?: ""
                                    if (url.isNotEmpty()) {
                                        // 移除水印参数
                                        val cleanUrl = removeWatermarkParams(url)
                                        if (cleanUrl != url) {
                                            HookHelper.logD("[隐私] 移除水印URL参数: $url -> $cleanUrl")
                                            param.result = cleanUrl
                                        }
                                    }
                }
            })

            // Hook图片/视频保存路径设置
            HookHelper.hookAllMethodsSafe(downloadClass, "setSavePath", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 允许原始路径保存
                                    HookHelper.logD("[隐私] 无水印保存路径")
                }
            })
        }

        // Hook朋友圈图片下载
        val snsDownloaderClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.plugin.sns.model.SnsImageDownloaderLogic",
        )
        if (snsDownloaderClass != null) {
            HookHelper.hookAllMethodsSafe(snsDownloaderClass, "downloadImage", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 移除参数中的水印标记
                                    param.args.forEachIndexed { index, arg ->
                                        if (arg is Boolean && arg) {
                                            // waterMark参数
                                            param.args[index] = false
                                        }
                                    }
                }
            })
        }
    }

    // ================================================================
    //  QQ端隐私增强
    // ================================================================

    private fun hookQQHideTyping(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqTypingClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mobileqq.activity.aio.InputLinearLayout",
            "com.tencent.mobileqq.activity.aio.AIOInput",
        )
        if (qqTypingClass != null) {
            HookHelper.hookAllMethodsSafe(qqTypingClass, "sendTyping", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    private fun hookQQHideReadStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqReadClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mobileqq.app.MessageHandler",
            "com.tencent.imcore.message.QQMessageFacade",
        )
        if (qqReadClass != null) {
            HookHelper.hookAllMethodsSafe(qqReadClass, "sendMessageReadReport", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })

            HookHelper.hookAllMethodsSafe(qqReadClass, "sendReadConfirm", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 移除URL中的水印相关参数 */
    private fun removeWatermarkParams(url: String): String {
        if (url.isEmpty()) return url
        var result = url

        // 常见的水印参数名
        val watermarkParams = listOf(
            "watermark", "wm", "water_mark", "logo",
            "wm_type", "watermarkType", "needwm",
            "is_watermark", "tag", "from"
        )

        for (param in watermarkParams) {
            // 匹配 &param=xxx 或 ?param=xxx
            val regex = Regex("[&?]${Regex.escape(param)}=[^&]*", RegexOption.IGNORE_CASE)
            result = regex.replace(result, "")
        }

        // 确保第一个参数前有正确的?
        if (result.contains("?") && result.startsWith("&")) {
            result = "?" + result.substring(1)
        }

        return result
    }
}
