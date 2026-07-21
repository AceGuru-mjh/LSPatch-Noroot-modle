package com.mjh.shizukufix.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * 包信息辅助工具（Kotlin 重写自 utils/PackageHelper.java）
 *
 * 提供获取 ApplicationInfo / PackageInfo / UID / 是否安装 等通用查询。
 * 所有方法吞掉异常返回 null / 默认值，避免 Hook 流程被异常中断。
 */
object PackageHelper {

    /** Scene 主包名 */
    const val SCENE_PACKAGE = "com.omarea.vtools"

    fun getPackageName(context: Context?): String = context?.packageName ?: ""

    /** 获取指定包的 UID，失败返回 -1 */
    fun getUid(context: Context, packageName: String): Int {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.uid
        } catch (_: PackageManager.NameNotFoundException) {
            LogX.e("Failed to get UID for: $packageName")
            -1
        } catch (t: Throwable) {
            LogX.e("Failed to get UID for: $packageName", t)
            -1
        }
    }

    fun getApplicationInfo(context: Context, packageName: String): ApplicationInfo? {
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            LogX.e("App not found: $packageName")
            null
        } catch (t: Throwable) {
            LogX.e("Failed to get ApplicationInfo for: $packageName", t)
            null
        }
    }

    fun getPackageInfo(context: Context, packageName: String): PackageInfo? {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            LogX.e("App not found: $packageName")
            null
        } catch (t: Throwable) {
            LogX.e("Failed to get PackageInfo for: $packageName", t)
            null
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            LogX.e("Failed to check package: $packageName", t)
            false
        }
    }

    fun getAllInstalledApps(context: Context): List<ApplicationInfo> {
        return try {
            context.packageManager.getInstalledApplications(0)
        } catch (t: Throwable) {
            LogX.e("Failed to get installed apps", t)
            emptyList()
        }
    }

    /** 获取指定包是否声明了 Shizuku 相关 service/provider/activity */
    fun hasShizukuComponent(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(
                packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS
            )
            info.services?.any { it.name?.lowercase()?.contains("shizuku") == true } == true ||
            info.providers?.any { it.name?.lowercase()?.contains("shizuku") == true } == true ||
            info.activities?.any {
                val n = it.name?.lowercase() ?: ""
                n.contains("shizuku") || n.contains("permission")
            } == true
        } catch (_: Throwable) { false }
    }
}
