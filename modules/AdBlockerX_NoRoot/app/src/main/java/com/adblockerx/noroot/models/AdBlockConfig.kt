package com.adblockerx.noroot.models

/**
 * AdBlockerX NoRoot 拦截配置
 *
 * 仅作用于当前 APP 进程内的网络请求/视图层级，
 * 不修改任何系统文件、不影响其他 APP 和系统 DNS。
 *
 * 字段分组：
 *  - masterEnabled: 总开关
 *  - 应用层基础：WebView/OkHttp/URLConnection/Hosts/AdView
 *  - 应用层实验性：Tracker/Cookie/Redirect/Intent 拦截
 *  - 黑名单参数：内置 + 自定义
 */
data class AdBlockConfig(
    // ===== 总开关 =====
    var masterEnabled: Boolean = true,

    // ===== 应用层基础 Hook =====
    /** WebView 广告拦截 (shouldOverrideUrlLoading / shouldInterceptRequest / loadUrl / onPageFinished 注入 CSS/JS) */
    var webviewAdEnabled: Boolean = true,
    /** OkHttp 请求拦截 (RealCall.execute / Interceptor.Chain.proceed) */
    var okHttpAdEnabled: Boolean = true,
    /** URLConnection / HttpURLConnection 拦截 */
    var urlConnectionAdEnabled: Boolean = true,
    /** 内存 hosts 黑名单查询（启用内置+自定义黑名单） */
    var hostsFilterEnabled: Boolean = true,
    /** 广告 SDK View 隐藏 (GDT/百度/字节/小米 等广告 SDK) */
    var adViewHideEnabled: Boolean = true,

    // ===== 应用层实验性 =====
    /** 追踪 SDK 拦截（Hook umeng/talkingdata/flurry 等上报方法） */
    var trackerBlockEnabled: Boolean = false,
    /** Cookie 清理（Hook CookieManager.getCookie 返回前清理追踪cookie） */
    var cookieCleanEnabled: Boolean = false,
    /** 重定向拦截（Hook WebViewClient.shouldOverrideUrlLoading 拦截广告跳转深链） */
    var redirectBlockEnabled: Boolean = false,
    /** Intent 拦截（Hook startActivity 拦截广告 Intent 跳转） */
    var intentInterceptorEnabled: Boolean = false,

    // ===== v1.0.6 新增（对标 AdClose） =====
    /** 截图录屏限制移除（Hook FLAG_SECURE 让目标APP可截图录屏） */
    var screenshotUnlockEnabled: Boolean = false,
    /** 摇一摇广告跳转禁用（Hook SensorManager 加速度计，阻止摇一摇触发广告） */
    var shakeAdBlockEnabled: Boolean = false,
    /** VPN/代理检测绕过（Hook NetworkInfo/ConnectivityManager 返回非VPN） */
    var vpnDetectBypassEnabled: Boolean = false,

    // ===== 参数 =====
    /** WebView 注入 JS 隐藏广告元素（实验性，可能影响页面正常显示） */
    var injectJsEnabled: Boolean = false,
    /** 是否启用内置广告域名黑名单 */
    var builtinBlocklistEnabled: Boolean = true,
    /** 用户自定义广告域名（一行一个，支持子域名 endsWith 匹配） */
    var customBlocklist: List<String> = emptyList(),
    /** 是否启用日志输出 */
    var logEnabled: Boolean = true,

    /** 拦截命中计数（仅用于显示） */
    var blockedCount: Long = 0L,

    var lastModified: Long = System.currentTimeMillis()
)
