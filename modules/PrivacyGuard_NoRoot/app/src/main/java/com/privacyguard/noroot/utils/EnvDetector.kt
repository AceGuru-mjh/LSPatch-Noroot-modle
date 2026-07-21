package com.privacyguard.noroot.utils

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage

object EnvDetector {
    var isLocalMode: Boolean = false
        private set

    fun detect(lpparam: XC_LoadPackage.LoadPackageParam) {
        isLocalMode = try {
            Class.forName("org.lsposed.lspatch.LSPatch", false, lpparam.classLoader)
            true
        } catch (_: Throwable) { false }
    }

    fun getStoragePath(context: Context): String = context.filesDir.absolutePath
}
