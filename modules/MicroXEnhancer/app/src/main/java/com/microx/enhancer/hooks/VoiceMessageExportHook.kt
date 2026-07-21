package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.LogX
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 【实验性】语音消息导�?Hook
 *
 * 功能�?
 *  - Hook 微信语音消息播放方法，在播放时同步将语音文件保存�?/sdcard/MicroXEnhancer/voice/
 *  - 文件名格式：voice_<时间�?_<msgId>.amr
 *  - 仅微信端实现（QQ 语音协议复杂度高，暂不实现）
 *
 * 实现原理�?
 *  - 微信语音文件�?silk/amr 格式存储�?data/data/com.tencent.mm/.../voice2/ 目录
 *  - 播放语音时通过 VoicePlayer 播放对应文件路径
 *  - Hook 播放器播放方法，读取被播放的文件路径并复制到外部存储
 *
 * 硬性限制：
 *  - 需要存储权限（在外部存储写文件�?
 *  - 不同微信版本语音播放类名差异大，使用多候选类名容�?
 */
object VoiceMessageExportHook {

    private const val EXPORT_DIR = "/sdcard/MicroXEnhancer/voice"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_VOICE_MESSAGE_EXPORT)) return
        HookHelper.log("【实验性】加载语音消息导出Hook（微信）")

        hookVoicePlayer(lpparam)
    }

    /** Hook 微信语音播放器：播放时复制语音文�?*/
    private fun hookVoicePlayer(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 多候选类名：不同微信版本语音播放器类名不�?
        val playerClasses = listOf(
            "com.tencent.mm.plugin.voip.video.PlayerEngine",
            "com.tencent.mm.plugin.voiceplayer.VoicePlayer",
            "com.tencent.mm.plugin.voiceplayer.c",
            "com.tencent.mm.plugin.voiceplayer.a",
            "com.tencent.mm.modelvoice.QVoicePlayer",
            "com.tencent.mm.plugin.scanner.util.PlayerAudio"
        )

        var hookedAny = false
        for (clsName in playerClasses) {
            val cls = HookHelper.findClassSafe(lpparam, clsName) ?: continue

            // Hook play(String path) / play(String path, ...) 系列方法
            try {
                XposedHelpers.findAndHookMethod(cls, "play",
                    String::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun afterHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            val voicePath = p.args[0] as? String ?: return
                            exportVoiceFile(voicePath)
                        }
                    })
                HookHelper.logD("语音播放�?Hook 成功: $clsName")
                hookedAny = true
            } catch (_: Throwable) {
                // 该类没有 String 单参 play 方法，继续尝试下一�?
            }

            // 备用：Hook setDataSource(String path)（MediaPlayer 接口�?
            try {
                XposedHelpers.findAndHookMethod(cls, "setDataSource",
                    String::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun afterHookedMethod(p: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
                            val voicePath = p.args[0] as? String ?: return
                            if (voicePath.contains("voice") || voicePath.endsWith(".amr")
                                || voicePath.endsWith(".silk") || voicePath.endsWith(".aud")) {
                                exportVoiceFile(voicePath)
                            }
                        }
                    })
                HookHelper.logD("语音 setDataSource Hook 成功: $clsName")
                hookedAny = true
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }

        if (!hookedAny) {
            HookHelper.logE("未找到微信语音播放器类，请尝试不同微信版�?)
        }
    }

    /** 复制语音文件到导出目�?*/
    private fun exportVoiceFile(srcPath: String) {
        try {
            val src = File(srcPath)
            if (!src.exists() || src.length() == 0L) return

            val dir = File(EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val srcName = src.nameWithoutExtension
            val ext = if (src.extension.isNotEmpty()) src.extension else "amr"
            val dst = File(dir, "voice_${ts}_$srcName.$ext")

            FileInputStreamSafe(src).use { input ->
                FileOutputStream(dst).use { output ->
                    input.copyTo(output)
                }
            }
            HookHelper.log("已导出语�? ${dst.absolutePath} (${src.length()} bytes)")
        } catch (e: Exception) {
            HookHelper.logE("导出语音异常: ${e.message}", e)
        }
    }

    /** 包装 FileInputStream，避免直接抛 FileNotFoundException */
    private class FileInputStreamSafe(private val file: File) : java.io.InputStream() {
        private val inner: java.io.FileInputStream? = try { java.io.FileInputStream(file) } catch (_: Throwable) { null }

        override fun read(): Int = inner?.read() ?: -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner?.read(b, off, len) ?: -1
        override fun close() { inner?.close() }
    }
}
