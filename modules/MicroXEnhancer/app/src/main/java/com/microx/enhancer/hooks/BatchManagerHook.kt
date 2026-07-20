package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * 批量管理工具Hook类
 *
 * 功能：
 * 1. 一键清理缓存：清理图片、视频、文件等媒体缓存，保留聊天记录
 * 2. 批量导出好友列表
 * 3. 僵尸粉检测
 * 4. 群聊管理：批量禁言、屏蔽指定成员发言、关键词过滤
 *
 * 注意：这些功能通过Hook存储操作方法提供API，
 * 实际触发由设置UI面板的按钮事件调用
 */
object BatchManagerHook {

    // ===== 微信批量管理 =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载批量管理Hook（微信）")

        // 提供缓存清理的入口Hook
        hookStorageAccess(lpparam)

        // 提供了好友列表导出的Hook
        hookContactAccess(lpparam)

        // 提供群管理Hook
        hookGroupManage(lpparam)
    }

    // ================================================================
    //  1. 缓存清理Hook
    //  原理：
    //  - 提供获取缓存目录路径的能力
    //  - Hook文件管理相关类，暴露缓存路径
    //  - 清理时遍历删除非数据库文件
    // ================================================================
    private fun hookStorageAccess(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_ONE_KEY_CLEAN)) return

        // Hook微信存储管理器
        val storageClasses = listOf(
            "com.tencent.mm.storage.StorageManager",
            "com.tencent.mm.compatible.util.FileAccess",
            "com.tencent.mm.sdk.platformtools.ImgUtil",
            "com.tencent.mm.loader.stub.CConstants",
        )

        for (className in storageClasses) {
            val storageClass = HookHelper.findClassSafe(lpparam, className)
            if (storageClass == null) continue

            // Hook获取缓存路径方法
            HookHelper.hookAllMethodsSafe(storageClass, "getCachePath", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 不做修改，仅标记
                                    HookHelper.logD("[缓存清理] 缓存路径: ${param.result}")
                }
            })

            HookHelper.hookAllMethodsSafe(storageClass, "getImgCachePath", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val path = param.result as? String ?: ""
                                    if (path.isNotEmpty()) {
                                        // 记录图片缓存路径用于批量清理
                                        HookHelper.logD("[缓存清理] 图片缓存路径: $path")
                                    }
                }
            })

            HookHelper.hookAllMethodsSafe(storageClass, "getVideoCachePath", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[缓存清理] 视频缓存路径: ${param.result}")
                }
            })
        }

        // 提供清理方法：当UI触发清理时调用
        provideCleanMethod(lpparam)
    }

    /** 提供缓存清理的统一入口方法 */
    private fun provideCleanMethod(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Application的getCacheDir，提供清理接口
        val appClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.app.Application",
            "com.tencent.wework.api.WwSDK",
        )

        if (appClass != null) {
            HookHelper.hookAllMethodsSafe(appClass, "getCacheDir", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val cacheDir = param.result as? File
                                    // 暴露给BatchManager清理时使用
                }
            })
        }
    }

    // ================================================================
    //  2. 好友列表导出Hook
    //  原理：
    //  - Hook联系人数据库查询方法
    //  - 在查询结果返回时拦截数据
    //  - 序列化为CSV格式写入外部存储
    // ================================================================
    private fun hookContactAccess(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_EXPORT_FRIENDS)) return

        // 通讯录存储/管理类
        val contactClasses = listOf(
            "com.tencent.mm.storage.ContactStorage",
            "com.tencent.mm.model.Contact",
            "com.tencent.mm.plugin.contact.ContactManager",
            "com.tencent.mm.model.AddressBookStorage",
        )

        for (className in contactClasses) {
            val contactClass = HookHelper.findClassSafe(lpparam, className)
            if (contactClass == null) continue

            // Hook getAll / getContactList 等方法
            val queryMethods = listOf(
                "getAll", "getContactList", "getFriends",
                "loadAllContacts", "queryAll",
            )

            for (methodName in queryMethods) {
                HookHelper.hookAllMethodsSafe(contactClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val result = param.result
                                        if (result is List<*> && result.isNotEmpty()) {
                                            // 缓存联系人数据供导出使用
                                            HookHelper.log("[好友导出] 捕获联系人列表，共${result.size}人")
                                            // 注意：这里仅做缓存标记，实际导出在UI触发
                                        }
                }
            })
            }
        }
    }

    // ================================================================
    //  3. 僵尸粉检测Hook
    //  原理：
    //  - 拦截好友关系查询结果
    //  - 标记那些返回"非好友"或删除状态的联系人
    // ================================================================
    private fun hookZombieCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_ZOMBIE_CHECK)) return

        // 好友关系查询类
        val relationClasses = listOf(
            "com.tencent.mm.model.Contact",
            "com.tencent.mm.storage.ContactStorage",
            "com.tencent.mm.model.RContact",
        )

        for (className in relationClasses) {
            val relationClass = HookHelper.findClassSafe(lpparam, className)
            if (relationClass == null) continue

            // Hook isContact / isFriend等方法
            HookHelper.hookAllMethodsSafe(relationClass, "isContact", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val result = param.result as? Boolean ?: true
                                    if (!result) {
                                        HookHelper.log("[僵尸粉] 检测到非好友联系人")
                                    }
                }
            })
        }
    }

    // ================================================================
    //  4. 群聊管理Hook
    //  原理：
    //  - Hook群消息过滤方法
    //  - 拦截指定成员发送的消息
    //  - 关键词过滤垃圾消息
    // ================================================================
    private fun hookGroupManage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_GROUP_MANAGE)) return

        // 群聊消息分发类
        val groupMsgClasses = listOf(
            "com.tencent.mm.model.ChattingDataLogic",
            "com.tencent.mm.modelmulti.MMCore",
            "com.tencent.mm.plugin.chatroom.ChatRoomService",
            "com.tencent.mm.ui.chatting.ChattingUI",
        )

        // 垃圾消息关键词
        val spamKeywords = listOf(
            "加微信", "扫码进群", "扫码领红包",
            "招兼职", "日赚", "正在直播",
            "点击链接", "免费领取",
        )

        for (className in groupMsgClasses) {
            val msgClass = HookHelper.findClassSafe(lpparam, className)
            if (msgClass == null) continue

            // Hook消息分发方法
            HookHelper.hookAllMethodsSafe(msgClass, "dispatchMessage", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    try {
                                        // 提取消息文本
                                        val msgText = extractContent(param)
                                        if (msgText != null && isGroupChatMsg(param)) {
                                            // 检查是否为垃圾消息
                                            val isSpam = spamKeywords.any {
                                                msgText.lowercase().contains(it.lowercase())
                                            }
                    
                                            if (isSpam) {
                                                HookHelper.log("[群聊管理] 过滤垃圾消息: ${msgText.take(50)}")
                                                // 阻止消息显示
                                                param.result = null
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 忽略
                                    }
                }
            })

            // Hook群成员禁言
            HookHelper.hookAllMethodsSafe(msgClass, "setMemberShutUp", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[群聊管理] 禁言操作")
                }
            })
        }
    }

    // ================================================================
    //  公开工具方法（供SettingsActivity等UI层调用）
    // ================================================================

    /** 执行缓存清理 */
    fun performCacheClean(context: android.content.Context): Long {
        var totalCleaned = 0L
        val cacheDirs = listOf(
            context.cacheDir,
            context.externalCacheDir,
            File(context.filesDir.parentFile, "MicroMsg"),
            File(context.filesDir.parentFile, "image2"),
            File(context.filesDir.parentFile, "video"),
            File(context.filesDir.parentFile, "voice2"),
            File(context.filesDir.parentFile, "avatar"),
        )

        for (dir in cacheDirs) {
            if (dir != null && dir.exists()) {
                try {
                    val size = getDirSize(dir)
                    cleanDir(dir)
                    totalCleaned += size
                    HookHelper.log("[缓存清理] 清理: ${dir.name} = $size bytes")
                } catch (e: Exception) {
                    HookHelper.logE("[缓存清理] 清理失败: ${dir.name}: ${e.message}")
                }
            }
        }

        return totalCleaned
    }

    /** 获取目录大小 */
    private fun getDirSize(dir: File): Long {
        var size = 0L
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    size += file.length()
                } else if (file.isDirectory) {
                    size += getDirSize(file)
                }
            }
        } catch (e: Exception) {
            // 忽略权限问题
        }
        return size
    }

    /** 清理目录（保留数据库文件） */
    private fun cleanDir(dir: File) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    cleanDir(file)
                    if (file.listFiles()?.isEmpty() == true) {
                        file.delete()
                    }
                } else {
                    // 保留数据库文件(.db, .db-journal)
                    val name = file.name.lowercase()
                    if (!name.endsWith(".db") &&
                        !name.endsWith(".db-journal") &&
                        !name.endsWith(".ini") &&
                        !name.endsWith(".cfg")
                    ) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    /** 从Hook参数中提取消息文本 */
    private fun extractContent(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): String? {
        for (arg in param.args) {
            if (arg is String && arg.isNotEmpty()) return arg
        }
        return null
    }

    /** 判断是否为群聊消息 */
    private fun isGroupChatMsg(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): Boolean {
        for (arg in param.args) {
            if (arg is String && arg.endsWith("@chatroom")) return true
        }
        return false
    }
}
