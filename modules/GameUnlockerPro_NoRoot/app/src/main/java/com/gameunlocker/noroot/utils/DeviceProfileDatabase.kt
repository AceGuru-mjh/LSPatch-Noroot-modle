package com.gameunlocker.noroot.utils

import com.gameunlocker.noroot.models.DeviceProfile

/**
 * 内置旗舰机型参数库（应用层伪装用）
 * 所有值取自真实机型参数
 */
object DeviceProfileDatabase {

    val BUILT_IN: List<DeviceProfile> = listOf(
        DeviceProfile(
            id = "xiaomi15", displayName = "小米 15",
            brand = "Xiaomi", model = "24129PN74C", manufacturer = "Xiaomi",
            device = "haotian", board = "kalama", hardware = "qcom", product = "haotian",
            cpuModel = "Snapdragon 8 Elite", gpuModel = "Adreno 830",
            androidVersion = "15", sdkVersion = 35,
            screenWidth = 1200, screenHeight = 2670, densityDpi = 460,
            maxRefreshRate = 120f,
            fingerprint = "Xiaomi/haotian/haotian:15/AP3A.240905.015.A1/OS2.0.17.0.VOBCNXM:user/release-keys"
        ),
        DeviceProfile(
            id = "iqoo13", displayName = "iQOO 13",
            brand = "vivo", model = "V2408A", manufacturer = "vivo",
            device = "PD2408", board = "kalama", hardware = "qcom", product = "PD2408",
            cpuModel = "Snapdragon 8 Elite", gpuModel = "Adreno 830",
            androidVersion = "15", sdkVersion = 35,
            screenWidth = 1440, screenHeight = 3200, densityDpi = 560,
            maxRefreshRate = 144f,
            fingerprint = "vivo/PD2408/PD2408:15/AP3A.240905.015.A1/compiler11261647:user/release-keys"
        ),
        DeviceProfile(
            id = "rog9", displayName = "ROG Phone 9",
            brand = "asus", model = "ASUS_AI2401", manufacturer = "asus",
            device = "ASUS_AI2401", board = "kalama", hardware = "qcom", product = "WW_AI2401",
            cpuModel = "Snapdragon 8 Elite", gpuModel = "Adreno 830",
            androidVersion = "15", sdkVersion = 35,
            screenWidth = 1080, screenHeight = 2448, densityDpi = 480,
            maxRefreshRate = 165f,
            fingerprint = "asus/WW_AI2401/ASUS_AI2401:15/AP3A.240905.015.A1/34.0820.0820.407-0:user/release-keys"
        ),
        DeviceProfile(
            id = "samsung_s25", displayName = "Samsung Galaxy S25 Ultra",
            brand = "samsung", model = "SM-S9380", manufacturer = "samsung",
            device = "g3q", board = "e3q", hardware = "qcom", product = "g3qzcx",
            cpuModel = "Snapdragon 8 Elite for Galaxy", gpuModel = "Adreno 830",
            androidVersion = "15", sdkVersion = 35,
            screenWidth = 1440, screenHeight = 3120, densityDpi = 560,
            maxRefreshRate = 120f,
            fingerprint = "samsung/g3qzcx/g3q:15/AP3A.240905.015.A1/S9380ZCS2AYC1:user/release-keys"
        ),
        DeviceProfile(
            id = "oneplus13", displayName = "一加 13",
            brand = "OnePlus", model = "PJZ110", manufacturer = "OnePlus",
            device = "waffle", board = "kalama", hardware = "qcom", product = "PJZ110",
            cpuModel = "Snapdragon 8 Elite", gpuModel = "Adreno 830",
            androidVersion = "15", sdkVersion = 35,
            screenWidth = 1440, screenHeight = 3168, densityDpi = 560,
            maxRefreshRate = 120f,
            fingerprint = "OnePlus/PJZ110/OP5D05L1:15/AP3A.240905.015.A1/V.3.0.0.PZ01_1:user/release-keys"
        ),
        DeviceProfile(
            id = "redmagic10", displayName = "红魔 10 Pro",
            brand = "nubia", model = "NX789J", manufacturer = "nubia",
            device = "NX789J", board = "kalama", hardware = "qcom", product = "NX789J_EEA",
            cpuModel = "Snapdragon 8 Elite Leading Version", gpuModel = "Adreno 830",
            androidVersion = "15", sdkVersion = 35,
            screenWidth = 1116, screenHeight = 2480, densityDpi = 480,
            maxRefreshRate = 144f,
            fingerprint = "nubia/NX789J_EEA/NX789J:15/AP3A.240905.015.A1/REDMAGICOS10.0.2_EEA:user/release-keys"
        ),
        DeviceProfile(
            id = "huawei_mate60pro", displayName = "华为 Mate 60 Pro",
            brand = "HUAWEI", model = "ALN-AL80", manufacturer = "HUAWEI",
            device = "HWALN", board = "kiran9000s", hardware = "kirin9000s", product = "ALN-AL80",
            cpuModel = "Kirin 9000S", gpuModel = "Maleoon 910",
            androidVersion = "14", sdkVersion = 34,
            screenWidth = 1260, screenHeight = 2720, densityDpi = 560,
            maxRefreshRate = 120f,
            fingerprint = "HUAWEI/ALN-AL80/HWALN:14/SP2ADEV.240800.005/ALN-AL80_4.2.0.123C00:user/release-keys"
        )
    )

    fun findById(id: String) = BUILT_IN.find { it.id == id }
    fun getAllIds() = BUILT_IN.map { it.id }
    fun getAllNames() = BUILT_IN.map { it.displayName }
}
