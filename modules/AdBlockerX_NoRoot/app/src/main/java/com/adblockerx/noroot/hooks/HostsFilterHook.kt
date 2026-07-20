package com.adblockerx.noroot.hooks

import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.AdBlockList
import com.adblockerx.noroot.utils.ConfigManager
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内存 hosts 过滤器（不写任何文件）
 *
 * 设计目标：
 *  - 维护一份广告域名黑名单（内置 90 条 + 用户自定义）
 *  - 提供统一查询接口 [isBlocked] 供 WebViewAdHook / OkHttpAdHook / URLConnectionAdHook / AdViewHideHook 调用
 *  - 所有数据仅存于内存，绝不写入 /system/etc/hosts、不修改系统 DNS、不设置 Private DNS
 *
 * 边界声明（NoRoot 版）：
 *  1. 仅拦截本 APP 进程内的网络请求
 *  2. 不影响其他 APP 和系统
 *  3. 不持久化到任何文件
 */
object HostsFilterHook {

    @Volatile
    private var currentConfig: AdBlockConfig = AdBlockConfig()

    /** 初始化：加载当前配置 */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        currentConfig = cfg
        LogX.i("HostsFilter 启用：内置黑名单=${cfg.builtinBlocklistEnabled}，自定义条数=${cfg.customBlocklist.size}")
        LogX.i("内置黑名单条数：${AdBlockList.BUILTIN_AD_DOMAINS.size}")
    }

    /** 刷新配置（UI 修改后调用） */
    fun refreshConfig(cfg: AdBlockConfig) {
        currentConfig = cfg
    }

    /**
     * 判断 host 是否被拦截
     *  - 优先匹配广告黑名单
     *  - 子域名匹配 + 包含匹配（保守策略，宁可误伤也不漏）
     */
    fun isBlocked(host: String?): Boolean {
        if (!currentConfig.hostsFilterEnabled) return false
        if (host.isNullOrBlank()) return false
        val hit = AdBlockList.isBlocked(
            host = host,
            builtinEnabled = currentConfig.builtinBlocklistEnabled,
            customList = currentConfig.customBlocklist
        )
        if (hit) {
            try { ConfigManager.incrementBlockedCount(1) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            LogX.d("[拦截命中] host=$host")
        }
        return hit
    }

    /** 从 URL 提取 host 后判断 */
    fun isUrlBlocked(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = AdBlockList.extractHost(url) ?: return false
        return isBlocked(host)
    }

    /** 返回当前生效的所有拦截 host 列表（内置+自定义） */
    fun currentBlockedHosts(): List<String> {
        val list = mutableListOf<String>()
        if (currentConfig.builtinBlocklistEnabled) list.addAll(AdBlockList.BUILTIN_AD_DOMAINS)
        list.addAll(currentConfig.customBlocklist.filter { it.isNotBlank() && !it.startsWith("#") })
        return list
    }
}
