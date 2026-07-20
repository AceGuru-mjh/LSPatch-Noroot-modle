package com.privacyguard.noroot.utils

import java.util.Random

/**
 * 伪造设备标识缓存
 * 单次进程生命周期内稳定，进程重启后重新随机
 */
object FakeDeviceCache {

    private val rng = Random()

    val fakeImei: String = genImei()
    val fakeMeid: String = genMeid()
    val fakeSubscriberId: String = genImsi()
    val fakeLine1Number: String = genPhone()
    val fakeSimSerial: String = genSimSerial()
    val fakeAndroidId: String = genAndroidId()
    val fakeMacAddress: String = genMac()
    val fakeBssid: String = genMac()
    val fakeSerial: String = genSerial()

    // ===== 实验性 =====
    val fakeIpAddress: String = genIp()
    val fakeDns: String = "8.8.8.8"

    private fun genImei(): String {
        // 15位数字
        val sb = StringBuilder()
        repeat(15) { sb.append(rng.nextInt(10)) }
        return sb.toString()
    }

    private fun genMeid(): String {
        val sb = StringBuilder()
        repeat(14) { sb.append(rng.nextInt(16).toString(16)) }
        return sb.toString().uppercase()
    }

    private fun genImsi(): String {
        val sb = StringBuilder("460")
        repeat(12) { sb.append(rng.nextInt(10)) }
        return sb.toString()
    }

    private fun genPhone(): String {
        val sb = StringBuilder("1")
        sb.append(rng.nextInt(8) + 1)
        repeat(9) { sb.append(rng.nextInt(10)) }
        return sb.toString()
    }

    private fun genSimSerial(): String {
        val sb = StringBuilder("8986")
        repeat(16) { sb.append(rng.nextInt(10)) }
        return sb.toString()
    }

    private fun genAndroidId(): String {
        val sb = StringBuilder()
        repeat(16) { sb.append(rng.nextInt(16).toString(16)) }
        return sb.toString()
    }

    private fun genMac(): String {
        val sb = StringBuilder()
        repeat(6) {
            if (it != 0) sb.append(":")
            sb.append(String.format("%02X", rng.nextInt(256)))
        }
        return sb.toString()
    }

    private fun genSerial(): String {
        val sb = StringBuilder()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        repeat(11) { sb.append(chars[rng.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun genIp(): String {
        return "${rng.nextInt(223) + 1}.${rng.nextInt(256)}.${rng.nextInt(256)}.${rng.nextInt(254) + 1}"
    }
}
