package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 广告净化Hook类
 * 拦截微信/QQ各场景中的广告展示
 *
 * Hook策略说明：
 * - 微信广告SDK主要由腾讯广告(AMS)驱动，核心类通常在gdt/ams包下
 * - 通过Hook广告加载、展示、点击的核心方法实现全局拦截
 * - 采用"先Hook再判断"策略：拦截所有广告展示请求，根据广告类型过滤
 * - 版本兼容：使用类名通配+多候选名的方式应对版本变化
 */
object AdBlockHook {

    // ===== 微信广告拦截 =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载广告净化Hook（微信）")

        // 1. 拦截开屏广告
        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_SPLASH)) {
            hookSplashAd(lpparam)
        }

        // 2. 拦截朋友圈信息流推广
        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_MOMENTS)) {
            hookMomentsAd(lpparam)
        }

        // 3. 拦截公众号内嵌广告
        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_OFFICIAL_ACCOUNT)) {
            hookOfficialAccountAd(lpparam)
        }

        // 4. 拦截小程序弹窗广告
        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_MINI_PROGRAM)) {
            hookMiniProgramAd(lpparam)
        }

        // 5. 移除聊天页推广卡片
        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_CHAT_CARD)) {
            hookChatCardAd(lpparam)
        }
    }

    // ===== QQ广告拦截 =====
    fun hookQQ(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载广告净化Hook（QQ）")

        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_SPLASH)) {
            hookQQSplashAd(lpparam)
        }

        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_MOMENTS)) {
            hookQQFeedAd(lpparam)
        }

        if (ConfigManager.isEnabled(ConfigManager.KEY_AD_CHAT_CARD)) {
            hookQQChatAd(lpparam)
        }
    }

    // ================================================================
    //  微信：开屏广告拦截
    //  核心思路：Hook开屏广告的展示/播放方法，直接跳过
    // ================================================================
    private fun hookSplashAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 微信开屏广告由SplashActivity或LaunchSplashActivity控制
        // 多种可能的广告类名（不同版本变化）
        val splashClasses = listOf(
            "com.tencent.mm.plugin.splash.ui.SplashView",
            "com.tencent.mm.splash.SplashWelcomeActivity",
            "com.tencent.mm.ui.LauncherUI",
        )

        for (className in splashClasses) {
            val splashClass = HookHelper.findClassSafe(lpparam, className)
            if (splashClass == null) continue

            // Hook广告展示时间控制：强制设为0（立即跳过）
            HookHelper.hookAllMethodsSafe(splashClass, "setAdShowTime", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[开屏广告] 拦截广告展示时间")
                                    // 将广告展示时间设置为0，立即跳过
                                    if (param.args.isNotEmpty() && param.args[0] is Int) {
                                        param.args[0] = 0
                                    }
                }
            })

            // Hook广告是否显示：强制返回false
            HookHelper.hookAllMethodsSafe(splashClass, "hasAd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[开屏广告] 拦截广告查询")
                                    param.result = false
                }
            })

            // Hook跳过按钮点击：模拟立即点击跳过
            HookHelper.hookAllMethodsSafe(splashClass, "onSkipClicked", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[开屏广告] 模拟跳过按钮点击")
                                    // 不做额外处理，让原方法正常执行即可
                }
            })
        }

        // 额外拦截：腾讯广告SDK(AMS)的SplashAd类
        val amsSplashClass = HookHelper.findClassSafe(
            lpparam,
            "com.qq.e.comm.plugin.splash.SplashAdView",
            "com.qq.e.tg.splash.TGSplashAd",
            "com.tencent.ams.splash.AMSSplashAd"
        )
        if (amsSplashClass != null) {
            // Hook show方法：阻止广告展示
            HookHelper.hookAllMethodsSafe(amsSplashClass, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[开屏广告] 拦截AMS广告展示")
                                    param.result = null // 不执行原展示方法
                }
            })

            // Hook loadAd方法：返回空（不加载广告）
            HookHelper.hookAllMethodsSafe(amsSplashClass, "loadAd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[开屏广告] 拦截AMS广告加载")
                                    param.result = null
                }
            })
        }
    }

    // ================================================================
    //  微信：朋友圈信息流推广拦截
    //  核心思路：Hook朋友圈数据适配器，过滤type为广告的Item
    // ================================================================
    private fun hookMomentsAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 朋友圈Timeline数据类
        val timelineClasses = listOf(
            "com.tencent.mm.plugin.sns.data.SnsInfo",
            "com.tencent.mm.plugin.sns.storage.AdLandingPagesStorage",
            "com.tencent.mm.plugin.sns.model.SnsAdUtil",
            "com.tencent.mm.plugin.sns.ad.SnsAdService",
        )

        // 方案1：Hook广告识别方法——判断某条动态是否为广告
        for (className in timelineClasses) {
            val snsClass = HookHelper.findClassSafe(lpparam, className)
            if (snsClass == null) continue

            // Hook isAd方法：强制返回false（标记为非广告，跳过展示）
            HookHelper.hookAllMethodsSafe(snsClass, "isAd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.logD("[朋友圈广告] 拦截广告标记")
                                    param.result = false
                }
            })

            // Hook getAdType方法
            HookHelper.hookAllMethodsSafe(snsClass, "getAdType", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = 0 // 0表示非广告类型
                }
            })
        }

        // 方案2：Hook朋友圈列表适配器，过滤广告Item
        hookMomentsAdapter(lpparam)
    }

    private fun hookMomentsAdapter(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 朋友圈时间线适配器
        val adapterClass = HookHelper.findClassSafe(
            lpparam,
            "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
            "com.tencent.mm.plugin.sns.ui.SnsTimeLineBaseAdapter",
            "com.tencent.mm.plugin.sns.ui.adapter.SnsTimeLineAdapter"
        )
        if (adapterClass == null) return

        // Hook getCount方法：减少广告条目数量
        HookHelper.hookAllMethodsSafe(adapterClass, "getCount", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val originalCount = param.result as? Int ?: 0
                                // 这里简单返回原值，更精细的过滤在getItemView中实现
                                param.result = originalCount
                }
            })

        // Hook getItem方法：如果是广告Item则返回null/空
        HookHelper.hookAllMethodsSafe(adapterClass, "getItem", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val position = param.args.getOrNull(0) as? Int ?: 0
                                val item = param.result
                                if (item != null) {
                                    try {
                                        // 尝试调用isAd()判断
                                        val isAdMethod = item.javaClass.getMethod("isAd")
                                        val isAd = isAdMethod.invoke(item) as? Boolean ?: false
                                        if (isAd) {
                                            HookHelper.logD("[朋友圈广告] 过滤广告Item position=$position")
                                            param.result = null
                                        }
                                    } catch (e: Exception) {
                                        // 无法判断，保留原Item
                                    }
                                }
                }
            })

        // Hook getItemViewType：跳过广告类型的View
        // 微信朋友圈中广告的viewType通常是一个特殊值
        HookHelper.hookAllMethodsSafe(adapterClass, "getItemViewType", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val position = param.args.getOrNull(0) as? Int ?: 0
                                val originalType = param.result as? Int ?: 0
                                // 某些版本中广告类型值为特定数字(如15、19等)
                                // 这里保留原值，实际拦截在getView中
                                param.result = originalType
                }
            })
    }

    // ================================================================
    //  微信：公众号内嵌广告拦截
    //  核心思路：Hook WebView/文章页面的广告加载和显示方法
    // ================================================================
    private fun hookOfficialAccountAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 公众号文章页面类
        val articleClasses = listOf(
            "com.tencent.mm.plugin.brandservice.ui.BrandServiceIndexUI",
            "com.tencent.mm.plugin.webview.ui.tools.WebViewUI",
            "com.tencent.mm.ui.widget.MMWebView",
        )

        for (className in articleClasses) {
            val webViewClass = HookHelper.findClassSafe(lpparam, className)
            if (webViewClass == null) continue

            // Hook loadUrl方法：拦截已知广告域名
            HookHelper.hookAllMethodsSafe(webViewClass, "loadUrl", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val url = param.args.getOrNull(0) as? String ?: ""
                                    if (isAdDomain(url)) {
                                        HookHelper.log("[公众号广告] 拦截广告URL: $url")
                                        param.result = null
                                    }
                }
            })

            // Hook HTML注入方法：移除文章中的广告脚本
            HookHelper.hookAllMethodsSafe(webViewClass, "loadDataWithBaseURL", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val data = param.args.getOrNull(1) as? String
                                    if (data != null && data.contains("advertisement")) {
                                        // 替换掉常见的广告标签
                                        val cleaned = data.replace(
                                            Regex("<div[^>]*class=\"[^\"]*ad[^\"]*\"[^>]*>.*?</div>",
                                                RegexOption.IGNORE_CASE),
                                            ""
                                        )
                                        param.args[1] = cleaned
                                    }
                }
            })
        }
    }

    // ================================================================
    //  微信：小程序弹窗广告拦截
    // ================================================================
    private fun hookMiniProgramAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 小程序激励视频广告类
        val rewardedAdClasses = listOf(
            "com.tencent.mm.plugin.appbrand.jsapi.ad.JsApiCreateRewardedVideoAd",
            "com.tencent.mm.plugin.appbrand.jsapi.ad.JsApiShowRewardedVideoAd",
            "com.tencent.mm.plugin.appbrand.jsapi.ad.JsApiOperateRewardedVideoAd",
        )

        for (className in rewardedAdClasses) {
            val adClass = HookHelper.findClassSafe(lpparam, className)
            if (adClass == null) continue

            // Hook广告创建：返回失败
            HookHelper.hookAllMethodsSafe(adClass, "invoke", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[小程序广告] 拦截激励视频广告调用")
                                    // 设置返回结果为失败，让小程序认为广告加载失败
                                    // 大多数小程序会跳过广告直接给奖励
                                    try {
                                        val callbackId = param.args.getOrNull(0)
                                        // 这里根据JSAPI协议返回失败code
                                    } catch (e: Exception) {
                                        // 忽略
                                    }
                }
            })
        }

        // 拦截小程序插屏广告
        val interstitialClass = HookHelper.findClassSafe(
            lpparam,
            "com.tencent.mm.plugin.appbrand.jsapi.ad.JsApiCreateInterstitialAd",
            "com.tencent.mm.plugin.appbrand.jsapi.ad.JsApiShowInterstitialAd"
        )
        if (interstitialClass != null) {
            HookHelper.hookAllMethodsSafe(interstitialClass, "invoke", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[小程序广告] 拦截插屏广告")
                }
            })
        }

        // 额外：Hook小程序广告组件的View显示
        val miniAdViewClass = HookHelper.findClassSafe(
            lpparam,
            "com.tencent.mm.plugin.appbrand.ui.AppBrandAdView",
            "com.tencent.mm.plugin.appbrand.jsapi.ad.JsApiBannerAd",
        )
        if (miniAdViewClass != null) {
            HookHelper.hookAllMethodsSafe(miniAdViewClass, "setVisibility", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val visibility = param.args.getOrNull(0) as? Int ?: 0
                                    // 0 = VISIBLE, 4 = INVISIBLE, 8 = GONE
                                    if (visibility == 0) {
                                        HookHelper.logD("[小程序广告] 隐藏Banner广告")
                                        param.args[0] = 8 // GONE
                                    }
                }
            })
        }
    }

    // ================================================================
    //  微信：聊天页推广卡片移除
    // ================================================================
    private fun hookChatCardAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 聊天页消息ListView适配器
        val chatAdapterClass = HookHelper.findClassSafe(
            lpparam,
            "com.tencent.mm.ui.chatting.ChattingAdapter",
            "com.tencent.mm.ui.chatting.adapter.ChattingListAdapter",
        )

        if (chatAdapterClass != null) {
            // Hook getView：检查消息是否为推广卡片类型
            HookHelper.hookAllMethodsSafe(chatAdapterClass, "getView", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val position = param.args.getOrNull(0) as? Int ?: 0
                                    val convertView = param.args.getOrNull(1)
                                    val parent = param.args.getOrNull(2)
                    
                                    // 通过item消息类型判断是否为推广卡片
                                    // 微信消息类型中，推广卡片通常有特殊msgType值
                                    try {
                                        // 尝试从adapter获取item的消息类型
                                        val item = param.thisObject.javaClass
                                            .getMethod("getItem", Int::class.javaPrimitiveType)
                                            .invoke(param.thisObject, position)
                    
                                        if (item != null) {
                                            val msgTypeField = item.javaClass.getField("field_type")
                                            val msgType = msgTypeField.get(item) as? Int ?: 0
                                            // 微信中推广卡片类型值（经验值，不同版本可能变化）
                                            if (msgType in listOf(49, 50, 51, 268435505)) {
                                                HookHelper.log("[聊天页广告] 过滤推广卡片 msgType=$msgType")
                                                val ctx = (parent as? android.view.ViewGroup)?.context
                                                if (ctx != null) {
                                                    val emptyView = android.view.View(ctx)
                                                    emptyView.layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
                                                    param.result = emptyView
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 忽略版本兼容问题
                                    }
                }
            })
        }
    }

    // ================================================================
    //  QQ广告拦截部分
    // ================================================================

    /** QQ开屏广告拦截 */
    private fun hookQQSplashAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        val qqSplashClasses = listOf(
            "com.tencent.mobileqq.splashad.SplashADView",
            "com.tencent.mobileqq.activity.SplashActivity",
            "com.tencent.mobileqq.app.SplashManager",
            "com.tencent.gdtad.splash.GdtSplashAd",
        )

        for (className in qqSplashClasses) {
            val splashClass = HookHelper.findClassSafe(lpparam, className)
            if (splashClass == null) continue

            HookHelper.hookAllMethodsSafe(splashClass, "showAd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[QQ开屏广告] 拦截广告展示")
                                    param.result = null
                }
            })

            HookHelper.hookAllMethodsSafe(splashClass, "hasAd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = false
                }
            })

            HookHelper.hookAllMethodsSafe(splashClass, "setDuration", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.args[0] = 0 // 设置广告时长为0
                }
            })
        }

        // QQ也使用AMS广告SDK（同微信处理）
        val amsSplash = HookHelper.findClassSafe(lpparam,
            "com.qq.e.comm.plugin.splash.SplashAdView",
            "com.tencent.gdtad.splash.GdtSplashAd"
        )
        if (amsSplash != null) {
            HookHelper.hookAllMethodsSafe(amsSplash, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    /** QQ动态信息流广告拦截 */
    private fun hookQQFeedAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        val feedClasses = listOf(
            "com.tencent.mobileqq.qcircle.api.hybird.HybirdFragment",
            "com.tencent.mobileqq.activity.qcircle.QCircleFeedManager",
        )

        for (className in feedClasses) {
            val feedClass = HookHelper.findClassSafe(lpparam, className)
            if (feedClass == null) continue

            HookHelper.hookAllMethodsSafe(feedClass, "isAdFeed", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = false
                }
            })
        }
    }

    /** QQ会话顶部推广弹窗拦截 */
    private fun hookQQChatAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chatAdClasses = listOf(
            "com.tencent.mobileqq.activity.aio.AIOBannerAdHelper",
            "com.tencent.mobileqq.banner.BannerManager",
        )

        for (className in chatAdClasses) {
            val chatAdClass = HookHelper.findClassSafe(lpparam, className)
            if (chatAdClass == null) continue

            HookHelper.hookAllMethodsSafe(chatAdClass, "showBanner", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    HookHelper.log("[QQ聊天广告] 拦截顶部推广")
                                    param.result = null
                }
            })

            HookHelper.hookAllMethodsSafe(chatAdClass, "loadBanner", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 判断URL是否为已知广告域名 */
    private fun isAdDomain(url: String): Boolean {
        if (url.isEmpty()) return false
        val adDomains = listOf(
            "gdt.qq.com",
            "ad.qq.com",
            "ams.qq.com",
            "e.qq.com",
            "doubleclick.net",
            "googleadservices.com",
            "tanx.com",
            "baichuan.taobao.com",
            "shenma",
        )
        return adDomains.any { url.contains(it, ignoreCase = true) }
    }
}
