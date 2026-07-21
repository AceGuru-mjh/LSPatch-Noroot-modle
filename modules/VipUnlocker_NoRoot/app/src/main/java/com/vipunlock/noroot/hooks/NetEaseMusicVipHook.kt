package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 网易云音�?VIP 解锁 Hook
 *
 * 目标：让网易云以为当前账号已开通黑胶VIP�?
 *
 * 候�?Hook 点（多类名容错）�?
 *  1. com.netease.cloudmusic.module.user.MusicConfig.isVip / isBlackVip / getVipType
 *  2. com.netease.cloudmusic.b.MusicConfig（混淆后类名�?
 *  3. com.netease.cloudmusic.music.VipInfoManager.isVip
 *  4. 通用：所有名�?isBlackVip / isVipMusicAvailable / isMusicVip 的方�?
 *
 * 硬性限制（NoRoot版严格遵守）�?
 *  - �?Hook 应用进程�?Java �?VIP 状态查询方�?
 *  - 不修改网易云的服务端校验，下载需 VIP 的歌曲可能仍提示无版�?
 *  - 部分版本 VIP 状态走 native 层或 service 层，�?Hook 不覆�?
 */
object NetEaseMusicVipHook {

    /** 候选类名列表（混淆/版本差异�?*/
    private val VIP_CLASS_CANDIDATES = listOf(
        "com.netease.cloudmusic.module.user.MusicConfig",
        "com.netease.cloudmusic.music.VipInfoManager",
        "com.netease.cloudmusic.b.MusicConfig",
        "com.netease.cloudmusic.module.user.b",
        "com.netease.cloudmusic.music.VipManager",
        "com.netease.cloudmusic.business.VipBusiness"
    )

    /** VIP 状态查询方法名（通用�?*/
    private val VIP_METHODS = listOf(
        "isVip", "isBlackVip", "isVipMusicAvailable", "isMusicVip",
        "isVipUser", "isBlackVipUser", "isVipValid", "isVipExpire"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.netEaseVipEnabled) return
        LogX.i("网易云VIP解锁启动（仅应用层）")

        // 1. 按候选类名逐一尝试 Hook VIP 状态方�?
        var hookedAny = false
        for (clsName in VIP_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in VIP_METHODS) {
                if (tryHookNoArgBoolean(cls, clsName, m)) hookedAny = true
            }
        }
        if (hookedAny) {
            LogX.i("网易云VIP状态方法Hook完成")
        } else {
            LogX.w("网易云VIP状态类未找到候选类，可能版本已变更")
        }

        // 2. Hook getVipType / getVipLevel 返回高级�?
        hookVipLevelMethods(lpparam)

        // 3. Hook SharedPreferences 读取网易云缓�?VIP 标志位（部分版本�?SP�?
        hookSharedPrefsVipFlag(lpparam)
    }

    /** 尝试 Hook 一个无�?boolean 方法，返�?true（已订阅�?*/
    private fun tryHookNoArgBoolean(cls: Class<*>, clsName: String, method: String): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = true
                }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) {
            // 该方法不存在，跳�?
            false
        } catch (e: Exception) {
            LogX.w("Hook失败 $clsName.$method : ${e.message}")
            false
        }
    }

    /** Hook getVipType / getVipLevel 返回高级�?*/
    private fun hookVipLevelMethods(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in VIP_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            // getVipType() -> 返回 1（黑胶VIP�?
            try {
                XposedHelpers.findAndHookMethod(cls, "getVipType", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = 1 }
                })
                LogX.hookSuccess(clsName, "getVipType")
            } catch (e: NoSuchMethodError) { /* 忽略，方法不存在 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
            // getVipLevel() -> 返回 5
            try {
                XposedHelpers.findAndHookMethod(cls, "getVipLevel", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = 5 }
                })
                LogX.hookSuccess(clsName, "getVipLevel")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
            // getBlackVipLevel() -> 返回 5
            try {
                XposedHelpers.findAndHookMethod(cls, "getBlackVipLevel", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = 5 }
                })
                LogX.hookSuccess(clsName, "getBlackVipLevel")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    /**
     * Hook SharedPreferences.getString 拦截网易�?VIP 标志�?
     * 候�?key：vip_type, is_black_vip, vip_level, music_package
     */
    private fun hookSharedPrefsVipFlag(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sp = XposedHelpers.findClassIfExists(
                "android.app.SharedPreferencesImpl", lpparam.classLoader) ?: return
            val vipKeys = setOf("vip_type", "is_black_vip", "vip_level", "music_package", "is_vip")
            try {
                XposedHelpers.findAndHookMethod(sp, "getString",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            when (key) {
                                "vip_type" -> p.result = "1"
                                "is_black_vip" -> p.result = "1"
                                "vip_level" -> p.result = "5"
                                "music_package" -> p.result = "1"
                                "is_vip" -> p.result = "1"
                            }
                        }
                    })
                LogX.hookSuccess("SharedPreferencesImpl", "getString(vip_flag)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getInt 也拦截（部分版本�?int 存储 VIP 级别�?
            try {
                XposedHelpers.findAndHookMethod(sp, "getInt",
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            if (key in vipKeys) p.result = 5
                        }
                    })
                LogX.hookSuccess("SharedPreferencesImpl", "getInt(vip_flag)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SharedPreferencesImpl", "vip_flag", e)
        }
    }
}
