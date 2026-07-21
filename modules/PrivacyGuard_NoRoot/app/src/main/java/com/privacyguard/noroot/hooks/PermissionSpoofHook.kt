package com.privacyguard.noroot.hooks

import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogStore
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 权限欺骗Hook（仅应用层，无法影响系统全局�?
 *
 * 硬性限制：
 *  - 仅欺�?APP 自身的权限检�?API，不真的修改系统授权
 *  - 不调�?pm revoke/grant，不进行全局权限拦截
 *  - 系统/其他 APP 仍按真实授权运行
 *  - APP 通过 native 直接�?/proc �?IPC 查询真实权限时本Hook无效
 *
 * 功能�?
 *  - Hook ContextWrapper.checkSelfPermission
 *  - Hook PackageManager.checkPermission
 *  - Hook ContextCompat.checkSelfPermission（androidx 兼容�?
 *  对配置中选定的危险权限返�?PERMISSION_DENIED，让 APP 行为退化为"无权限模�?
 */
object PermissionSpoofHook {

    private const val PERMISSION_GRANTED = 0
    private const val PERMISSION_DENIED = -1

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.permissionSpoofEnabled) return
        if (cfg.deniedPermissions.isEmpty()) {
            LogX.d("权限欺骗开启但未配置拒绝列表，跳过")
            return
        }
        LogX.i("权限欺骗启动（仅应用层）：拒�?${cfg.deniedPermissions.size} 个权�?)
        try { LogStore.add("spoofed", "伪造权限检�?) } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        val deniedSet = cfg.deniedPermissions.toSet()

        hookContextWrapperCheckPermission(lpparam, deniedSet)
        hookPackageManagerCheckPermission(lpparam, deniedSet)
        hookContextCompatCheckPermission(lpparam, deniedSet)
    }

    /** Hook ContextWrapper.checkSelfPermission (Android 23+) */
    private fun hookContextWrapperCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val cw = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cw, "checkSelfPermission",
                    String::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                                LogX.d("权限欺骗: $perm -> DENIED")
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkSelfPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // checkPermission(String, int, int)
            try {
                XposedHelpers.findAndHookMethod(cw, "checkPermission",
                    String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("ContextWrapper", "checkSelfPermission", e)
        }
    }

    /** Hook PackageManager.checkPermission */
    private fun hookPackageManagerCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(pm, "checkPermission",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("PackageManager", "checkPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("PackageManager", "checkPermission", e)
        }
    }

    /** Hook androidx ContextCompat.checkSelfPermission (反射查找，可能不存在) */
    private fun hookContextCompatCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val cc = XposedHelpers.findClassIfExists(
                "androidx.core.content.ContextCompat", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cc, "checkSelfPermission",
                    "android.content.Context", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[1] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("ContextCompat", "checkSelfPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            // androidx 类不存在是正常情�?
            LogX.d("ContextCompat 未找到，跳过 androidx 兼容Hook")
        }
    }
}
