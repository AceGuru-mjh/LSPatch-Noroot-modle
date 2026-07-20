package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 朋友圈管理Hook类
 *
 * 功能：
 * 1. 防删朋友圈：好友删除动态后，本地缓存不会立即消失
 * 2. 朋友圈界面精简：过滤营销/代购动态，只显示原创内容
 * 3. 朋友圈广告过滤（独立于AdBlockHook的补充逻辑）
 *
 * 实现原理：
 * - 微信朋友圈数据存储在本地SQLite数据库
 * - 当好友删除动态时，服务端发送删除指令，客户端从UI移除并更新数据库
 * - Hook删除指令的处理方法，阻止本地数据被移除
 * - Hook朋友圈列表加载的过滤逻辑，去除营销类内容
 */
object MomentHook {

    // ===== 微信朋友圈Hook =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载朋友圈管理Hook（微信）")

        // 1. 防删朋友圈
        if (ConfigManager.isEnabled(ConfigManager.KEY_ANTI_DELETE_MOMENT)) {
            hookMomentDelete(lpparam)
        }

        // 2. 朋友圈界面精简
        if (ConfigManager.isEnabled(ConfigManager.KEY_SIMPLIFY_MOMENTS)) {
            hookMomentSimplify(lpparam)
        }

        // 3. 朋友圈数据列表加载优化
        hookMomentDataLoad(lpparam)
    }

    // ================================================================
    //  防删朋友圈：阻止本地删除操作
    //  关键：Hook SnsInfoStorage（朋友圈数据存储）的删除方法
    // ================================================================
    private fun hookMomentDelete(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 朋友圈数据存储核心类（多版本候选）
        val storageClasses = listOf(
            "com.tencent.mm.plugin.sns.storage.SnsInfoStorage",
            "com.tencent.mm.plugin.sns.model.SnsCore",
            "com.tencent.mm.plugin.sns.data.SnsInfoStorageLogic",
            "com.tencent.mm.plugin.sns.model.SnsInfo",
        )

        for (className in storageClasses) {
            val storageClass = HookHelper.findClassSafe(lpparam, className)
            if (storageClass == null) continue

            // Hook delete方法（多种可能的命名）
            val deleteMethods = listOf(
                "delete", "deleteSnsInfo", "deleteItem",
                "removeSnsInfo", "del", "deleteBySnsId",
                "a", "b", "c" // 混淆后方法名
            )

            for (methodName in deleteMethods) {
                HookHelper.hookAllMethodsSafe(storageClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 判断是否为服务端下发的删除指令
                                        val isServerDelete = checkIsServerDelete(param)
                                        if (isServerDelete) {
                                            HookHelper.log("[防删朋友圈] 阻止服务端删除指令")
                                            param.result = null // 关键：阻止删除操作
                                        } else {
                                            // 本地手动删除允许正常执行
                                            HookHelper.logD("[防删朋友圈] 允许本地删除")
                                        }
                }
            })
            }

            // Hook update方法：某些版本通过更新状态标记删除
            HookHelper.hookAllMethodsSafe(storageClass, "updateSnsInfo", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 检查是否正在更新为"已删除"状态
                                    try {
                                        val deleteFlag = tryGetDeleteFlag(param)
                                        if (deleteFlag) {
                                            HookHelper.log("[防删朋友圈] 阻止状态更新为已删除")
                                            param.result = null
                                        }
                                    } catch (e: Exception) {
                                        // 忽略
                                    }
                }
            })
        }
    }

    // ================================================================
    //  朋友圈界面精简：过滤营销/代购内容
    //  策略：
    //  1. Hook朋友圈列表适配器，过滤特定类型的动态
    //  2. 过滤含营销关键词的内容
    //  3. 过滤非原创转发内容（可选）
    // ================================================================
    private fun hookMomentSimplify(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 朋友圈时间线适配器
        val adapterClass = HookHelper.findClassSafe(
            lpparam,
            "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
            "com.tencent.mm.plugin.sns.ui.adapter.SnsTimeLineAdapter",
            "com.tencent.mm.plugin.sns.ui.SnsTimeLineBaseAdapter",
        )

        if (adapterClass == null) return

        // Hook getView：检查每条动态的内容
        HookHelper.hookAllMethodsSafe(adapterClass, "getView", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val position = param.args.getOrNull(0) as? Int ?: 0
                    
                                try {
                                    val item = param.thisObject.javaClass
                                        .getMethod("getItem", Int::class.javaPrimitiveType)
                                        .invoke(param.thisObject, position)
                    
                                    if (item != null) {
                                        // 判断内容是否营销性质
                                        if (isMarketingContent(item)) {
                                            HookHelper.logD("[朋友圈精简] 过滤营销动态 position=$position")
                                            // 返回极小的View使其不显示
                                            val parent = param.args.getOrNull(2) as? android.view.ViewGroup
                                            val ctx = parent?.context
                                            if (ctx != null) {
                                                val emptyView = android.view.View(ctx)
                                                emptyView.layoutParams =
                                                    android.view.ViewGroup.LayoutParams(0, 0)
                                                param.result = emptyView
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 兼容性处理
                                }
                }
            })

        // Hook朋友圈文本内容设置：检查并过滤营销文案
        hookMomentTextFilter(lpparam)
    }

    /**
     * 判断某条动态是否为营销/代购性质内容
     * 通过检查文本内容关键词和动态类型来判断
     */
    private fun isMarketingContent(item: Any): Boolean {
        try {
            // 获取动态文本内容
            val contentField = item.javaClass.getField("field_desc")
            val content = contentField.get(item) as? String ?: ""

            // 营销关键词列表
            val marketingKeywords = listOf(
                "正品代购", "海外代购", "直邮", "现货",
                "加微信", "加V", "加v信", "扫码",
                "招代理", "招兼职", "日赚", "轻松赚钱",
                "拼单", "团购", "优惠券", "满减",
                "微商", "爆款", "限量", "手慢无",
                "仅售", "白菜价", "跳楼价", "清仓",
                "zfb", "支付bao", "转账",
                "好评", "五星好评", "返现",
            )

            // 检查文本内容是否包含营销关键词
            if (content.isNotEmpty()) {
                val lowerContent = content.lowercase()
                for (keyword in marketingKeywords) {
                    if (lowerContent.contains(keyword.lowercase())) {
                        return true
                    }
                }
            }

            // 检查动态类型：某些类型的动态更可能是营销内容
            val typeField = item.javaClass.getField("field_type")
            val type = typeField.get(item) as? Int ?: 0
            // type=5可能是广告或营销动态标识（不同微信版本可能不同）
            if (type == 5 || type == 15 || type == 20) {
                return true
            }

        } catch (e: Exception) {
            // 无法判断时默认不过滤
        }
        return false
    }

    // ================================================================
    //  额外：朋友圈文本内容过滤Hook
    // ================================================================
    private fun hookMomentTextFilter(lpparam: XC_LoadPackage.LoadPackageParam) {
        val textViewClasses = listOf(
            "com.tencent.mm.plugin.sns.ui.SnsTextView",
            "com.tencent.mm.plugin.sns.ui.widget.SnsCommentTextView",
            "com.tencent.mm.ui.widget.MMTextView",
        )

        for (className in textViewClasses) {
            val textClass = HookHelper.findClassSafe(lpparam, className)
            if (textClass == null) continue

            HookHelper.hookAllMethodsSafe(textClass, "setText", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val text = param.args.getOrNull(0)?.toString() ?: return
                                    // 将营销文本替换为提示信息
                                    if (isMarketingText(text)) {
                                        HookHelper.logD("[朋友圈精简] 替换营销文案")
                                        param.args[0] = "[此内容已被过滤·疑似营销推广]"
                                    }
                }
            })
        }
    }

    /** 检查文本是否为营销性质 */
    private fun isMarketingText(text: String): Boolean {
        if (text.isEmpty()) return false
        val lowerText = text.lowercase()
        val keywords = listOf(
            "加微信", "扫码", "代购", "招代理", "日赚",
            "微商", "拼单", "优惠券", "团购",
        )
        return keywords.count { lowerText.contains(it) } >= 2
    }

    // ================================================================
    //  朋友圈数据加载优化
    //  目的：让防删后的数据能正确显示，避免因本地缓存缺失导致空白
    // ================================================================
    private fun hookMomentDataLoad(lpparam: XC_LoadPackage.LoadPackageParam) {
        val dataLoaderClass = HookHelper.findClassSafe(
            lpparam,
            "com.tencent.mm.plugin.sns.model.SnsDataLoader",
            "com.tencent.mm.plugin.sns.data.SnsDataFetcher",
        )

        if (dataLoaderClass != null) {
            HookHelper.hookAllMethodsSafe(dataLoaderClass, "onDataLoaded", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[朋友圈] 数据加载完成")
                                    // 数据加载后的处理（例如清理过期缓存）
                }
            })
        }
    }

    // ================================================================
    //  辅助工具方法
    // ================================================================

    /** 检查是否为服务端下发的删除指令 */
    private fun checkIsServerDelete(param: XC_MethodHook.MethodHookParam): Boolean {
        try {
            // 通过调用栈判断调用来源
            val stackTrace = Thread.currentThread().stackTrace
            for (element in stackTrace) {
                val className = element.className.lowercase()
                // 如果调用来自网络/同步相关的类，认为是服务端删除
                if (className.contains("sync") ||
                    className.contains("remote") ||
                    className.contains("server") ||
                    className.contains("net") ||
                    className.contains("cgi") ||
                    className.contains("protobuf")
                ) {
                    return true
                }
            }

            // 通过参数判断：服务端删除通常携带snsId
            for (arg in param.args) {
                if (arg is String && arg.length in 10..30 && arg.all { it.isDigit() }) {
                    return true
                }
                if (arg is Long && arg > 1000000L) {
                    return true
                }
            }
        } catch (e: Exception) {
            // 异常时保守处理：不阻止
        }
        return false
    }

    /** 尝试获取删除标记 */
    private fun tryGetDeleteFlag(param: XC_MethodHook.MethodHookParam): Boolean {
        try {
            for (arg in param.args) {
                if (arg is Boolean && arg) return true
                if (arg is Int && arg == 1) return true
            }
            // 检查对象中的删除标记字段
            val deleteFlag = param.thisObject.javaClass
                .getField("deleteFlag")
                .get(param.thisObject) as? Boolean ?: false
            return deleteFlag
        } catch (e: Exception) {
            return false
        }
    }
}
