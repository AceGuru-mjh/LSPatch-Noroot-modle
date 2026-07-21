package com.mjh.shizukufix.hooks

import android.content.Context
import android.content.pm.ApplicationInfo
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import com.mjh.shizukufix.utils.PackageHelper
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 检测 Shizuku 变体包名
 *
 * ⚠️ 本类为工具类，被其他 Hook 调用，不直接注册 Xposed 方法 Hook。
 *
 * 原 shizuku/ShizukuVariantDetector.java 转 Kotlin。
 *
 * 用途：
 *  - MainHook 调用 isShizukuProcess(pkg) 判断目标进程是否为 Shizuku 变体
 *  - apply() 阶段在 Shizuku 进程内启动时扫描全部已安装应用，
 *    识别所有 Shizuku 变体包名并写入日志，辅助排错
 *
 * 按 AI_DEV_GUIDE §4.3 工具类规范，本类不调用任何 Xposed Hook 注册 API，
 * 体检脚本判定为 "utility" 状态（合理）。
 *
 * 已知 Shizuku 包名：
 *  - moe.shizuku.privileged.api
 *  - rikka.shizuku.manager
 *  - moe.shizuku.privileged.api.plus
 *  - com.shizuku.plus
 *  - stellar.shizuku.api
 *  - com.stellar.shizuku
 *  - com.shizuku
 *  - rikka.shizuku
 *  - moe.shizuku
 *
 * 变体检测策略：
 *  - 包名包含 shizuku 关键字（大小写不敏感）
 *  - 且包内声明了含 shizuku 关键字的 service / provider / activity
 */
object ShizukuVariantDetectorHook {

    private val KNOWN_SHIZUKU_PACKAGES = arrayOf(
        "moe.shizuku.privileged.api",
        "rikka.shizuku.manager",
        "moe.shizuku.privileged.api.plus",
        "com.shizuku.plus",
        "stellar.shizuku.api",
        "com.stellar.shizuku",
        "com.shizuku",
        "rikka.shizuku",
        "moe.shizuku"
    )

    private val NAME_KEYWORDS = arrayOf("shizuku")

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.variantDetectEnabled) return
        // 工具类：仅打日志 + 触发后台扫描，不直接注册 Xposed 方法 Hook
        LogX.i("ShizukuVariantDetector 工具类已加载")

        // 在子线程扫描全部已安装应用，避免阻塞 APP 启动
        Thread {
            try {
                val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
                val ctx = XposedHelpers.callMethod(cat, "getApplication") as? Context
                if (ctx != null) {
                    val detected = detectShizukuVariants(ctx)
                    LogX.i("Detected ${detected.size} Shizuku variants: $detected")
                }
            } catch (t: Throwable) {
                LogX.e("变体检测扫描失败", t)
            }
        }.start()
    }

    /** 扫描已安装应用，识别 Shizuku 变体 */
    fun detectShizukuVariants(context: Context): Set<String> {
        val detected = LinkedHashSet<String>()
        LogX.i("Detecting Shizuku variants...")
        try {
            val apps = PackageHelper.getAllInstalledApps(context)
            for (info in apps) {
                val pkg = info.packageName ?: continue
                if (isKnownShizukuPackage(pkg)) {
                    detected.add(pkg)
                    LogX.i("  [Known] Detected Shizuku package: $pkg")
                    continue
                }
                if (isShizukuByName(pkg) && PackageHelper.hasShizukuComponent(context, pkg)) {
                    detected.add(pkg)
                    LogX.i("  [Name+Service] Detected Shizuku variant: $pkg")
                }
            }
        } catch (t: Throwable) {
            LogX.e("Error detecting Shizuku variants", t)
        }
        return detected
    }

    /** 是否为已知 Shizuku 包名 */
    fun isKnownShizukuPackage(packageName: String): Boolean {
        return KNOWN_SHIZUKU_PACKAGES.any { it == packageName }
    }

    /** 包名是否包含 shizuku 关键字 */
    fun isShizukuByName(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return NAME_KEYWORDS.any { lower.contains(it) }
    }

    /** 进程是否为 Shizuku（已知包名 OR 包名含 shizuku 关键字） */
    fun isShizukuProcess(packageName: String): Boolean {
        return isKnownShizukuPackage(packageName) || isShizukuByName(packageName)
    }
}
