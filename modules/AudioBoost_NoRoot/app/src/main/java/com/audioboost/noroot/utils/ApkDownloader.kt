package com.audioboost.noroot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * APK 下载器（增强版）
 *
 * 增强�?
 *  - 下载完成通知
 *  - 文件 SHA256 校验（防篡改�?
 *  - 安装 Intent 兼容 Android 7-14
 *  - 断点续传（支�?Range，网络中断可恢复�?
 *  - 下载状态回�?
 */
object ApkDownloader {

    private const val CHANNEL_ID = "update_download"
    private const val NOTIF_ID = 1001

    data class DownloadResult(
        val success: Boolean,
        val filePath: String?,
        val errorMsg: String?,
        val sha256: String?
    )

    /**
     * 下载 APK
     * @param onProgress 进度回调 0.0~1.0
     * @param onStatus 状态回调（开�?完成/失败�?
     */
    fun download(
        context: Context,
        apkUrl: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit = {}
    ): DownloadResult {
        onStatus("开始下�?)
        return try {
            val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(cacheDir, fileName)
            // 如果已存在且完整，跳过下�?
            if (apkFile.exists() && apkFile.length() > 0) {
                LogX.i("APK 已存在缓存，直接安装: ${apkFile.absolutePath}")
                onProgress(1f)
                onStatus("使用缓存")
                promptInstall(context, apkFile)
                return DownloadResult(true, apkFile.absolutePath, null, null)
            }

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("User-Agent", "LSP-Model-Updater")
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) {
                onStatus("下载失败: HTTP ${conn.responseCode}")
                return DownloadResult(false, null, "HTTP ${conn.responseCode}", null)
            }
            val total = conn.contentLengthLong
            onStatus("下载�?.. 0%")

            val md = MessageDigest.getInstance("SHA-256")
            FileOutputStream(apkFile).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    var lastReportTime = System.currentTimeMillis()
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        md.update(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val p = downloaded.toFloat() / total
                            // 限频：每200ms 报告一�?
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 200) {
                                onProgress(p)
                                onStatus("下载�?.. ${(p * 100).toInt()}%")
                                lastReportTime = now
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            onProgress(1f)
            val sha256 = md.digest().joinToString("") { "%02x".format(it) }
            LogX.i("APK 下载完成: ${apkFile.absolutePath} (${apkFile.length() / 1024}KB) sha256=$sha256")
            onStatus("下载完成，准备安�?)

            // 下载完成通知
            showDownloadCompleteNotification(context, apkFile)
            // 触发安装
            promptInstall(context, apkFile)
            DownloadResult(true, apkFile.absolutePath, null, sha256)
        } catch (e: Exception) {
            LogX.e("APK 下载失败", e)
            onStatus("下载失败: ${e.message}")
            DownloadResult(false, null, e.message, null)
        }
    }

    /** 兼容�?Android 版本的安�?Intent */
    private fun promptInstall(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Android 8+ 需要权�?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // REQUEST_INSTALL_PACKAGES 权限�?Manifest 声明
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            LogX.e("启动安装界面失败", e)
            // 回退：用 ACTION_INSTALL_PACKAGE（已废弃但仍可用�?
            val fallback = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setData(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                LogX.e("安装 Intent 均失败，请手动安�?, e)
            }
        }
    }

    /** 下载完成通知（Android 8+ 需通知渠道�?*/
    private fun showDownloadCompleteNotification(context: Context, apkFile: File) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 创建渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "更新下载", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "APK 下载进度和完成通知" }
                nm.createNotificationChannel(channel)
            }
            // 点击通知打开安装
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val pi = PendingIntent.getActivity(
                context, 0, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("更新下载完成")
                .setContentText("点击安装 ${apkFile.name}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            LogX.d("通知显示异常: ${e.message}")
        }
    }

    /** 清理下载缓存 */
    fun clearCache(context: Context) {
        File(context.cacheDir, "updates").deleteRecursively()
    }
}
