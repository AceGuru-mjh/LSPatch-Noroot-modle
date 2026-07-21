package com.mjh.shizukufix.models

/**
 * Shizuku Scene Fix 配置
 *
 * 功能说明：
 *  - 基础功能（默认开启）：修复 Scene 在 Shizuku 授权列表不显示问题
 *    [1] Scene 申请 Shizuku 权限流程修复（Path A）
 *    [2] 向 Shizuku 授权列表注入 Scene（Path B）
 *    [3] 检测 Shizuku 变体包名（兼容第三方 fork）
 *
 *  - 实验性功能（默认关闭）：
 *    [4] Shizuku 服务保活：检测 ShizukuService 存活并尝试重启
 *    [5] 自动授权辅助：Hook Shizuku 授权对话框自动确认
 *    [6] 隐藏模块自身：Hook Scene 检测，隐藏 Xposed 模块存在
 *
 * 硬性限制：
 *  - 仅 Hook Java 层，不修改系统文件
 *  - 实验性自动授权功能有风险，可能被 Shizuku 拒绝服务，请谨慎开启
 */
data class ShizukuFixConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,
    var sceneFixEnabled: Boolean = true,
    var listInjectorEnabled: Boolean = true,
    var variantDetectEnabled: Boolean = true,

    // ===== 实验性 =====
    var serviceWatchdogEnabled: Boolean = false,
    var autoGrantHelperEnabled: Boolean = false,
    var hideFromSceneEnabled: Boolean = false,

    // ===== 参数 =====
    var watchdogIntervalSec: Int = 30,          // 服务保活检测间隔（秒）
    var watchdogRestartAttempts: Int = 2,       // 最大重启尝试次数
    var autoGrantDelayMs: Long = 800,           // 自动授权延迟（毫秒）

    var lastModified: Long = 0L
)
