package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 爱奇艺 VIP 解锁 Hook（黄金/白金/星钻会员）
 *
 * 候选 Hook 类：
 *  1. com.iqiyi.video.PlayerUtil
 *  2. com.iqiyi.video.QYVipManager
 *  3. com.iqiyi.video.UserCenter
 *  4. org.qiyi.basecore.vip.VipManager
 *  5. org.qiyi.android.video.vip.VipController
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Java 层 VIP 状态查询方法
 *  - 服务端播放鉴权不绕过，部分 4K/杜比片源仍需真实VIP
 */
object IqiyiVipHook {

    private val VIP_CLASS_CANDIDATES = listOf(
        "com.iqiyi.video.PlayerUtil",
        "com.iqiyi.video.QYVipManager",
        "com.iqiyi.video.UserCenter",
        "org.qiyi.basecore.vip.VipManager",
        "org.qiyi.android.video.vip.VipController",
        "com.iqiyi.video.vip.VipBusiness"
    )

    private val VIP_METHODS = listOf(
        "isVip", "isQiyiVip", "isVipUser", "isGoldenVip", "isPlatinumVip",
        "isStarDiamondVip", "isDiamondVip", "isVipAvailable"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.iqiyiVipEnabled) return
        LogX.i("爱奇艺VIP解锁启动（仅应用层）")

        var hookedAny = false
        for (clsName in VIP_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in VIP_METHODS) {
                if (tryHookBoolean(cls, clsName, m)) hookedAny = true
            }
            // getVipType / getVipLevel 返回高级别
            tryHookIntReturning(cls, clsName, "getVipType", 3)
            tryHookIntReturning(cls, clsName, "getVipLevel", 3)
            tryHookIntReturning(cls, clsName, "getUserVipType", 3)
        }
        if (hookedAny) {
            LogX.i("爱奇艺VIP状态Hook完成")
        } else {
            LogX.w("爱奇艺VIP状态类未找到候选类，可能版本已变更")
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
