# LSPatch NoRoot 模块合集

> 11 个基于 LSPatch 框架的免 Root Xposed 模块 · Material 3 毛玻璃悬浮球 · 三大铁律架构

<p align="center">
  <img src="https://img.shields.io/badge/Modules-11-emerald" />
  <img src="https://img.shields.io/badge/Mode-NoRoot-emerald" />
  <img src="https://img.shields.io/badge/Framework-LSPatch-emerald" />
  <img src="https://img.shields.io/badge/UI-Material%203-emerald" />
  <img src="https://img.shields.io/badge/MinSDK-26-emerald" />
  <img src="https://img.shields.io/badge/License-MIT-emerald" />
  <img src="https://img.shields.io/github/actions/workflow/status/AceGuru-mjh/LSPatch-Noroot-modle/build.yml?branch=main&label=CI&logo=github" />
  <img src="https://img.shields.io/badge/Health-86%25-brightgreen" />
</p>

## 模块列表

| 模块 | 包名 | 功能 |
|------|------|------|
| AdBlockerX_NoRoot | com.adblockerx.noroot | 广告拦截（WebView/OkHttp/黑名单/追踪器） |
| PrivacyGuard_NoRoot | com.privacyguard.noroot | 隐私保护（设备 ID/位置/传感器伪造） |
| GameUnlockerPro_NoRoot | com.gameunlocker.noroot | 游戏加速（帧率解锁/机型伪装/触控优化） |
| BatteryOptimizer_NoRoot | com.batteryopt.noroot | 省电优化（WakeLock/Alarm/Job 调度） |
| MicroXEnhancer | com.microx.enhancer | 微信 QQ 增强（防撤回/去广告/美化） |
| VipUnlocker_NoRoot | com.vipunlocker.noroot | VIP 解锁（去广告/会员主题/付费章节） |
| VideoSaver_NoRoot | com.videosaver.noroot | 视频下载（抖音/快手/小红书去水印） |
| StepModifier_NoRoot | com.stepmod.noroot | 步数修改（步数/轨迹/心率伪造） |
| AudioBoost_NoRoot | com.audioboost.noroot | 音量增强（突破上限/均衡器/Hi-Fi） |
| NotifyMaster_NoRoot | com.notifymaster.noroot | 通知管理（静音/合并/拦截营销） |
| ShizukuSceneFix | com.mjh.shizukufix | Shizuku 修复（Scene 授权列表不显示） |

## 核心特性

- **免 Root**：基于 LSPatch 框架，无需 Root 即可注入 Xposed 模块
- **Material 3 毛玻璃悬浮球**：每个模块自带可拖拽悬浮球，点击展开毛玻璃面板
- **三大铁律防秒崩**：零 import hooks/* + Class.forName 反射 + 进程双分支
- **安全 IPC**：ContentProvider 跨进程配置同步，signature 级 readPermission 保护
- **Shizuku 集成**：免 Root 下执行 adb 级系统操作

## 三大铁律

LSPatch 集成模式下，违反任一铁律会导致类加载阶段秒崩。

### 铁律 1：XposedLoader 禁止 import hooks/*

```kotlin
// 禁止
import com.xxx.hooks.FrameRateUnlockHook

// 正确：反射加载
val cls = Class.forName("com.xxx.hooks.FrameRateUnlockHook")
cls.getDeclaredMethod("apply", LoadPackageParam::class.java, GameConfig::class.java)
    .invoke(null, lpparam, cfg)
```

### 铁律 2：Hook 必须用 Class.forName() 反射

```kotlin
// 禁止：String + classLoader 重载
XposedHelpers.findAndHookMethod("com.unity3d.player.UnityPlayer", lpparam.classLoader, ...)

// 正确：传 Class 对象
val unityClass = Class.forName("com.unity3d.player.UnityPlayer")
XposedHelpers.findAndHookMethod(unityClass, "setTargetFrameRate", ...)
```

### 铁律 3：进程双分支

```kotlin
override fun handleLoadPackage(lpparam: LoadPackageParam) {
    if (lpparam.packageName == OWN_PKG) return  // 自身进程走 UI
    applyHooksViaReflection(lpparam, cfg)        // 宿主进程走 Hook
}
```

## 使用方法

### 1. 下载模块 APK

从 [Releases](https://github.com/AceGuru-mjh/LSPatch-Noroot-modle/releases) 下载需要的模块 APK 并安装。

### 2. 安装 LSPatch 管理器

从 [LSPatch GitHub](https://github.com/LSPosed/LSPatch) 下载 LSPatch 管理器。

### 3. 注入模块

1. 打开 LSPatch 管理器
2. 选择目标 APP（如微信）
3. 勾选需要启用的模块
4. 选择「集成模式」打包
5. 卸载原版 APP，安装打包后的版本

### 4. 开启悬浮球

打开模块 APP → 设置 → 悬浮球 → 授权「显示在其他应用上层」

## 构建

每个模块是独立的 Gradle 工程：

```bash
cd modules/AdBlockerX_NoRoot
gradle wrapper --gradle-version 8.2
./gradlew :app:assembleRelease
```

GitHub Actions 会自动构建全部 11 个模块并发布到 Releases。

## 技术栈

- **框架**：LSPatch（免 Root Xposed）
- **语言**：Kotlin 1.9.20
- **UI**：Jetpack Compose + Material 3
- **Xposed API**：compileOnly de.robv.android.xposed:api:82
- **Shizuku API**：compileOnly dev.rikka.shizuku:api:13.1.5
- **构建**：AGP 8.2.0 + Gradle 8.2 + JDK 17
- **minSdk**：26 (Android 8.0)
- **targetSdk**：34 (Android 14)

## 项目结构

```
modules/
├── AdBlockerX_NoRoot/          # 每个模块独立 Gradle 工程
│   ├── app/src/main/java/com/adblockerx/noroot/
│   │   ├── XposedLoader.kt     # 入口（铁律 1+2+3）
│   │   ├── core/               # ConfigProvider + ConfigClient (IPC)
│   │   ├── hooks/              # Hook 实现
│   │   ├── utils/              # ConfigManager + LogX + ShizukuHelper
│   │   ├── ui/                 # Compose + Material 3 界面
│   │   ├── activities/         # PanelActivity（毛玻璃面板）
│   │   └── services/           # FloatingBallService（悬浮球）
│   └── app/src/main/AndroidManifest.xml
├── keystore/                   # 签名
└── ...其他 10 个模块
.github/workflows/build.yml     # CI matrix 并行构建
```

## 相关链接

- **LSPatch 框架**：https://github.com/LSPosed/LSPatch
- **LSPosed（Root 版）**：https://github.com/AceGuru-mjh/LSPosed-root-modle
- **官网**：https://lspatch-noroot.vercel.app

## 开发者

**MJH** - [@AceGuru-mjh](https://github.com/AceGuru-mjh)

## License

MIT
