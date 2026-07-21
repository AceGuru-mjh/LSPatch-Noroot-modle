package com.mjh.shizukufix.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.ui.components.FeatureCard
import com.mjh.shizukufix.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: ShizukuFixConfig, onConfigChange: (ShizukuFixConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Scene 权限申请修复",
            "Hook Scene 启动流程，主动向 Shizuku 发送 REQUEST_PERMISSION（Path A）",
            cfg.sceneFixEnabled,
            { val nc = cfg.copy(sceneFixEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "授权列表注入",
            "向 Shizuku getInstalledApplications/getInstalledPackages 等返回值注入 Scene（Path B）",
            cfg.listInjectorEnabled,
            { val nc = cfg.copy(listInjectorEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 变体检测",
            "扫描已安装应用，识别 Shizuku 变体包名（含第三方 fork），辅助排错",
            cfg.variantDetectEnabled,
            { val nc = cfg.copy(variantDetectEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Shizuku 系统级授权（adb级，需 Shizuku 运行）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "pm grant 真正授予权限", "通过 Shizuku 执行 pm grant + am broadcast，真正授予 Scene Shizuku API 权限（非模拟UI点击）",
            cfg.pmGrantEnabled,
            { val nc = cfg.copy(pmGrantEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 服务保活",
            "Hook ShizukuService.onStartCommand，周期检测存活并尝试 startService 重启",
            cfg.serviceWatchdogEnabled,
            { val nc = cfg.copy(serviceWatchdogEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "自动授权辅助",
            "Hook Shizuku 授权对话框，检测到来自 Scene 的请求时自动点击允许按钮",
            cfg.autoGrantHelperEnabled,
            { val nc = cfg.copy(autoGrantHelperEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "隐藏模块自身",
            "Hook Scene PackageManager 查询，过滤掉本模块和 LSPosed/Magisk 等敏感包名",
            cfg.hideFromSceneEnabled,
            { val nc = cfg.copy(hideFromSceneEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(40.dp))
    }
}
