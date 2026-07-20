package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * QQ音乐 VIP 解锁 Hook（绿钻/豪华绿钻）
 *
 * 目标：让 QQ音乐 以为当前账号已开通豪华绿钻。
 *
 * 候选 Hook 类：
 *  1. com.tencent.qqmusiccommon.util.MusicUtils
 *  2. com.tencent.qqmusiccommon.business.vip.VipInfoManager
 *  3. com.tencent.wmusic.business.vip.VipManager
 *  4. com.tencent.qqmusiccommon.usertips.UserTipsManager
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Java 层 VIP 状态查询方法
 *  - 不绕过腾讯服务端校验，下载付费歌曲可能仍受限
 */
object QQMusicVipHook {

    private val VIP_CLASS_CANDIDATES = listOf(
        "com.tencent.qqmusiccommon.util.MusicUtils",
        "com.tencent.qqmusiccommon.business.vip.VipInfoManager",
        "com.tencent.wmusic.business.vip.VipManager",
        "com.tencent.qqmusiccommon.usertips.UserTipsManager",
        "com.tencent.qqmusiccommon.business.vip.VipUtil",
        "com.tencent.wmusic.common.util.MusicUtil"
    )

    private val VIP_METHODS = listOf(
        "isVip", "isGreenVip", "isGreenDiamond", "isHvVip", "isSuperVip",
        "isLuxuryGreenVip", "isMusicVip", "isGreenVipAvailable"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.qqMusicVipEnabled) return
        LogX.i("QQ音乐VIP解锁启动（仅应用层）")

        var hookedAny = false
        for (clsName in VIP_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in VIP_METHODS) {
                if (tryHookBoolean(cls, clsName, m)) hookedAny = true
            }
            // getVipLevel / getGreenVipLevel 返回高级别
            tryHookIntReturning(cls, clsName, "getVipLevel", 6)
            tryHookIntReturning(cls, clsName, "getGreenVipLevel", 6)
            tryHookIntReturning(cls, clsName, "getLuxuryLevel", 6)
        }
        if (hookedAny) {
            LogX.i("QQ音乐VIP状态Hook完成")
        } else {
            LogX.w("QQ音乐VIP状态类未找到候选类，可能版本已变更")
        }
    }

    /** Hook 无参 boolean 方法返回 true */
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

    /** Hook 无参 int 方法返回指定值 */
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
