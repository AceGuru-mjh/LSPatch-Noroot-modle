package com.videosaver.noroot.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 视频文件保存器（应用进程内执行，无需 Root/Shizuku）
 *
 * 策略：
 *  - 优先使用配置中的 customSavePath（用户自定义路径）
 *  - 路径为空或不可写时，回退到外部存储公共目录 Movies/VideoSaver/
 *  - 自动重命名：平台_时间戳.扩展名
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅在目标 APP 自身进程内执行，借用 APP 的存储权限写文件
 *  - 不调用 Shizuku/Root，不写 /system /sys
 *  - 失败时静默 catch，不影响宿主正常流程
 */
object VideoFileSaver {

    /** 默认保存目录（用户未配置时使用） */
    private const val DEFAULT_DIR = "/sdcard/Download/VideoSaver/"

    /**
     * 保存输入流到文件，返回保存后的绝对路径，失败返回 null
     */
    fun saveStream(
        context: Context?,
        input: InputStream,
        platform: String,
        extension: String = "mp4",
        customPath: String? = null,
        autoRename: Boolean = true
    ): String? {
        return try {
            val dir = resolveSaveDir(context, customPath)
            if (!dir.exists()) dir.mkdirs()
            val fileName = buildFileName(platform, extension, autoRename)
            val target = File(dir, fileName)
            FileOutputStream(target).use { out ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                }
                out.flush()
            }
            LogX.i("视频已保存: ${target.absolutePath} (${target.length() / 1024} KB)")
            target.absolutePath
        } catch (e: Throwable) {
            LogX.w("视频保存失败: ${e.message}")
            null
        }
    }

    /** 直接写字节数组 */
    fun saveBytes(
        context: Context?,
        bytes: ByteArray,
        platform: String,
        extension: String = "mp4",
        customPath: String? = null,
        autoRename: Boolean = true
    ): String? {
        return try {
            val dir = resolveSaveDir(context, customPath)
            if (!dir.exists()) dir.mkdirs()
            val fileName = buildFileName(platform, extension, autoRename)
            val target = File(dir, fileName)
            FileOutputStream(target).use { it.write(bytes) }
            LogX.i("视频已保存: ${target.absolutePath} (${target.length() / 1024} KB)")
            target.absolutePath
        } catch (e: Throwable) {
            LogX.w("视频保存失败: ${e.message}")
            null
        }
    }

    /** 解析保存目录：优先 customPath，其次 Movies/VideoSaver/，最后 DEFAULT_DIR */
    private fun resolveSaveDir(context: Context?, customPath: String?): File {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.isAbsolute) return f
        }
        // 回退到公共 Movies 目录
        return try {
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            File(movies, "VideoSaver")
        } catch (_: Throwable) {
            File(DEFAULT_DIR)
        }
    }

    private fun buildFileName(platform: String, extension: String, autoRename: Boolean): String {
        val safePlatform = platform.replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (autoRename) {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            "${safePlatform}_$ts.$extension"
        } else {
            "$safePlatform.$extension"
        }
    }

    /** 获取保存目录绝对路径字符串（用于 UI 展示） */
    fun resolveSaveDirString(customPath: String?): String {
        return resolveSaveDir(null, customPath).absolutePath
    }
}
