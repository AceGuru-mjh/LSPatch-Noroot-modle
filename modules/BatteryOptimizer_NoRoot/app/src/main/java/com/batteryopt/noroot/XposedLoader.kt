package com.batteryopt.noroot

import android.app.Application
import com.batteryopt.noroot.hooks.*
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.ConfigManager
import com.batteryopt.noroot.utils.HookConfigReader
import com.batteryopt.noroot.utils.LogStore
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * BatteryOptimizer NoRoot - Xposed 模块唯一入口
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] WakeLock  [2] Alarm  [3] Sync
 *    [4] Job       [5] Location  [6] Animation
 *    [7] Sensor
 *    [实验] BluetoothScan / CameraBackground / Vibrator
 *
 * 硬性限制（NoRoot版严格遵守）：
 *  - 仅应用进程内 Java 层 Hook
 *  - 不修改系统属性(setprop)、不写 /system /sys
 *  - 不调用 Shizuku 做真Root操作
 *  - 仅优化当前 APP 自身耗电行为，无法冻结其他 APP/不改系统 doze
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.11"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("BatteryOptimizer NoRoot v$VERSION 初始化 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
            if (lpparam.packageName == "android") return
            if (!lpparam.isFirstApplication) return
            val pkg = lpparam.packageName ?: return
            if (!isTargetApp(pkg)) return

            LogX.i("===== APP启动: $pkg =====")
            currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        LogX.i("配置: 总开关=${cfg.masterEnabled} wakelock=${cfg.wakeLockEnabled} " +
                "alarm=${cfg.alarmEnabled} sync=${cfg.syncEnabled} job=${cfg.jobEnabled} " +
                "location=${cfg.locationEnabled} anim=${cfg.animationEnabled} " +
                "sensor=${cfg.sensorEnabled} appOps=${cfg.appOpsRestrictEnabled} " +
                "[实验]bt=${cfg.bluetoothScanThrottleEnabled} cam=${cfg.cameraBackgroundBlockEnabled} " +
                "vib=${cfg.vibratorThrottleEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能 =====
        if (cfg.wakeLockEnabled) WakeLockHook.apply(lpparam, cfg)
        if (cfg.alarmEnabled) AlarmOptimizerHook.apply(lpparam, cfg)
        if (cfg.syncEnabled) BackgroundSyncHook.apply(lpparam, cfg)

        // ===== Shizuku 系统级（adb级 cmd appops） =====
        if (cfg.appOpsRestrictEnabled) AppOpsRestrictHook.apply(lpparam, cfg)

        if (cfg.jobEnabled) JobSchedulerHook.apply(lpparam, cfg)
        if (cfg.locationEnabled) LocationOptHook.apply(lpparam, cfg)
        if (cfg.animationEnabled) AnimationOptHook.apply(lpparam, cfg)
        if (cfg.sensorEnabled) SensorOptHook.apply(lpparam, cfg)

        // ===== 实验性功能 =====
        if (cfg.bluetoothScanThrottleEnabled) BluetoothScanThrottleHook.apply(lpparam, cfg)
        if (cfg.cameraBackgroundBlockEnabled) CameraBackgroundBlockHook.apply(lpparam, cfg)
        if (cfg.vibratorThrottleEnabled) VibratorThrottleHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
        }
    }

    /** 目标耗电大户 APP 包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm",                 // 微信
        "com.tencent.mobileqq",           // QQ
        "com.ss.android.ugc.aweme",       // 抖音
        "com.smile.gifmaker",             // 快手
        "com.taobao.taobao",              // 淘宝
        "com.jingdong.app.mall",          // 京东
        "com.xunmeng.pinduoduo",          // 拼多多
        "com.eg.android.AlipayGphone",    // 支付宝
        "com.netease.cloudmusic",         // 网易云音乐
        "com.tencent.wmusic",             // QQ音乐
        "com.zhihu.android",              // 知乎
        "com.sina.weibo",                 // 微博
        "com.netease.mail",               // 网易邮箱
        "com.tencent.androidqqmail"       // QQ邮箱
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): BatteryConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { BatteryConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) { ConfigManager.init(app); LogStore.init(app) }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app); LogStore.init(app) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
