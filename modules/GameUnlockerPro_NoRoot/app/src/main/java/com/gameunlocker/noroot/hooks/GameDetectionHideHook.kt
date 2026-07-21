package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 游戏环境检测隐�?Hook
 *
 * 硬性限制：
 *  - 全部�?Java �?Hook，无法拦�?Native 层直�?dlopen/fopen 的检�?
 *  - LSPatch 本地模式会修�?APK 签名�?lib 路径，主流游戏反作弊 SDK 可能检测到
 *  - 封号风险需自行承担，建议仅用于单机或轻检测游�?
 *
 * 拦截路径�?
 *  1. PackageManager 查询 �?隐藏 Xposed/Shizuku/MT 管理器等�?
 *  2. ClassLoader.loadClass �?拦截 de.robv.android.xposed 类加�?
 *  3. File.exists �?隐藏特征文件存在�?
 *  4. System.loadLibrary �?拦截安全检�?so 库加�?
 *  5. ActivityManager.getRunningAppProcesses �?过滤敏感进程
 *  6. Throwable.getStackTrace �?清除堆栈�?Xposed 痕迹
 */
object GameDetectionHideHook {

    private val HIDE_PKGS = arrayOf(
        "org.lsposed.lspatch", "moe.shizuku.privileged.api",
        "de.robv.android.xposed.installer",
        "me.piebridge.brevent", "bin.mt.plus", "bin.mt.plus.canary"
    )

    private val HIDE_PATHS = listOf(
        "XposedBridge.jar", "/data/local/tmp/xposed",
        "/data/adb/modules", "/sdcard/Shizuku"
    )

    /** 需要阻止加载的安全检�?so �?*/
    private val BLOCK_LIBS = listOf(
        "tss_sdk", "tersafe", "tguard", "msaoaidsec", "oasis", "grsdk"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.detectionHideEnabled) return
        LogX.i("环境隐藏启动")

        if (cfg.hideShizuku || cfg.hideXposed || cfg.hideLspatch) {
            hookPackageManager(lpparam)
            hookClassLoader(lpparam)
            hookFileSystem(lpparam)
            hookProcessList(lpparam)
            hookNativeLibs(lpparam)
            hookStackTrace(lpparam)
        }
    }

    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(pm, "getPackageInfo",
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[0] as? String ?: return
                            if (HIDE_PKGS.any { name.contains(it, true) }) {
                                throw android.content.pm.PackageManager.NameNotFoundException()
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(pm, "getInstalledApplications",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val list = p.result as? MutableList<*> ?: return
                            val filtered = list.filter { item ->
                                try {
                                    val field = item?.javaClass?.getDeclaredField("packageName")
                                    field?.isAccessible = true
                                    val name = field?.get(item) as? String ?: return@filter true
                                    !HIDE_PKGS.any { name.contains(it, true) }
                                } catch (_: Throwable) { true }
                            }
                            p.result = java.util.ArrayList(filtered)
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("PackageManager", "getPackageInfo/getInstalledApplications")
        } catch (e: Throwable) {
            LogX.hookFailed("PackageManager", "hide", e)
        }
    }

    private fun hookClassLoader(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cl = XposedHelpers.findClassIfExists(
                "java.lang.ClassLoader", lpparam.classLoader) ?: return
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val name = p.args[0] as? String ?: return
                    if (name.contains("de.robv.android.xposed") && !name.contains("gameunlocker")) {
                        throw ClassNotFoundException()
                    }
                }
            }
            try { XposedHelpers.findAndHookMethod(cl, "loadClass", String::class.java, hook) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            try { XposedHelpers.findAndHookMethod(cl, "findClass", String::class.java, hook) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            LogX.hookSuccess("ClassLoader", "loadClass/findClass Xposed屏蔽")
        } catch (e: Throwable) {
            LogX.hookFailed("ClassLoader", "loadClass", e)
        }
    }

    private fun hookFileSystem(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val file = XposedHelpers.findClassIfExists("java.io.File", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(file, "exists", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val path = (p.thisObject as java.io.File).absolutePath
                    if (HIDE_PATHS.any { path.contains(it, true) }) p.result = false
                }
            })
            LogX.hookSuccess("File", "exists")
        } catch (e: Throwable) {
            LogX.hookFailed("File", "exists", e)
        }
    }

    private fun hookProcessList(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val am = XposedHelpers.findClassIfExists(
                "android.app.ActivityManager", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(am, "getRunningAppProcesses", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val list = p.result as? MutableList<*> ?: return
                    p.result = java.util.ArrayList(list.filter { proc ->
                        try {
                            val f = proc?.javaClass?.getDeclaredField("processName")
                            f?.isAccessible = true
                            val name = f?.get(proc) as? String ?: return@filter true
                            !HIDE_PKGS.any { name.contains(it, true) }
                        } catch (_: Throwable) { true }
                    })
                }
            })
            LogX.hookSuccess("ActivityManager", "getRunningAppProcesses")
        } catch (e: Throwable) {
            LogX.hookFailed("ActivityManager", "getRunningAppProcesses", e)
        }
    }

    private fun hookNativeLibs(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sys = XposedHelpers.findClassIfExists("java.lang.System", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(sys, "loadLibrary", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val lib = p.args[0] as? String ?: return
                        if (BLOCK_LIBS.any { lib.contains(it, true) }) {
                            LogX.w("拦截安全�? $lib")
                            throw UnsatisfiedLinkError("Library $lib not found")
                        }
                    }
                })
            LogX.hookSuccess("System", "loadLibrary")
        } catch (e: Throwable) {
            LogX.hookFailed("System", "loadLibrary", e)
        }
    }

    private fun hookStackTrace(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val t = XposedHelpers.findClassIfExists(
                "java.lang.Throwable", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(t, "getStackTrace", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val trace = p.result as? Array<StackTraceElement> ?: return
                    val clean = trace.filter { el ->
                        !el.className.contains("de.robv.android.xposed") &&
                        !el.className.contains("com.gameunlocker") &&
                        !el.className.contains("org.lsposed")
                    }.toTypedArray()
                    if (clean.size < trace.size) p.result = clean
                }
            })
            LogX.hookSuccess("Throwable", "getStackTrace")
        } catch (e: Throwable) {
            LogX.hookFailed("Throwable", "getStackTrace", e)
        }
    }
}
