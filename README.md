# LSPatch 免 Root 模块合集

<p align="center">
  <img src="https://img.shields.io/badge/Modules-11-brightgreen?style=flat-square" />
  <img src="https://img.shields.io/badge/Hooks-104-green?style=flat-square" />
  <img src="https://img.shields.io/badge/LSPatch-NoRoot-blue?style=flat-square" />
  <img src="https://img.shields.io/badge/Shizuku-adb级-lightgrey?style=flat-square" />
  <img src="https://img.shields.io/github/actions/workflow/status/AceGuru-mjh/LSPatch-Noroot-modle/build.yml?style=flat-square" />
  <img src="https://img.shields.io/github/v/release/AceGuru-mjh/LSPatch-Noroot-modle?style=flat-square" />
</p>

> **无需 Root** · 11 个纯应用层增强模块 · 通过 LSPatch 注入

## 模块列表

| # | 模块 | 功能 | 硬性限制 |
|---|------|------|---------|
| 1 | **ShizukuSceneFix** | Shizuku 授权修复 | pm grant / am broadcast / 直写 authorization.xml（adb级） |
| 2 | **GameUnlockerPro** | 游戏性能 | 帧率解锁 / 机型伪装 / 进程优化（无GPU调频/无温控绕过） |
| 3 | **MicroXEnhancer** | 微信/QQ增强 | 防撤回 / 自动红包 / 步数修改 / 贴纸收集 / 朋友圈清理 |
| 4 | **PrivacyGuard** | 隐私保护 | 设备ID伪造 / 位置伪造 / 传感器伪造 / 指纹防护（Java层） |
| 5 | **AdBlockerX** | 广告拦截 | WebView拦截 / OkHttp拦截 / 域名黑名单 / 自学习检测 / DOM清理 |
| 6 | **BatteryOptimizer** | 省电优化 | WakeLock优化 / Alarm降级 / Job约束 / 位置降采样 / 休眠管理 |
| 7 | **StepModifier** | 步数修改 | 传感器Hook / 上报Hook / 多APP同步 |
| 8 | **NotifyMaster** | 通知管理 | 过滤 / 防撤回通知 / 历史记录 / 美化 |
| 9 | **AudioBoost** | 音量增强 | 突破上限 / 低音增强 / 均衡器 / 麦克风增益 |
| 10 | **VipUnlocker** | VIP解锁 | Hook会员状态 / 广告移除 / 验证绕过 |
| 11 | **VideoSaver** | 视频下载 | 抖音/快手/小红书去水印 / 批量下载 |

## 权限边界（铁律）

| 能力 | NoRoot版 | Root版 |
|------|----------|--------|
| 应用进程内Java层Hook | ✅ | ✅ |
| Shizuku adb级命令 | ✅ | ✅ |
| setprop 系统属性 | ❌ | ✅ |
| 写 /sys /proc 节点 | ❌ | ✅ |
| Magisk overlay | ❌ | ✅ |
| pm revoke 权限回收 | ❌ | ✅ |
| 系统DNS/Hosts | ❌ | ✅ |

## 使用方式

```bash
# 1. 安装 LSPatch（免Root，GitHub Release 下载）
# 2. 下载模块 APK
# 3. 通过 LSPatch 将模块注入目标应用
# 4. 重启目标应用生效
```

## 下载

[📦 Releases](https://github.com/AceGuru-mjh/LSPatch-Noroot-modle/releases)

## 构建

```bash
# 单模块编译
cd modules/GameUnlockerPro_NoRoot
gradle wrapper --gradle-version 8.2
./gradlew :app:assembleRelease

# CI 自动编译全部模块（GitHub Actions）
# 推送至 main 分支自动触发 matrix 构建 + Release
```

## 开发者

**MJH** · [GitHub](https://github.com/AceGuru-mjh)
