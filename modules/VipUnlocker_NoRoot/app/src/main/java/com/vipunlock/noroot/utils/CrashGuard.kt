package com.vipunlock.noroot.utils

import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashGuard {
    private const val LOG_FILE = "lsp_crash.log"
    private const val TAG = "LSP-CrashGuard"
    private var logFile: File? = null
    private var started = false

    fun init(filesDir: File?) {
        if (started) return
        started = true

        val dir = filesDir ?: File("/data/data/unknown/files")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, LOG_FILE)

        writeSync("=== LSP Crash Guard Init | pid=${Process.myPid()} | time=${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())} ===")

        Log.e(TAG, "CrashGuard initialized, log: ${logFile?.absolutePath}")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                writeSync("FATAL: ${sw.toString()}")
                Log.e(TAG, "FATAL: ${ex.message}", ex)
            } catch (_: Throwable) { }
            defaultHandler?.uncaughtException(thread, ex)
        }
    }

    fun log(msg: String) {
        writeSync(msg)
    }

    private fun writeSync(msg: String) {
        try {
            val f = logFile ?: return
            val fos = FileOutputStream(f, true)
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            fos.write("[$timestamp] $msg\n".toByteArray())
            fos.flush()
            try { fos.fd.sync() } catch (_: Throwable) { }
            fos.close()
        } catch (_: Throwable) { }
    }

    fun getLogPath(): String = logFile?.absolutePath ?: "N/A"
}
