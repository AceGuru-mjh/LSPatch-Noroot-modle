package com.videosaver.noroot.utils

import kotlin.random.Random

object AntiDetectionHelper {
    fun sleepDuringVerify() {
        try { Thread.sleep(Random.nextLong(50, 200)) } catch (_: Throwable) { }
    }

    fun isSecurityCritical(className: String): Boolean {
        val keywords = listOf("signature", "verify", "integrity", "tamper", "cert")
        return keywords.any { className.lowercase().contains(it) }
    }
}
