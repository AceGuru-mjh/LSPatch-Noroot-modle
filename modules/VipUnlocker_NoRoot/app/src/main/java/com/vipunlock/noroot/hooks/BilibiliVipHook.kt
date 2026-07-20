package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Bilibili 大会员解锁 Hook
 *
 * 候选 Hook 类：
 *  1. com.bilibili.lib.account.model.UserInfo
 *  2. com.bilibili.lib.account.info.VipInfo
 *  3. tv.danmaku.bili.ui.account.UserInfo
 *  4. com.bilibili.bangumi.data.user.BangumiUserVip
 *  5. com.bilibili.lib.community.auth.VipStatusManager
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内大会员状态查询方法
 *  - 4K/HDR/杜比等付费片源鉴权走服务端，本地 Hook 不生效
 */
object BilibiliVipHook {

    private val VIP_CLASS_CANDIDATES = listOf(
        "com.bilibili.lib.account.model.UserInfo",
        "com.bilibili.lib.account.info.VipInfo",
        "tv.danmaku.bili.ui.account.UserInfo",
        "com.bilibili.bangumi.data.user.BangumiUserVip",
        "com.bilibili.lib.community.auth.VipStatusManager",
        "com.bilibili.lib.account.model.BiliAccount"
    )

    private val VIP_METHODS = listOf(
        "isVip", "isAnnualVip", "isBigVip", "isVipValid", "isBiliVip",
        "hasVip", "isVipExpired", "isPremium"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.biliVipEnabled) return
        LogX.i("B站大会员解锁启动（仅应用层）")

        var hookedAny = false
        for (clsName in VIP_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in VIP_METHODS) {
                if (tryHookBoolean(cls, clsName, m)) hookedAny = true
            }
            // getVipType -> 2 (年度大会员)
            tryHookIntReturning(cls, clsName, "getVipType", 2)
            tryHookIntReturning(cls, clsName, "getVipStatus", 1)
            tryHookIntReturning(cls, clsName, "getVipRole", 2)
        }
        if (hookedAny) {
            LogX.i("B站大会员状态Hook完成")
        } else {
            LogX.w("B站大会员状态类未找到候选类，可能版本已变更")
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
