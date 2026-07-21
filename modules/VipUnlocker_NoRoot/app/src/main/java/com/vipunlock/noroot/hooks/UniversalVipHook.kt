package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogStore
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通用 VIP 解锁 Hook
 *
 * 分两层：
 *  1. applyForXxx: 各 APP 专属候选类名表（酷狗/酷我/优酷/腾讯视频/喜马拉雅/今日头条/知乎/百度网盘/WPS/微信读书）
 *  2. applyForCommon: 跨 APP 通用方法名 Hook（isVip / isPremium / getVipLevel 等通用命名）
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 VIP 状态查询方法
 *  - 服务端鉴权不绕过
 *  - 通用 Hook 范围广，可能误伤非VIP相关方法，已通过方法名白名单收敛
 */
object UniversalVipHook {

    // ===== 各 APP 专属候选类名 =====

    private val KUGOU_CANDIDATES = listOf(
        "com.kugou.android.app.vip.VipManager",
        "com.kugou.android.app.user.UserVipInfo",
        "com.kugou.framework.vip.VipUtil",
        "com.kugou.android.app.vip.KugouVipBusiness"
    )

    private val KUWO_CANDIDATES = listOf(
        "com.kuwo.player.app.vip.VipManager",
        "com.kuwo.player.app.user.UserVipInfo",
        "com.kuwo.player.business.vip.VipUtil",
        "com.kuwo.player.mvip.MVipManager"
    )

    private val YOUKU_CANDIDATES = listOf(
        "com.youku.phone.vip.VipManager",
        "com.youku.phone.vip.YoukuVipUtil",
        "com.youku.service.vip.VipBusiness",
        "com.youku.user.vip.UserVipInfo"
    )

    private val TENCENT_VIDEO_CANDIDATES = listOf(
        "com.tencent.qqlive.module.vip.VipManager",
        "com.tencent.qqlive.business.vip.VipUtil",
        "com.tencent.qqlive.onactivity.vip.UserVipInfo",
        "com.tencent.qqlive.dao.vip.VipBusiness"
    )

    private val XIMALAYA_CANDIDATES = listOf(
        "com.ximalaya.ting.android.host.manager.vip.VipManager",
        "com.ximalaya.ting.android.host.vip.XmlyVipUtil",
        "com.ximalaya.ting.android.vip.business.VipBusiness",
        "com.ximalaya.ting.android.user.vip.UserVipInfo"
    )

    private val TOUTIAO_CANDIDATES = listOf(
        "com.ss.android.article.news.feature.vip.VipManager",
        "com.ss.android.article.news.user.UserVipInfo",
        "com.ss.android.account.vip.VipUtil",
        "com.bytedance.article.news.vip.VipBusiness"
    )

    private val ZHIHU_CANDIDATES = listOf(
        "com.zhihu.android.app.vip.ZhihuVipManager",
        "com.zhihu.android.vip.ZhihuVip",
        "com.zhihu.android.app.user.UserVipInfo",
        "com.zhihu.android.vip.business.SaltVipManager"
    )

    private val BAIDU_NETDISK_CANDIDATES = listOf(
        "com.baidu.netdisk.vip.VipManager",
        "com.baidu.netdisk.business.vip.VipUtil",
        "com.baidu.netdisk.user.UserVipInfo",
        "com.baidu.netdisk.vip.SuperVipManager"
    )

    private val WPS_CANDIDATES = listOf(
        "com.wps.moffice.documentmanager.vip.VipManager",
        "com.wps.moffice.business.vip.WpsVipUtil",
        "com.wps.moffice.user.UserVipInfo",
        "com.wps.moffice_engine.vip.SuperVipManager"
    )

    private val WEREAD_CANDIDATES = listOf(
        "com.tencent.weread.user.vip.VipManager",
        "com.tencent.weread.business.vip.WereadVipUtil",
        "com.tencent.weread.user.UserVipInfo",
        "com.tencent.weread.vip.InfiniteCardManager"
    )

    // ===== 通用 VIP 方法名（跨APP通用） =====
    private val COMMON_BOOLEAN_METHODS = listOf(
        "isVip", "isPremium", "isPro", "isProUser", "isPaidUser", "isSubscriber",
        "isVipUser", "isVipValid", "isVipAvailable", "isVipMember", "isMember",
        "isSubscribed", "isPaid", "hasVip", "hasPremium", "isEligible"
    )
    private val COMMON_INT_METHODS = listOf(
        "getVipLevel", "getVipType", "getMemberLevel", "getMemberType",
        "getUserLevel", "getPremiumLevel", "getSubscriptionLevel"
    )

    // ===== 各 APP 入口 =====

    fun applyForKugou(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.kugouVipEnabled) return
        LogX.i("酷狗VIP解锁启动（仅应用层）")
        try { LogStore.add("unlocked", "VIP解锁已启用") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }
        hookCandidates(lpparam, KUGOU_CANDIDATES, "Kugou")
    }

    fun applyForKuwo(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.kuwoVipEnabled) return
        LogX.i("酷我VIP解锁启动（仅应用层）")
        hookCandidates(lpparam, KUWO_CANDIDATES, "Kuwo")
    }

    fun applyForYouku(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.youkuVipEnabled) return
        LogX.i("优酷VIP解锁启动（仅应用层）")
        hookCandidates(lpparam, YOUKU_CANDIDATES, "Youku")
    }

    fun applyForTencentVideo(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.tencentVideoVipEnabled) return
        LogX.i("腾讯视频VIP解锁启动（仅应用层）")
        hookCandidates(lpparam, TENCENT_VIDEO_CANDIDATES, "TencentVideo")
    }

    fun applyForXimalaya(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.ximalayaVipEnabled) return
        LogX.i("喜马拉雅VIP解锁启动（仅应用层）")
        hookCandidates(lpparam, XIMALAYA_CANDIDATES, "Ximalaya")
    }

    fun applyForToutiao(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.toutiaoVipEnabled) return
        LogX.i("今日头条关键功能解锁启动（仅应用层）")
        hookCandidates(lpparam, TOUTIAO_CANDIDATES, "Toutiao")
    }

    fun applyForZhihu(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.zhihuVipEnabled) return
        LogX.i("知乎盐选会员解锁启动（仅应用层）")
        hookCandidates(lpparam, ZHIHU_CANDIDATES, "Zhihu")
    }

    fun applyForBaiduNetdisk(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.baiduNetdiskVipEnabled) return
        LogX.i("百度网盘SVIP解锁启动（仅应用层）")
        hookCandidates(lpparam, BAIDU_NETDISK_CANDIDATES, "BaiduNetdisk")
    }

    fun applyForWps(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.wpsVipEnabled) return
        LogX.i("WPS超级会员解锁启动（仅应用层）")
        hookCandidates(lpparam, WPS_CANDIDATES, "WPS")
    }

    fun applyForWeread(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.wereadVipEnabled) return
        LogX.i("微信读书无限卡解锁启动（仅应用层）")
        hookCandidates(lpparam, WEREAD_CANDIDATES, "Weread")
    }

    /**
     * 通用 VIP 尝试：跨 APP 通用方法名 Hook（不依赖具体类名）
     *
     * 通过 XposedBridge.hookAllMethod-like 行为：扫描已加载的类（受限于 Xposed API
     * 限制，本实现采用 hookMethodsByNames 在所有候选类中查找匹配方法）。
     *
     * 由于不依赖具体类名，需要 Hook 通用基类/接口：
     *  - java.lang.Object 不可 Hook（会触发系统警告）
     *  - 改为 Hook 所有候选类的同名方法（在已知 APP 类路径下扫描）
     *
     * 简化实现：复用 COMMON_BOOLEAN_METHODS / COMMON_INT_METHODS，
     * 通过遍历每个 APP 的候选类列表统一处理。
     */
    fun applyForCommon(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.universalVipTryEnabled) return
        LogX.i("通用VIP尝试启动（仅应用层，方法名 Hook）")

        // 汇总所有候选类
        val allCandidates = KUGOU_CANDIDATES + KUWO_CANDIDATES + YOUKU_CANDIDATES +
            TENCENT_VIDEO_CANDIDATES + XIMALAYA_CANDIDATES + TOUTIAO_CANDIDATES +
            ZHIHU_CANDIDATES + BAIDU_NETDISK_CANDIDATES + WPS_CANDIDATES + WEREAD_CANDIDATES

        var hookedAny = false
        for (clsName in allCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in COMMON_BOOLEAN_METHODS) {
                if (tryHookBoolean(cls, clsName, m)) hookedAny = true
            }
            for (m in COMMON_INT_METHODS) {
                if (tryHookIntReturning(cls, clsName, m, 9)) hookedAny = true
            }
        }
        // Hook 通用枚举/常量类（部分APP用 enum 表示VIP状态）
        hookCommonVipEnum(lpparam)

        if (hookedAny) {
            LogX.i("通用VIP方法名Hook完成")
        } else {
            LogX.w("通用VIP未匹配到任何候选类（当前APP可能不在作用域内）")
        }
    }

    /** 对一组候选类名执行通用 Hook：boolean->true / int->9 */
    private fun hookCandidates(
        lpparam: XC_LoadPackage.LoadPackageParam,
        candidates: List<String>,
        tag: String
    ) {
        var hookedAny = false
        for (clsName in candidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in COMMON_BOOLEAN_METHODS) {
                if (tryHookBoolean(cls, clsName, m)) hookedAny = true
            }
            for (m in COMMON_INT_METHODS) {
                if (tryHookIntReturning(cls, clsName, m, 9)) hookedAny = true
            }
            // APP 专属方法名（首字母小写）
            val lowerTag = tag.replaceFirstChar { it.lowercase() }
            tryHookBoolean(cls, clsName, "is${tag}Vip")
            tryHookBoolean(cls, clsName, "is${tag}Pro")
            tryHookBoolean(cls, clsName, "is${lowerTag}Vip")
            tryHookIntReturning(cls, clsName, "get${tag}VipType", 9)
            tryHookIntReturning(cls, clsName, "get${tag}VipLevel", 9)
        }
        if (!hookedAny) {
            LogX.w("$tag VIP状态类未找到候选类，可能版本已变更")
        }
    }

    /**
     * Hook 通用 VIP 枚举类
     * 候选：com.xxx.VipType / com.xxx.MemberType 等
     * 通用做法：Hook valueOf 返回 VIP 级别
     */
    private fun hookCommonVipEnum(lpparam: XC_LoadPackage.LoadPackageParam) {
        val enumCandidates = listOf(
            "com.tencent.qqlive.module.vip.VipType",
            "com.baidu.netdisk.vip.VipType",
            "com.wps.moffice.business.vip.WpsVipType",
            "com.zhihu.android.vip.ZhihuVipType"
        )
        for (clsName in enumCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                // Hook 构造或 ordinal 不通用，这里 Hook 静态获取方法
                XposedHelpers.findAndHookMethod(cls, "isVip",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) { p.result = true }
                    })
                LogX.hookSuccess(clsName, "isVip")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun tryHookBoolean(cls: Class<*>, clsName: String, method: String): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = true }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }

    private fun tryHookIntReturning(cls: Class<*>, clsName: String, method: String, value: Int): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = value }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }
}
