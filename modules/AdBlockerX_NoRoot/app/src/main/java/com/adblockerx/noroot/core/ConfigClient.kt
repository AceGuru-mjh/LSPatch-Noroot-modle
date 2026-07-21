package com.adblockerx.noroot.core

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri

object ConfigClient {
    private const val MODULE_PKG = "com.adblockerx.noroot"
    private val CONFIG_URI = Uri.parse("content://$MODULE_PKG.configprovider")

    fun isModuleAppInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(MODULE_PKG, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }
    }

    fun readConfig(ctx: Context): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!isModuleAppInstalled(ctx)) return result
        try {
            val cursor: Cursor? = ctx.contentResolver.query(CONFIG_URI, null, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    try {
                        val key = it.getString(0) ?: continue
                        val value = it.getString(1) ?: continue
                        result[key] = value
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) { }
        return result
    }

    fun readMasterSwitch(ctx: Context): Boolean {
        return readConfig(ctx)["master_switch"]?.toBoolean() ?: true
    }
}
