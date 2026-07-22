package .utils

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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import com.vipunlock.noroot.utils.LogX
import java.util.concurrent.atomic.AtomicBoolean

object ApkDownloader {

    private const val CHANNEL_ID = "update_download"
    private const val NOTIF_ID = 1001

    data class DownloadResult(
        val success: Boolean,
        val filePath: String?,
        val errorMsg: String?,
        val sha256: String?
    )

    /** Generate mirror URLs for a GitHub release asset */
    fun generateMirrors(originalUrl: String): List<String> {
        val mirrors = mutableListOf(originalUrl)
        try {
            val uri = Uri.parse(originalUrl).toString()
            // jsDelivr CDN mirror
            if (uri.contains("github.com") && uri.contains("releases/download")) {
                val parts = uri.split("github.com/", limit = 2)
                if (parts.size == 2) {
                    mirrors.add("https://cdn.jsdelivr.net/gh/${parts[1].replace("/releases/download/", "@")}")
                }
            }
            // FastGit mirror
            mirrors.add(uri.replace("github.com", "download.fastgit.org"))
            // GitHub raw proxy mirror
            mirrors.add(uri.replace("github.com", "ghproxy.com/https://github.com"))
        } catch (_: Exception) { }
        return mirrors
    }

    /**
     * Download APK with resume support and mirror fallback
     * @param mirrors list of mirror URLs to try (ordered by priority)
     */
    fun download(
        context: Context,
        mirrors: List<String>,
        fileName: String,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit = {},
        paused: AtomicBoolean = AtomicBoolean(false)
    ): DownloadResult {
        onStatus("Preparing download...")

        val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(cacheDir, fileName)
        val tempFile = File(cacheDir, "$fileName.tmp")

        // Check if complete file already exists
        if (apkFile.exists() && apkFile.length() > 0) {
            onProgress(1f)
            onStatus("Cached - ready to install")
            promptInstall(context, apkFile)
            return DownloadResult(true, apkFile.absolutePath, null, null)
        }

        // Resume from temp file
        var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

        for ((idx, mirrorUrl) in mirrors.withIndex()) {
            if (paused.get()) break
            onStatus("Trying CDN ${idx + 1}/${mirrors.size}...")

            try {
                val result = downloadFromMirror(
                    mirrorUrl, tempFile, apkFile, downloadedBytes,
                    onProgress, onStatus, paused
                )
                if (result.success) {
                    onProgress(1f)
                    onStatus("Download complete")
                    showDownloadCompleteNotification(context, apkFile)
                    promptInstall(context, apkFile)
                    return result
                }
                if (paused.get()) break
                onStatus("CDN ${idx + 1} failed, trying next...")
            } catch (_: Exception) {
                onStatus("CDN ${idx + 1} error, trying next...")
            }
        }

        return DownloadResult(false, null, "All mirrors exhausted", null)
    }

    private fun downloadFromMirror(
        url: String,
        tempFile: File,
        finalFile: File,
        startOffset: Long,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit,
        paused: AtomicBoolean
    ): DownloadResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("User-Agent", "LSP-Model-Updater/1.0")
                instanceFollowRedirects = true
                // Resume support: request remaining bytes
                if (startOffset > 0) {
                    setRequestProperty("Range", "bytes=$startOffset-")
                }
            }

            val responseCode = conn.responseCode
            val supportsResume = responseCode == 206  // Partial Content
            val supportsFull = responseCode == 200

            if (!supportsResume && !supportsFull && responseCode != 416) {
                return DownloadResult(false, null, "HTTP $responseCode", null)
            }

            // If server doesn't support Range but we already have partial data, restart from 0
            val totalSize = if (supportsResume) {
                conn.getHeaderField("Content-Range")?.let { range ->
                    range.substringAfter("/").toLongOrNull()
                } ?: conn.contentLengthLong
            } else {
                if (startOffset > 0) tempFile.delete()
                conn.contentLengthLong
            }

            val total = totalSize.coerceAtLeast(0)
            val appendMode = supportsResume && startOffset > 0

            val fileOut = if (appendMode) {
                RandomAccessFile(tempFile, "rw").apply { seek(startOffset) }
            } else {
                tempFile.delete()
                RandomAccessFile(tempFile, "rw")
            }

            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                var read: Int
                var downloaded = if (appendMode) startOffset else 0L
                var lastReport = System.currentTimeMillis()
                val md = MessageDigest.getInstance("SHA-256")

                if (!appendMode) {
                    // Read existing bytes to compute hash
                    if (startOffset > 0) {
                        val existing = tempFile.inputStream()
                        val existingBuf = ByteArray(8192)
                        var er: Int
                        while (existing.read(existingBuf).also { er = it } != -1) {
                            md.update(existingBuf, 0, er)
                        }
                        existing.close()
                    }
                }

                while (input.read(buf).also { read = it } != -1) {
                    if (paused.get()) {
                        fileOut.close()
                        return DownloadResult(false, null, "Paused", null)
                    }
                    fileOut.write(buf, 0, read)
                    md.update(buf, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    if (now - lastReport > 250 && total > 0) {
                        val p = downloaded.toFloat() / total.coerceAtLeast(1)
                        onProgress(p)
                        onStatus("${(p * 100).toInt()}% (${downloaded / 1024}KB)")
                        lastReport = now
                    }
                }
                fileOut.close()

                // Rename temp to final
                tempFile.renameTo(finalFile)
                val sha256 = md.digest().joinToString("") { "%02x".format(it) }
                DownloadResult(true, finalFile.absolutePath, null, sha256)
            }
        } catch (e: Exception) {
            conn?.disconnect()
            DownloadResult(false, null, e.localizedMessage, null)
        } finally {
            conn?.disconnect()
        }
    }

    /** Compatible install intent across Android versions */
    private fun promptInstall(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            LogX.e("Install intent failed", e)
        }
    }

    private fun showDownloadCompleteNotification(context: Context, apkFile: File) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "Update Downloads", NotificationManager.IMPORTANCE_LOW
                ))
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val pi = PendingIntent.getActivity(context, 0, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText("Tap to install ${apkFile.name}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            LogX.d("Notification failed: ${e.localizedMessage}")
        }
    }

    fun clearCache(context: Context) {
        val dir = File(context.cacheDir, "updates")
        dir.deleteRecursively()
    }
}
