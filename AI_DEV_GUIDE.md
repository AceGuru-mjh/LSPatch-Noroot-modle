# LSP-Model AI 开发指南（图谱与规则）

> 本文件是给 AI Agent 阅读的项目宪法。每次介入本项目前**必须完整阅读本文件**，再决定如何改动。
> 人类开发者也可参考，但首要受众是 AI。

---

## 0. 元规则（最高优先级）

1. **每次大幅优化只推进一个小版本**：v1.0.1 → v1.0.2 → v1.0.3。禁止跨大版本（不要直接跳 v2.0）。
2. **改任何代码前，先跑体检**：`python3 scripts/healthcheck.py`（见 §6）。体检报告决定优先级。
3. **禁止空壳**：每个 `hooks/*.kt` 文件必须有真实 `findAndHookMethod`/`XposedBridge.hook*` 调用，或明确标注为"工具类"（见 §4.3）。
4. **NoRoot 版与 Root 版边界不可混淆**（见 §3）。
5. **core/ui 隔离架构不可违反**（见 §1.4）—— 这是 LSPatch 集成模式防秒崩的核心。
6. **不要新建 Markdown 文档**，除非用户明确要求。本文件 + Web 体检工具已足够。
7. **提交信息用中文**，描述"做了什么 + 为什么"。
8. **改完必须本地编译验证**：`./gradlew :app:compileReleaseKotlin`（每个改动模块都要过）。
9. **Windows 用户警告**：PowerShell 的 `Set-Content` 会破坏 UTF-8 中文编码。只使用 `[System.IO.File]::ReadAllText/WriteAllText` 操作 .kt 文件。

---

## 1. 项目架构图谱

### 1.1 仓库结构
```
lsp-model/
├── modules/                          # 10 个独立 Gradle 工程
│   ├── keystore/mjh-release.jks      # 统一签名 (alias=mjh, 密码通过环境变量或local.properties提供)
│   ├── ShizukuSceneFix/              # 通用 (Kotlin)
│   ├── GameUnlockerPro_NoRoot/       # LSPatch 免Root
│   ├── GameUnlockerPro_Root/         # LSPosed Root
│   ├── MicroXEnhancer/               # LSPatch 微信QQ增强
│   ├── PrivacyGuard_NoRoot/          # LSPatch 隐私
│   ├── PrivacyGuard_Root/            # LSPosed 隐私
│   ├── AdBlockerX_NoRoot/            # LSPatch 广告
│   ├── AdBlockerX_Root/              # LSPosed 广告
│   ├── BatteryOptimizer_NoRoot/      # LSPatch 省电
│   └── BatteryOptimizer_Root/        # LSPosed 省电
├── .github/workflows/build.yml       # matrix 并行编译 + Release
├── scripts/healthcheck.py            # 体检脚本
├── web/                              # Next.js 体检工具 + 开发指南
└── AI_DEV_GUIDE.md                   # 本文件
```

### 1.2 单模块内部结构（强制）
```
模块名/
├── build.gradle.kts                  # AGP 8.2.0 + Kotlin 1.9.20
├── settings.gradle.kts               # repositories 含 api.xposed.info + jitpack
├── gradle.properties                 # jvmargs -Xmx1536m
├── gradle/wrapper/gradle-wrapper.properties  # gradle-8.2-bin.zip
└── app/
    ├── build.gradle.kts              # 见 §2.1
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml       # 4 个 xposed meta-data + MainActivity
        ├── assets/xposed_init        # 入口类全限定名
        └── java/<包路径>/
            ├── XposedLoader.kt       # 唯一入口 (IXposedHookLoadPackage + IXposedHookZygoteInit)
            ├── hooks/                # Hook 逻辑 (object 类)
            ├── utils/                # LogX, ConfigManager, HookConfigReader, ShizukuHelper(仅Root)
            ├── models/               # XxxConfig data class
            └── ui/
                ├── MainActivity.kt           # Compose 入口
                ├── theme/{Color,Theme}.kt    # M3 配色
                ├── screens/{Home,Features,About}Screen.kt
                └── components/FeatureCard.kt
```

### 1.4 core/ui 隔离架构（最高安全级别）

> **这是 LSPatch 集成模式防秒崩的核心规则。违反任何一条都会导致宿主 APP 直接崩溃。**

#### 1.4.1 模块内部包结构（强制）

```
java/<包路径>/
├── XposedLoader.kt       # 唯一入口 —— 禁止 import hooks/*
├── core/                  # 纯 Hook 逻辑，绝不允许引用 ui/
│   ├── ConfigProvider.kt  # ContentProvider IPC 配置服务
│   └── ConfigClient.kt    # 跨进程配置读取
├── hooks/                 # Hook 实现 (object 类)
├── utils/                 # LogX, ConfigManager, HookConfigReader 等
├── models/                # XxxConfig data class
├── ui/                    # Compose/Material3 全部 UI 代码
│   ├── MainActivity.kt
│   ├── UiInitializer.kt   # 反射加载入口
│   └── ...
└── activities/ + services/  # PanelActivity + FloatingBallService
```

#### 1.4.2 XposedLoader 强制规则（铁律）

**XposedLoader.kt 是模块入口，必须遵守以下三条铁律：**

**铁律 1：禁止 import 任何 hooks/ 或 utils/ 下的类**

```kotlin
// ❌ 绝对禁止 —— 会导致类加载阶段秒崩
import com.xxx.hooks.WebViewAdHook
import com.xxx.hooks.OkHttpAdHook
import com.xxx.utils.ConfigManager
import com.xxx.utils.EnvDetector

// ✅ 允许 —— 仅 Xposed 框架 + 模型数据类
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.xxx.models.AdBlockConfig
```

**铁律 2：所有 Hook 都必须用反射调用**

```kotlin
// ❌ 禁止 —— 直接调用
WebViewAdHook.apply(lpparam, cfg)

// ✅ 必须 —— 反射调用
Class.forName("com.xxx.hooks.WebViewAdHook", false, lpparam.classLoader)
    .getDeclaredMethod("apply", XC_LoadPackage.LoadPackageParam::class.java, AdBlockConfig::class.java)
    .invoke(null, lpparam, cfg)
```

**铁律 3：进程双分支（自身进程 vs 宿主进程）**

```kotlin
override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.processName != lpparam.packageName) return  // 跳过子进程
    
    // 分支1：模块自身进程 —— 加载 Compose UI
    if (lpparam.packageName == "com.xxx.模块包名") {
        // 反射加载 UI（不能用 import）
        Class.forName("com.xxx.ui.UiInitializer")
            .getDeclaredMethod("initAllUi", Context::class.java)
            .invoke(null, context)
        return  // 模块自身进程不加载 Hook
    }
    
    // 分支2：宿主 APP 进程 —— 纯 Hook，零 UI
    val cfg = loadConfig()
    if (!cfg.masterEnabled) return
    
    // 全部反射调用（不 import）
    tryInvoke("com.xxx.hooks.SomeHook", "apply", lpparam.classLoader, lpparam, cfg)
}
```

#### 1.4.3 为什么必须这样做（秒崩原理）

LSPatch 集成模式将模块 APK 打包进宿主 APP。当宿主启动时：

1. JVM 类加载器加载 `XposedLoader` 类
2. 如果 `XposedLoader` 有 `import com.xxx.hooks.WebViewAdHook`，JVM 必须解析这个类
3. `WebViewAdHook` 的类定义引用了 OkHttp、Compose、Material3 等
4. 宿主 APP 的类路径可能与模块打包的版本冲突 → **ClassLoader 直接崩溃**
5. 此时还没执行到 `handleLoadPackage`，try-catch 无效

**用反射后**：
1. JVM 加载 `XposedLoader` —— 只解析 5 个 import（框架+模型）
2. `handleLoadPackage` 运行时才调用 `Class.forName("hooks.WebViewAdHook")`
3. 每个 Hook 独立 try-catch，一个挂了不影响其他的

#### 1.4.4 编写新模块时的检查清单

- [ ] `XposedLoader.kt` 的 import 列表只有 `<10 行`
- [ ] 没有任何 `import *.hooks.*` 或 `import *.utils.*`
- [ ] 所有 Hook 调用使用 `tryInvoke()` 反射
- [ ] 所有工具类初始化使用 `Class.forName()` 反射
- [ ] 模块自身进程和宿主进程是两个独立分支
- [ ] `core/` 包下没有任何 Compose/Material3 import
- [ ] 宿主进程分支不调用任何 UI 相关代码

#### 1.4.5 集成模式配置通信

宿主进程需要读取用户配置时，通过 ContentProvider IPC（不是 SharedPreferences 跨进程）：

```kotlin
// core/ConfigProvider.kt —— 模块 APP 端提供
class ConfigProvider : ContentProvider() {
    override fun query(...): Cursor? {
        val sp = context?.getSharedPreferences("prefs", MODE_PRIVATE)
        // 返回所有配置键值对
    }
}

// core/ConfigClient.kt —— 宿主进程端读取
object ConfigClient {
    fun readMasterSwitch(ctx: Context): Boolean {
        val cursor = ctx.contentResolver.query(
            Uri.parse("content://com.xxx.module.configprovider"), ...)
        // 解析 cursor 返回开关值
    }
}
```

> 原因：Android 13+ 沙箱限制，跨进程 SharedPreferences 不可靠；ContentProvider 是系统级 IPC，稳定。

#### 1.4.6 新增 bug 模式（已踩坑）

| # | Bug | 症状 | 修复 |
|---|-----|------|------|
| 11 | XposedLoader import hooks.* | 集成模式秒崩（还没到 handleLoadPackage） | 全部改为反射加载 |
| 12 | host 进程调用 Compose | 嵌入后宿主打不开 | 进程双分支 + UiInitializer 反射 |
| 13 | SharedPreferences 跨进程读 | Android 13+ 读不到 | 改用 ContentProvider IPC |
| 14 | Plugin 进程无过滤 | 微信/QQ小程序进程崩 | `processName != packageName` return |
| 15 | Windows Set-Content 破坏 UTF-8 | 中文变乱码编译失败 | 只用 ReadAllText/WriteAllText + UTF8 BOM |
| 16 | config 类 import 没问题 | —— | 模型 data class 可以 import（无依赖） |
```
用户操作 UI (Compose)
  → MainActivity 读写 ConfigManager (SharedPreferences MODE_WORLD_READABLE)
  → 持久化到 /data/data/<模块包名>/shared_prefs/
  ↓
目标 APP 启动 → LSPosed/LSPatch 加载 XposedLoader
  → HookConfigReader.readGlobal() (XSharedPreferences 跨进程读)
  → 回退 ConfigManager.getGlobalConfig() (LSPatch 本地模式同进程读)
  → 按配置应用各 Hook
```

---

## 2. 构建配置规范（不可变）

### 2.1 app/build.gradle.kts 模板
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "<包名>"
    compileSdk = 34
    defaultConfig {
        applicationId = "<包名>"
        minSdk = 26
        targetSdk = 34
        versionCode = 1              // 永远为 1，用 versionName 表达版本
        versionName = "1.0.1"        // 小版本递增
    }
    signingConfigs {
        create("release") {
            storeFile = file("../../keystore/mjh-release.jks")
            storePassword = System.getenv("MJH_STORE_PASSWORD")?.takeIf { it.isNotEmpty() } ?: error("MJH_STORE_PASSWORD not set")
            keyAlias = System.getenv("MJH_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "mjh"
            keyPassword = System.getenv("MJH_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: error("MJH_KEY_PASSWORD not set")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.4" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("dev.rikka.shizuku:api:13.1.5")        // NoRoot 版也加
    compileOnly("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // Compose 直接版本号，禁用 BOM（沙箱 BOM 解析失败）
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-graphics:1.5.4")
    implementation("androidx.compose.foundation:foundation:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.navigation:navigation-compose:2.7.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

### 2.2 禁止事项
- ❌ 不要用 Compose BOM（`platform("androidx.compose.bom:...")`），沙箱内解析失败
- ❌ 不要在 `defaultConfig` 里写 `metaData {}` DSL 块，AGP 无此 DSL
- ❌ 不要 `implementation` Xposed API，必须 `compileOnly`（运行时由框架提供）
- ❌ 不要生成 `gradlew`（GitHub Actions 会自动生成）
- ❌ 不要改 `compileSdk`/`minSdk`/`jvmTarget`/AGP/Kotlin 版本

---

## 3. NoRoot vs Root 权限边界（铁律）

### 3.1 LSPatch 合规规范（NoRoot 版强制，v1.0.8+）

NoRoot 版模块面向 LSPatch 免 Root 环境，必须遵循以下 LSPatch 专属规范：

1. **xposedminversion = 93**（非 82）。LSPatch v0.6.1+ 支持 API 93+，设 82 会导致部分版本不识别。
2. **Manifest 必须声明 FOREGROUND_SERVICE 权限**（避免 Android 9+ 启动崩溃）。
3. **XposedLoader.handleLoadPackage 开头必须加两行过滤**：
   ```kotlin
   if (lpparam.packageName == "android") return       // 跳过系统进程
   if (!lpparam.isFirstApplication) return             // 仅主进程，避免子进程 ClassLoader 隔离崩溃
   ```
4. **不 Hook system_server**：NoRoot 版仅 Hook 目标 APP 自身进程内的类。
   - 注：Hook `android.app.Application.onCreate`、`android.os.PowerManager` 等 framework 类**是允许的**（目标 APP 进程能访问这些类），规范真正禁止的是 Hook system_server 进程。
5. **不调用 Root API**：无 `/system/bin/su`、无 `setenforce 0`、无 `/system` 分区写操作。
6. **SharedPreferences 独立文件名**：每个模块用带前缀的 prefs 名（如 `privacy_guard_prefs`），避免与宿主冲突。
7. **资源命名空间**：build.gradle.kts 已声明 `namespace`，资源 ID 自动隔离。

### 3.2 NoRoot vs Root 能力对照


| 能力 | NoRoot 版 | Root 版 |
|------|-----------|---------|
| 应用进程内 Java 层 Hook | ✅ | ✅ |
| Shizuku adb 级命令（settings put/dumpsys/pm） | ✅ | ✅ |
| `setprop` 修改系统属性 | ❌ | ✅ |
| 写 `/system` `/sys` `/proc` 系统文件 | ❌ | ✅ |
| Magisk overlay (`/data/adb/modules/...`) | ❌ | ✅ |
| `pm revoke` 真实回收权限 | ❌ | ✅ |
| 修改内核温控/CPU governor 节点 | ❌ | ✅ |
| Hook system_server | ❌ | ✅ |

**判断规则**：如果一个操作需要 `su` 或 Magisk root，只能在 Root 版；如果只需 Shizuku adb 级授权，NoRoot 版也可。

---

## 4. Hook 实现规范

### 4.1 Hook 类模板
```kotlin
object XxxHook {
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: XxxConfig) {
        if (!cfg.xxxEnabled) return
        LogX.i("Xxx Hook 启动")
        hookTargetMethod(lpparam)
    }

    private fun hookTargetMethod(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.xxx.TargetClass", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(cls, "targetMethod", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        // 修改参数或返回值
                    }
                })
            LogX.hookSuccess("TargetClass", "targetMethod")
        } catch (e: Exception) {
            LogX.hookFailed("TargetClass", "targetMethod", e)
        }
    }
}
```

### 4.2 命令执行型 Hook（Root 版常见）
通过 Shizuku 执行系统命令，不调用 `findAndHookMethod`，但**必须**在 `apply()` 里 Hook `Application.onCreate` 或广播接收器来触发命令执行：
```kotlin
object SystemDozeHook {
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.dozeEnabled) return
        // Hook Application.onCreate 触发初始化
        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    applyDozeParams()  // 执行 Shizuku 命令
                    registerScreenReceiver(p.thisObject as Context)
                }
            })
    }
}
```
**体检判定**：命令执行型 Hook 至少要有 1 个 `findAndHookMethod`（用于触发），否则判为空壳。

### 4.3 工具类（非 Hook）
纯工具类（如 `HostsFilterHook` 提供 `isBlocked()` 查询）放在 `utils/` 目录，不要放 `hooks/`，类名不带 Hook 后缀：
```kotlin
// utils/HostsFilter.kt（不是 hooks/HostsFilterHook.kt）
object HostsFilter {
    fun isBlocked(host: String): Boolean { ... }
}
```
若已存在 `hooks/HostsFilterHook.kt`，迁移到 `utils/` 并更新所有引用。

### 4.4 禁止模式
- ❌ `hooks/` 目录下只有 log 没有 `findAndHookMethod` 的文件（空壳）
- ❌ `findAndHookMethod` 写在 `try` 里但 `catch` 啥都不干（静默失败）
- ❌ Hook 目标类名硬编码字符串拼错（用 `findClassIfExists` + null 检查）
- ❌ 在 Hook 回调里做重 IO（阻塞主线程）

---

## 5. Compose UI 规范（已踩坑，必读）

### 5.1 状态管理（核心 bug 来源）
```kotlin
// ❌ 错误：直接改 data class 字段不触发重组
FeatureCard("X", cfg.xEnabled, { cfg.xEnabled = it; onConfigChange(cfg) })
// 开关点了没反应，因为 cfg 是同一个引用，mutableStateOf 不通知

// ✅ 正确：用 copy() 创建新实例
FeatureCard("X", cfg.xEnabled, {
    val nc = cfg.copy(xEnabled = it)
    ConfigManager.saveGlobalConfig(nc)
    onConfigChange(nc)
})
```

### 5.2 Slider 流畅度
```kotlin
// ❌ 错误：onValueChange 每次写 SharedPreferences，卡顿
Slider(value = cfg.x.toFloat(),
    onValueChange = { cfg = cfg.copy(x = it); ConfigManager.save(cfg) })

// ✅ 正确：local state + onValueChangeFinished 提交
val xState = remember(cfg) { mutableFloatStateOf(cfg.x.toFloat()) }
Slider(
    value = xState.floatValue,
    onValueChange = { xState.floatValue = it },
    onValueChangeFinished = {
        val nc = cfg.copy(x = xState.floatValue.toInt())
        ConfigManager.saveGlobalConfig(nc)
        onConfigChange(nc)
    }
)
```

### 5.3 三栏布局
- 竖屏：`NavigationBar`（底部）
- 横屏：`NavigationRail`（侧边）
- 判断：`LocalConfiguration.current.screenWidthDp > screenHeightDp`

### 5.4 关于页必含
- 开发者：**MJH**
- 项目地址：`github.com/AceGuru-mjh/LSPatch-Noroot-modle`
- 功能简介 + 免责声明

---

## 6. 体检脚本（`scripts/healthcheck.py`）

### 6.1 体检维度
1. **基础**：文件结构、xposed_init、AndroidManifest meta-data、import 完整性
2. **构建**：build.gradle.kts 配置一致性（签名、Compose 版本、jvmTarget）
3. **Hook 实现**：每个 `hooks/*.kt` 的 `findAndHookMethod` 调用数、try-catch、TODO
4. **空壳检测**：0 调用 → 空壳；<2 调用且 catch 多 → 弱实现
5. **UI 状态**：搜索 `cfg.\w+ = it`（直接改字段 bug）
6. **注释安全**：`/* */` 平衡、注释里的 `/*` `*/` 误匹配

### 6.2 输出
```json
{
  "version": "1.0.1",
  "timestamp": "2026-07-19T...",
  "modules": [
    {
      "name": "PrivacyGuard_NoRoot",
      "version": "1.0.1",
      "hooks": [
        {"file": "DeviceIdSpoofHook.kt", "hookCalls": 12, "status": "ok"},
        {"file": "NetworkInfoSpoofHook.kt", "hookCalls": 0, "status": "hollow"}
      ],
      "uiBugs": 0,
      "configConsistency": "ok"
    }
  ]
}
```

### 6.3 执行
```bash
python3 scripts/healthcheck.py > web/public/health-report.json
```
Web 工具读取该 JSON 渲染报告。

---

## 7. 版本推进规则

### 7.1 何时推进版本
- 修复 ≥3 个空壳 Hook → +0.0.1
- 修复 UI bug（开关/滑块） → +0.0.1
- 新增功能模块 → +0.0.1
- 仅改注释/文档 → 不推进

### 7.2 推进步骤
1. 改 `app/build.gradle.kts` 的 `versionName`
2. 改 `.github/workflows/build.yml` 的 `tag_name` 和 `name`
3. 提交信息注明版本号
4. 推送触发 Actions 自动发布 Release

### 7.3 禁止
- ❌ 跨大版本（v1.0.1 → v2.0）
- ❌ 同时改 `versionCode`（永远为 1）
- ❌ 删除已有 Release（保留历史）

---

## 8. 常见 Bug 模式（已踩坑库）

| # | Bug | 症状 | 修复 |
|---|-----|------|------|
| 1 | `cfg.X = it` | 开关点不动 | `cfg.copy(X = it)` |
| 2 | 注释里 `/sys/*` | 未闭合块注释，编译错 | 改为 `/sys` |
| 3 | 注释里 `cpu*/cpufreq` | `*/` 提前关闭文档注释 | 改为 `cpuN/cpufreq` |
| 4 | Compose BOM | 依赖解析失败 | 直接版本号 |
| 5 | `setAdditionalInstanceProperty` | Xposed API jar 无此方法 | 用 `WeakHashMap` 自实现 |
| 6 | `MODE_WORLD_READABLE` | Android 7+ 抛异常 | try-catch 回退 `MODE_PRIVATE` |
| 7 | `findViewById(id)` 泛型 | 类型推断失败 | `findViewById<View>(id)` |
| 8 | lambda 当 XC_MethodHook | 类型不匹配 | `object : XC_MethodHook() {...}` |
| 9 | Hook 里 `return@label` | label 不存在 | 改为 `return` |
| 10 | slider 缺 `remember` import | Unresolved reference | 补 import |

---

## 9. Web 体检工具（`web/`）

Next.js 应用，功能：
1. **首页**：10 模块总览卡片（版本、Hook 数、空壳数、状态色）
2. **模块详情**：列出所有 Hook，标注 ok/weak/hollow，可展开看代码
3. **开发指南页**：渲染本文件（AI_DEV_GUIDE.md）
4. **版本历史**：记录每次小版本优化的变更

### 9.1 数据源
- `web/public/health-report.json`：体检脚本输出
- `web/public/changelog.json`：版本变更记录（手动维护）

### 9.2 路由
- `/` 首页
- `/module/[name]` 模块详情
- `/guide` 开发指南
- `/changelog` 版本历史

---

## 10. AI Agent 工作流

当被要求"优化""修复""体检"本项目时，按以下顺序：

1. **读本文件**（你正在做）
2. **读 worklog**：`/home/z/my-project/worklog.md` 了解历史
3. **跑体检**：`python3 scripts/healthcheck.py`
4. **定位最高优先级问题**：空壳 > UI bug > 弱实现 > 文档
5. **修复**：遵循 §4（Hook 规范）+ §5（UI 规范）
6. **本地编译验证**：每个改动模块 `compileReleaseKotlin`
7. **更新 worklog**：追加 Task ID + 变更摘要
8. **推进版本**（§7）：改 versionName + workflow tag
9. **推送**：触发 Actions 自动出 APK
10. **验证 Actions 成功**：用 GitHub API 查 run 状态

---

## 11. 联系点

- 仓库：`github.com/AceGuru-mjh/LSPatch-Noroot-modle`
- 开发者：MJH
- 签名：`mjh-release.jks` (alias=mjh, 密码通过 MJH_STORE_PASSWORD / MJH_KEY_PASSWORD 环境变量提供)

---

*本文件由 MJH 维护，AI Agent 每次介入前必读。最后更新：v1.2.0 (core/ui 隔离架构)
