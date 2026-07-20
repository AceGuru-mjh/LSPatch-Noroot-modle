package com.adblockerx.noroot.utils

/**
 * 内置广告域名黑名单
 *
 * 设计原则：
 *  - 全部存储在内存中，绝不写入 /system/etc/hosts 等系统文件
 *  - 仅作为本 APP 进程内 Hook 的查询源
 *  - 子域名匹配：判断时使用 host.endsWith(domain) 或 host.contains(domain)
 *  - 用户可在 APP 配置中追加自定义域名
 *
 * 涵盖：Google Ads / 腾讯 GDT / 字节穿山甲 / 百度联盟 /
 *      阿里妈妈 / 快手广告 / 网易广告 / 360联盟 / Mobtech / umeng / etc.
 */
object AdBlockList {

    /**
     * 内置广告/追踪域名（约 90 条）
     * 注意：列表仅用于广告拦截，不含任何合法域名
     */
    val BUILTIN_AD_DOMAINS: List<String> = listOf(
        // ===== Google / DoubleClick =====
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "adwords.google.com",
        "adsense.google.com",
        "googletagservices.com",
        "adsystem.google.com",
        "partnerad.l.doubleclick.net",

        // ===== 腾讯 GDT / AMS =====
        "pgdt.ugdtimg.com",
        "t.gdt.qq.com",
        "e.qq.com",
        "ad.qq.com",
        "ams.qq.com",
        "gdt.qq.com",
        "qzonestyle.gtimg.cn",
        "mi.gdt.qq.com",

        // ===== 字节跳动 / 穿山甲 / 巨量引擎 =====
        "ad.toutiao.com",
        "pdp.toutiao.com",
        "is.snssdk.com",
        "pg.snssdk.com",
        "ad.toutiao.com.cn",
        "adx.toutiao.com",
        "toblog.ctobsnssdk.com",
        "mssdk.bytedance.com",
        "sf3-fe-tos.pglstatp-tpl.pglstatp.com",
        "log.snssdk.com",
        "init.snssdk.com",

        // ===== 百度联盟 / Mobads =====
        "cpro.baidu.com",
        "mobads.baidu.com",
        "pos.baidu.com",
        "baidumobads.baidu.com",
        "als.baidu.com",
        "e.baidu.com",
        "hmma.baidu.com",
        "duclick.baidu.com",

        // ===== 阿里妈妈 / 淘宝联盟 =====
        "amdc.alibaba.com",
        "acs4baichuan.m.taobao.com",
        "adash-c.ut.taobao.com",
        "adashx.ut.taobao.com",
        "aenbaichuan.com",
        "baichuan.taobao.com",

        // ===== 快手广告 =====
        "ad.kuaishou.com",
        "yt-adp.nsnssdk.com",
        "ssp.ksadx.com",

        // ===== 网易广告 =====
        "ad.bn.netease.com",
        "adwallet.netease.com",
        "g1.163.com",
        "adstest.163.com",

        // ===== 360 / 神马 / 搜狗 =====
        "360.cn",
        "shuzilm.cn",
        "a.shenma.cn",
        "ideasad.com",

        // ===== Mobtech / Umeng / TalkingData =====
        "api.mob.com",
        "ad.talkingdata.com",
        "ulogs.umeng.com",
        "ulogs.umengcloud.com",
        "plbslog.umeng.com",
        "alogs.umeng.com",

        // ===== 其他常见广告/追踪 =====
        "adsame.cn",
        "tanx.com",
        "sax.sina.cn",
        "sax.n.sina.com.cn",
        "r.dmp.cn",
        "admaster.com",
        "mediav.com",
        "miaozhen.com",
        "irs01.com",
        "adcdn.com",
        "moatads.com",
        "rubiconproject.com",
        "pubmatic.com",
        "criteo.com",
        "applovin.com",
        "chartboost.com",
        "inmobi.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "vungle.com",
        "tapjoy.com",
        "admob.com",
        "mm.adtech.com",
        "adtech.com",

        // ===== 追踪 SDK 域名 =====
        "tracking.miui.com",
        "data.adsrvr.org",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "snap.licdn.com",
        "px.ads.linkedin.com",
        "tags.tiqcdn.com",
        "collect.tencent.com",

        // ===== 网盟/RTB 补充 =====
        "adsymptotic.com",
        "yieldlab.net",
        "smartadserver.com",
        "openx.net",
        "3lift.com",
        "bidswitch.net",
        "contextweb.com",
        "quantserve.com",
        "scorecardresearch.com"
    )

    /**
     * 判断 host 是否命中黑名单
     *  - 内置黑名单 + 用户自定义黑名单
     *  - 支持子域名匹配：sub.doubleclick.net 命中 doubleclick.net
     *  - 支持包含匹配：xxx.doubleclick.net.cn 也会命中（更宽松，减少漏拦）
     */
    fun isBlocked(
        host: String?,
        builtinEnabled: Boolean,
        customList: List<String>
    ): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trim()

        if (builtinEnabled) {
            for (domain in BUILTIN_AD_DOMAINS) {
                val d = domain.lowercase()
                if (h == d || h.endsWith(".$d") || h.contains(d)) {
                    return true
                }
            }
        }

        for (domain in customList) {
            val d = domain.lowercase().trim()
            if (d.isBlank()) continue
            if (h == d || h.endsWith(".$d") || h.contains(d)) {
                return true
            }
        }
        return false
    }

    /** 从 URL 字符串中提取 host */
    fun extractHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val u = url.trim()
            val start = when {
                u.startsWith("https://", true) -> 8
                u.startsWith("http://", true) -> 7
                else -> 0
            }
            val rest = u.substring(start)
            val end = rest.indexOfFirst { it == '/' || it == ':' || it == '?' || it == '#' }
            if (end < 0) rest else rest.substring(0, end)
        } catch (e: Exception) {
            null
        }
    }
}
