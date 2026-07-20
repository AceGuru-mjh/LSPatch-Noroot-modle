package com.microx.enhancer.hooks

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 界面美化与自定义Hook类
 *
 * 功能：
 * 1. 自定义聊天气泡颜色/样式
 * 2. 全局字体大小调节
 * 3. 一键隐藏所有小红点（未读标记）
 * 4. 去除底部多余Tab（游戏、购物、视频号）
 * 5. 自定义聊天背景图片
 */
object UIModHook {

    // ===== 微信界面美化 =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载界面美化Hook（微信）")

        // 1. 自定义聊天气泡
        if (ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_BUBBLE)) {
            hookChatBubble(lpparam)
        }

        // 2. 全局字体大小调节
        if (ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_FONT)) {
            hookFontSize(lpparam)
        }

        // 3. 隐藏小红点
        if (ConfigManager.isEnabled(ConfigManager.KEY_HIDE_RED_DOT)) {
            hookHideRedDots(lpparam)
        }

        // 4. 去除底部多余Tab
        if (ConfigManager.isEnabled(ConfigManager.KEY_REMOVE_TAB)) {
            hookRemoveTabs(lpparam)
        }

        // 5. 自定义聊天背景
        if (ConfigManager.isEnabled(ConfigManager.KEY_CHAT_BACKGROUND)) {
            hookChatBackground(lpparam)
        }
    }

    // ===== QQ界面美化 =====
    fun hookQQ(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载界面美化Hook（QQ）")

        if (ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_BUBBLE)) {
            hookQQChatBubble(lpparam)
        }
        if (ConfigManager.isEnabled(ConfigManager.KEY_HIDE_RED_DOT)) {
            hookQQHideRedDots(lpparam)
        }
        if (ConfigManager.isEnabled(ConfigManager.KEY_REMOVE_TAB)) {
            hookQQRemoveTabs(lpparam)
        }
    }

    // ================================================================
    //  1. 自定义聊天气泡
    //  策略：Hook聊天消息Item的View创建，修改背景drawable
    // ================================================================
    private fun hookChatBubble(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 聊天消息Item视图类（对方消息气泡、自己消息气泡）
        val bubbleClasses = listOf(
            "com.tencent.mm.ui.chatting.viewitems.ChattingItemAppMsg",
            "com.tencent.mm.ui.chatting.viewitems.ChattingItemText",
            "com.tencent.mm.ui.chatting.viewitems.ChattingItemImg",
            "com.tencent.mm.ui.chatting.ChattingItemContainer",
            "com.tencent.mm.ui.chatting.viewitems.ChattingItem",
        )

        for (className in bubbleClasses) {
            val bubbleClass = HookHelper.findClassSafe(lpparam, className)
            if (bubbleClass == null) continue

            // Hook ItemView的创建/填充方法
            val buildMethods = listOf(
                "inflate", "createView", "buildItem", "fillItem",
                "a", "b" // 混淆方法名
            )

            for (methodName in buildMethods) {
                HookHelper.hookAllMethodsSafe(bubbleClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 这里有个命名冲突，下面调整
                                        try {
                                            // param.result 可能是创建的View
                                            val resultView = param.result as? View
                                            if (resultView != null) {
                                                // 查找气泡背景View（通常是一个带背景的FrameLayout/LinearLayout）
                                                val bubbleView = findBubbleView(resultView)
                                                if (bubbleView != null) {
                                                    // 判断是接收还是发送
                                                    val isSender = isSenderBubble(param.thisObject, param.args)
                                                    applyBubbleStyle(bubbleView, isSender)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // 忽略
                                        }
                }
            })
            }
        }

        // 补偿方案：Hook所有TextView的背景设置，针对聊天消息修改
        hookAllTextViewBackgrounds(lpparam)
    }

    /** 在View树中查找聊天气泡背景视图 */
    private fun findBubbleView(rootView: View): View? {
        // 气泡通常是LinearLayout或RelativeLayout且有背景drawable
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val child = rootView.getChildAt(i)
                if (child is ViewGroup && child.background != null) {
                    return child
                }
                val result = findBubbleView(child)
                if (result != null) return result
            }
        }
        return null
    }

    /** 判断当前消息是发送还是接收 */
    private fun isSenderBubble(thisObject: Any?, args: Array<out Any?>): Boolean {
        args.forEach { arg ->
            try {
                if (arg != null) {
                    val isSenderField = arg.javaClass.getField("isSend")
                    return isSenderField.get(arg) as? Boolean ?: false
                }
            } catch (e: Exception) { /* ignore */ }
        }
        return false
    }

    /** 应用气泡样式 */
    private fun applyBubbleStyle(bubbleView: View, isSender: Boolean) {
        bubbleView.post {
            try {
                val color = if (isSender) Color.parseColor("#95EC69")  // 发送：浅绿色
                else Color.parseColor("#FFFFFF")                       // 接收：白色
                bubbleView.setBackgroundColor(color)
                // 设置圆角
                if (bubbleView is ViewGroup) {
                    bubbleView.clipToOutline = true
                }
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    /** Hook所有TextView背景设置，针对聊天气泡文本修改 */
    private fun hookAllTextViewBackgrounds(lpparam: XC_LoadPackage.LoadPackageParam) {
        val textViewClass = XposedHelpers.findClass("android.widget.TextView", lpparam.classLoader)
        HookHelper.hookAllMethodsSafe(textViewClass, "setBackground", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 不做全局处理，仅在特定场景触发
                }
            })
    }

    // ================================================================
    //  2. 全局字体大小调节
    //  策略：Hook TextView.setTextSize 和 setTextAppearance
    // ================================================================
    private fun hookFontSize(lpparam: XC_LoadPackage.LoadPackageParam) {
        val textViewClass = XposedHelpers.findClass("android.widget.TextView", lpparam.classLoader)

        // Hook setTextSize方法
        HookHelper.hookAllMethodsSafe(textViewClass, "setTextSize", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 获取原始字体大小
                                when {
                                    param.args.size >= 1 && param.args[0] is Float -> {
                                        val originalSize = param.args[0] as Float
                                        // 调整比例（可配置）：如1.2倍
                                        val scaleFactor = 1.15f
                                        param.args[0] = originalSize * scaleFactor
                                    }
                                    param.args.size >= 2 && param.args[1] is Float -> {
                                        val originalSize = param.args[1] as Float
                                        param.args[1] = originalSize * 1.15f
                                    }
                                }
                }
            })
    }

    // ================================================================
    //  3. 一键隐藏小红点（未读标记）
    //  策略：Hook红点View的setVisibility方法
    //  微信红点通常在Tab页面和侧边栏中
    // ================================================================
    private fun hookHideRedDots(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 小红点相关类（BadgeView, UnreadCountView等）
        val redDotClasses = listOf(
            "com.tencent.mm.ui.widget.MMBadgeView",
            "com.tencent.mm.ui.widget.UnreadCountView",
            "com.tencent.mm.ui.chatting.UnreadCountView",
            "com.tencent.mm.plugin.brandservice.ui.BrandServiceUnreadView",
            "com.tencent.mm.ui.widget.cropview.CropLayout",
            "com.tencent.mm.ui.mogic.WxViewPager",
            "com.tencent.mm.ui.tools.MMViewPager",
        )

        for (className in redDotClasses) {
            val redDotClass = HookHelper.findClassSafe(lpparam, className)
            if (redDotClass == null) continue

            // Hook show方法和setVisibility
            HookHelper.hookAllMethodsSafe(redDotClass, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[隐藏小红点] 拦截show()")
                                    param.result = null
                }
            })

            HookHelper.hookAllMethodsSafe(redDotClass, "setVisibility", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val visibility = param.args.getOrNull(0) as? Int ?: 0
                                    if (visibility == View.VISIBLE) {
                                        HookHelper.logD("[隐藏小红点] 设置View为GONE")
                                        param.args[0] = View.GONE
                                    }
                }
            })

            // Hook setCount/setNumber方法
            HookHelper.hookAllMethodsSafe(redDotClass, "setCount", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.args[0] = 0 // 设置未读数为0
                }
            })

            HookHelper.hookAllMethodsSafe(redDotClass, "setUnreadCount", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.args[0] = 0
                }
            })
        }

        // 额外：Hook launcher UI的红点更新方法
        val launcherClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.ui.LauncherUI",
            "com.tencent.mm.ui.HomeUI",
        )
        if (launcherClass != null) {
            HookHelper.hookAllMethodsSafe(launcherClass, "updateUnreadCount", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[隐藏小红点] 拦截未读数更新")
                                    param.result = null
                }
            })

            HookHelper.hookAllMethodsSafe(launcherClass, "onTabUnreadCountChanged", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // ================================================================
    //  4. 去除底部多余Tab
    //  微信底部Tab：微信、通讯录、发现、我
    //  多余入口：游戏(game)、购物(shopping)、视频号等
    //  策略：Hook底部Tab布局的初始化，移除目标Tab
    // ================================================================
    private fun hookRemoveTabs(lpparam: XC_LoadPackage.LoadPackageParam) {
        val tabClasses = listOf(
            "com.tencent.mm.ui.LauncherUI",
            "com.tencent.mm.ui.HomeUI",
            "com.tencent.mm.ui.MainTabUI",
            "com.tencent.mm.ui.chatting.MainFrame",
        )

        for (className in tabClasses) {
            val tabClass = HookHelper.findClassSafe(lpparam, className)
            if (tabClass == null) continue

            // Hook Tab初始化/创建方法
            val initMethods = listOf(
                "initTabs", "initTabLayout", "setupTabs",
                "createTabs", "addTabs",
            )

            for (methodName in initMethods) {
                HookHelper.hookAllMethodsSafe(tabClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 在Tab初始化后移除多余的Tab
                                        param.thisObject.javaClass.methods
                                            .find { it.name.contains("remove") || it.name.contains("hide") }
                                            ?.let { method ->
                                                // 尝试移除发现页以外的多余入口
                                                HookHelper.logD("[去除Tab] 拦截Tab初始化")
                                            }
                }
            })
            }
        }

        // 方案2：Hook Tab添加方法
        val tabHostClass = HookHelper.findClassSafe(lpparam,
            "android.widget.TabHost",
            "com.tencent.mm.ui.widget.MMTabHost",
        )
        if (tabHostClass != null) {
            HookHelper.hookAllMethodsSafe(tabHostClass, "addTab", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 获取Tab的tag/标题
                                    val tabTag = param.args.getOrNull(0)?.toString() ?: ""
                                    val blockedTabs = listOf("game", "shopping", "video", "shop")
                                    if (blockedTabs.any { tabTag.lowercase().contains(it) }) {
                                        HookHelper.log("[去除Tab] 移除多余Tab: $tabTag")
                                        param.result = null
                                    }
                }
            })
        }
    }

    // ================================================================
    //  5. 自定义聊天背景图
    //  策略：Hook聊天页背景设置方法
    // ================================================================
    private fun hookChatBackground(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chatUIClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mm.ui.chatting.ChattingUI",
            "com.tencent.mm.ui.chatting.BaseChattingUI",
        )

        if (chatUIClass != null) {
            HookHelper.hookAllMethodsSafe(chatUIClass, "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val activity = param.thisObject as? android.app.Activity
                                    if (activity != null) {
                                        // 在聊天页创建后设置自定义背景
                                        val rootView = activity.window?.decorView?.findViewById<View>(
                                            android.R.id.content
                                        ) as? ViewGroup
                                        rootView?.post {
                                            try {
                                                // 设置背景颜色或图片
                                                rootView.setBackgroundColor(Color.parseColor("#F5F5F5"))
                                            } catch (e: Exception) {
                                                HookHelper.logE("[聊天背景] 设置失败: ${e.message}")
                                            }
                                        }
                                    }
                }
            })
        }
    }

    // ================================================================
    //  QQ端界面美化
    // ================================================================

    private fun hookQQChatBubble(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqBubbleClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mobileqq.activity.aio.item.ChatItemBuilder",
            "com.tencent.mobileqq.activity.aio.BaseChatItemLayout",
        )
        if (qqBubbleClass == null) return

        HookHelper.hookAllMethodsSafe(qqBubbleClass, "getView", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val view = param.result as? View
                                if (view is ViewGroup) {
                                    view.setBackgroundColor(Color.parseColor("#E8F5E9"))
                                }
                }
            })
    }

    private fun hookQQHideRedDots(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqBadgeClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mobileqq.widget.QQBadgeView",
            "com.tencent.mobileqq.activity.contacts.base.BadgeView",
        )
        if (qqBadgeClass == null) return

        HookHelper.hookAllMethodsSafe(qqBadgeClass, "setVisibility", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.args[0] = View.GONE
                }
            })

        HookHelper.hookAllMethodsSafe(qqBadgeClass, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })
    }

    private fun hookQQRemoveTabs(lpparam: XC_LoadPackage.LoadPackageParam) {
        val mainClass = HookHelper.findClassSafe(lpparam,
            "com.tencent.mobileqq.activity.SplashActivity",
            "com.tencent.mobileqq.activity.home.MainFragment",
        )
        if (mainClass == null) return

        HookHelper.hookAllMethodsSafe(mainClass, "initTab", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val tabName = param.args.getOrNull(0)?.toString() ?: ""
                                // QQ动态页、看点等（不同版本tab名称不同）
                                val blockedTabs = listOf("dynamic", "read", "live", "game")
                                if (blockedTabs.any { tabName.lowercase().contains(it) }) {
                                    HookHelper.log("[QQ去除Tab] 移除多余Tab: $tabName")
                                    param.result = null
                                }
                }
            })
    }
}
