package com.adblockerx.noroot.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.ui.components.FeatureCard
import com.adblockerx.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: AdBlockConfig, onConfigChange: (AdBlockConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础拦截功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "WebView 广告拦截",
            "shouldOverrideUrlLoading / shouldInterceptRequest 404 / loadUrl 拦截 / 注入 JS",
            cfg.webviewAdEnabled,
            { val nc = cfg.copy(webviewAdEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "OkHttp 请求拦截",
            "RealCall.execute/enqueue + Interceptor.Chain.proceed 多候选类名容错",
            cfg.okHttpAdEnabled,
            { val nc = cfg.copy(okHttpAdEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "URLConnection 拦截",
            "URL.openConnection 抛 IOException / HttpURLConnection 返回 404 / Https 同理",
            cfg.urlConnectionAdEnabled,
            { val nc = cfg.copy(urlConnectionAdEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内存 Hosts 黑名单",
            "内置广告域名黑名单 + 用户自定义，子域名+包含匹配",
            cfg.hostsFilterEnabled,
            { val nc = cfg.copy(hostsFilterEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "广告 SDK View 隐藏",
            "Hook 21 个广告 SDK 的 View 类，构造后强制 GONE + 拦截 VISIBLE",
            cfg.adViewHideEnabled,
            { val nc = cfg.copy(adViewHideEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性拦截", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "追踪 SDK 拦截",
            "Hook Umeng/TalkingData/Flurry/Bugly/BaiduMtj 等上报方法直接 return",
            cfg.trackerBlockEnabled,
            { val nc = cfg.copy(trackerBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Cookie 清理",
            "Hook CookieManager.getCookie 返回前过滤 _ga/_gid/IDE 等追踪 Cookie",
            cfg.cookieCleanEnabled,
            { val nc = cfg.copy(cookieCleanEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "重定向拦截",
            "Hook WebViewClient.shouldOverrideUrlLoading 拦截广告跳转深链 / click 关键字",
            cfg.redirectBlockEnabled,
            { val nc = cfg.copy(redirectBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Intent 拦截",
            "Hook startActivity / startActivityForResult 拦截广告 Intent 跳转",
            cfg.intentInterceptorEnabled,
            { val nc = cfg.copy(intentInterceptorEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(16.dp))
        Text("v1.0.6 新增（对标 AdClose）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "截图录屏限制移除", "Hook FLAG_SECURE，让目标APP可截图录屏",
            cfg.screenshotUnlockEnabled,
            { val nc = cfg.copy(screenshotUnlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "摇一摇广告禁用", "拦截加速度计事件，阻止摇一摇触发广告跳转",
            cfg.shakeAdBlockEnabled,
            { val nc = cfg.copy(shakeAdBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "VPN/代理检测绕过", "Hook NetworkInfo 返回非VPN，绕过APP的VPN检测",
            cfg.vpnDetectBypassEnabled,
            { val nc = cfg.copy(vpnDetectBypassEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("高级选项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FeatureCard(
            "WebView 注入 JS",
            "onPageFinished 后注入 CSS 隐藏广告 DOM（可能影响页面正常显示）",
            cfg.injectJsEnabled,
            { val nc = cfg.copy(injectJsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))
        FeatureCard(
            "内置广告黑名单",
            "启用内置 90 条广告域名（关闭后仅匹配自定义黑名单）",
            cfg.builtinBlocklistEnabled,
            { val nc = cfg.copy(builtinBlocklistEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(40.dp))
    }
}
