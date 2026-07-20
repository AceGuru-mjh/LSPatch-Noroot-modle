package com.gameunlocker.noroot.models

/**
 * 设备机型伪装参数
 * 仅用于应用内 Build 属性伪装，不涉及系统级属性修改
 *
 * 硬性限制：本模块仅作用于被 LSPatch 修补的单款游戏进程，
 * 无法全局伪装手机型号，退出游戏后恢复真实参数。
 */
data class DeviceProfile(
    val id: String,
    val displayName: String,
    val brand: String,
    val model: String,
    val manufacturer: String,
    val device: String,
    val board: String = "",
    val hardware: String = "",
    val product: String = "",
    val cpuModel: String = "Snapdragon 8 Elite",
    val gpuModel: String = "Adreno 750",
    val androidVersion: String = "14",
    val sdkVersion: Int = 34,
    val screenWidth: Int = 1440,
    val screenHeight: Int = 3200,
    val densityDpi: Int = 560,
    val maxRefreshRate: Float = 144f,
    val fingerprint: String = "",
    val isCustom: Boolean = false
)
