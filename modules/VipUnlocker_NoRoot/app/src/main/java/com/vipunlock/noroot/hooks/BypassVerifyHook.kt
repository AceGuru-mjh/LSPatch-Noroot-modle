package com.vipunlock.noroot.hooks

import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】绕过签名/完整性校验 Hook
 *
 * 目标：让 APP 内的签名自校验/完整性校验逻辑放行。
 *
 * 候选 Hook 点：
 *  1. PackageManager.getPackageInfo(0=签名) 返回官方签名
 *     （注：实际签名常量 GET_SIGNATURES=64, GET_SIGNING_CERTIFICATES=134217728）
 *  2. APP 自实现的签名校验方法：checkSignature / verifySignature / isOfficialSignature
 *  3. 文件完整性校验：checkIntegrity / verifyIntegrity / isTampered
 *  4. Xposed 检测：isXposedExist / isModuleLoaded
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 Java 层校验方法
 *  - 服务端签名校验不绕过
 *  - 部分校验走 native 层（.so 内），本 Hook 不覆盖
 *  - 实验性默认关闭，可能影响 APP 稳定性
 */
object BypassVerifyHook {

    /** APP 自实现签名校验方法名候选 */
    private val VERIFY_METHODS = listOf(
        "checkSignature", "verifySignature", "isOfficialSignature", "isOfficialApp",
        "checkApkSignature", "checkSign", "verifySign", "isTampered", "isModified",
        "checkIntegrity", "verifyIntegrity", "isXposedExist", "isXposedInstalled",
        "isModuleLoaded", "isHooked", "hasXposed"
    )

    /** 候选类名（APP 自实现的校验类） */
    private val VERIFY_CLASS_CANDIDATES = listOf(
        "com.tencent.mobileqq.msf.coreSecurity.SecurityUtil",
        "com.tencent.mm.protocal.SecurityNativeHelper",
        "com.alipay.android.telephony.util.SignatureUtil",
        "com.eg.android.AlipayGphone.security.AlipaySecurityGuard",
        "com.netease.cloudmusic.security.SignatureVerifier",
        "tv.danmaku.bili.security.SecurityCheck",
        "com.iqiyi.video.security.SignatureUtil",
        "com.tencent.qqlive.security.SecurityVerifier",
        "com.baidu.netdisk.security.SignatureCheck",
        "com.zhihu.android.security.ZhihuSecurityGuard"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.bypassVerifyEnabled) return
        LogX.i("【实验性】绕过签名/完整性校验启动（仅应用层）")

        // 1. Hook APP 自实现的校验方法返回通过
        hookVerifyMethods(lpparam)

        // 2. Hook Runtime.exec / ProcessBuilder 拦截 su/which 命令（防 Root 检测）
        hookRootDetectionCommands(lpparam)

        // 3. Hook StackTrace 检测（部分APP通过堆栈查找 Xposed 类）
        hookStackTraceCheck(lpparam)
    }

    /** Hook APP 自实现的校验方法 */
    private fun hookVerifyMethods(lpparam: XC_LoadPackage.LoadPackageParam) {
        var hookedAny = false
        for (clsName in VERIFY_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in VERIFY_METHODS) {
                if (tryHookBooleanReturning(cls, clsName, m, true)) hookedAny = true
            }
        }
        if (hookedAny) {
            LogX.i("签名/完整性校验方法Hook完成")
        }
    }

    /** Hook Runtime.exec 拦截 su / which 命令（防 Root 检测） */
    private fun hookRootDetectionCommands(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val runtime = XposedHelpers.findClassIfExists(
                "java.lang.Runtime", lpparam.classLoader) ?: return

            // exec(String)
            try {
                XposedHelpers.findAndHookMethod(runtime, "exec",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val cmd = p.args[0] as? String ?: return
                            if (isRootDetectionCommand(cmd)) {
                                LogX.d("拦截 Root 检测命令: $cmd")
                                // 抛 IOException 让 APP 以为 su 不存在
                                throw java.io.IOException("Permission denied")
                            }
                        }
                    })
                LogX.hookSuccess("Runtime", "exec(String)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // exec(String[])
            try {
                XposedHelpers.findAndHookMethod(runtime, "exec",
                    Array<String>::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val cmds = p.args[0] as? Array<*> ?: return
                            val cmdStr = cmds.joinToString(" ")
                            if (isRootDetectionCommand(cmdStr)) {
                                LogX.d("拦截 Root 检测命令: $cmdStr")
                                throw java.io.IOException("Permission denied")
                            }
                        }
                    })
                LogX.hookSuccess("Runtime", "exec(String[])")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Runtime", "exec", e)
        }

        // Hook ProcessBuilder.start
        try {
            val pb = XposedHelpers.findClassIfExists(
                "java.lang.ProcessBuilder", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(pb, "start", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val cmdField = XposedHelpers.getObjectField(p.thisObject, "command") as? List<*>
                            val cmdStr = cmdField?.joinToString(" ") ?: ""
                            if (isRootDetectionCommand(cmdStr)) {
                                LogX.d("拦截 Root 检测命令(ProcessBuilder): $cmdStr")
                                throw java.io.IOException("Permission denied")
                            }
                        } catch (io: java.io.IOException) { throw io }
                        catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
                LogX.hookSuccess("ProcessBuilder", "start")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("ProcessBuilder", "start", e)
        }
    }

    /** 判断是否为 Root 检测命令 */
    private fun isRootDetectionCommand(cmd: String): Boolean {
        val lc = cmd.lowercase()
        return lc.contains("su") || lc.contains("which") || lc.contains("busybox") ||
               lc.contains("/system/xbin") || lc.contains("/system/bin/su") ||
               lc.contains("magisk") || lc.contains("supersu")
    }

    /** Hook StackTrace 检测 Xposed */
    private fun hookStackTraceCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val thr = XposedHelpers.findClassIfExists(
                "java.lang.Thread", lpparam.classLoader) ?: return
            // Hook Thread.getStackTrace 移除含 "de.robv.android.xposed" 的元素
            try {
                XposedHelpers.findAndHookMethod(thr, "getStackTrace", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val trace = p.result as? Array<*> ?: return
                        val filtered = trace.filter {
                            val s = it.toString()
                            !s.contains("de.robv.android.xposed") &&
                            !s.contains("lspd") &&
                            !s.contains("XposedBridge")
                        }.toTypedArray()
                        p.result = filtered
                    }
                })
                LogX.hookSuccess("Thread", "getStackTrace")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Thread", "getStackTrace", e)
        }
    }

    private fun tryHookBooleanReturning(cls: Class<*>, clsName: String, method: String, value: Boolean): Boolean {
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
