package com.privacyguard.noroot.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.ui.components.FeatureCard
import com.privacyguard.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: PrivacyConfig, onConfigChange: (PrivacyConfig) -> Unit) {
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
            "设备ID伪造", "IMEI/AndroidID/MAC/Serial 等设备标识随机伪造",
            cfg.deviceIdSpoofEnabled,
            { val nc = cfg.copy(deviceIdSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "剪贴板保护", "监控并可选阻断应用读取剪贴板",
            cfg.clipboardGuardEnabled,
            { val nc = cfg.copy(clipboardGuardEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "剪贴板读取拦截", "完全阻断应用读取剪贴板（可能影响粘贴功能）",
            cfg.clipboardBlockRead,
            { val nc = cfg.copy(clipboardBlockRead = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "权限检查欺骗", "让应用误以为危险权限未授予，触发降级行为",
            cfg.permissionSpoofEnabled,
            { val nc = cfg.copy(permissionSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "GPS位置伪造", "伪造经纬度坐标（下方可调）",
            cfg.locationSpoofEnabled,
            { val nc = cfg.copy(locationSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "传感器伪造", "加速度/陀螺仪返回静态或加噪数据，防指纹",
            cfg.sensorFakerEnabled,
            { val nc = cfg.copy(sensorFakerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "广告ID屏蔽", "屏蔽 Google Advertising ID 获取",
            cfg.advertisingIdBlockEnabled,
            { val nc = cfg.copy(advertisingIdBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "已安装应用可见性伪装", "从查询结果中隐藏 Xposed/Shizuku/Magisk 等敏感应用",
            cfg.packageVisibilitySpoofEnabled,
            { val nc = cfg.copy(packageVisibilitySpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网络信息伪造", "伪造本机IP/DNS/MAC，防网络指纹追踪",
            cfg.networkInfoSpoofEnabled,
            { val nc = cfg.copy(networkInfoSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "屏幕参数防指纹", "伪造分辨率/密度/刷新率，防屏幕特征追踪",
            cfg.screenMetricsSpoofEnabled,
            { val nc = cfg.copy(screenMetricsSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "存储路径混淆", "混淆外部存储路径查询结果",
            cfg.storagePathSpoofEnabled,
            { val nc = cfg.copy(storagePathSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(16.dp))
        Text("v1.0.6 新增（对标 HideMyAndroid）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "应用安装状态伪造", "Hook getPackageInfo，隐藏LSPosed/Shizuku/Magisk等敏感应用",
            cfg.installStatusSpoofEnabled,
            { val nc = cfg.copy(installStatusSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Mock位置系统级", "Hook LocationManager 全局返回伪造坐标（含 requestLocationUpdates）",
            cfg.mockLocationSystemLevelEnabled,
            { val nc = cfg.copy(mockLocationSystemLevelEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        if (cfg.locationSpoofEnabled) {
            Text("位置参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("纬度: ${"%.4f".format(cfg.spoofLatitude)}", style = MaterialTheme.typography.bodySmall)
            val spoofLatitudeState = remember(cfg) { mutableFloatStateOf(cfg.spoofLatitude.toFloat()) }
            Slider(
                value = spoofLatitudeState.floatValue,
                onValueChange = { spoofLatitudeState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(spoofLatitude = spoofLatitudeState.floatValue.toDouble())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = -90f..90f
            )
            Text("经度: ${"%.4f".format(cfg.spoofLongitude
            )}", style = MaterialTheme.typography.bodySmall)
            val spoofLongitudeState = remember(cfg) { mutableFloatStateOf(cfg.spoofLongitude.toFloat()) }
            Slider(
                value = spoofLongitudeState.floatValue,
                onValueChange = { spoofLongitudeState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(spoofLongitude = spoofLongitudeState.floatValue.toDouble())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = -180f..180f
            )
        }

        if (cfg.sensorFakerEnabled) {
            Spacer(Modifier.height(16.dp)
            )
            Text("传感器噪声级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val levels = listOf("静态", "加噪1", "加噪2", "加噪3")
            Text("当前: ${levels[cfg.sensorNoiseMode]}", style = MaterialTheme.typography.bodySmall)
            val sensorNoiseModeState = remember(cfg) { mutableFloatStateOf(cfg.sensorNoiseMode.toFloat()) }
            Slider(
                value = sensorNoiseModeState.floatValue,
                onValueChange = { sensorNoiseModeState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(sensorNoiseMode = sensorNoiseModeState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 0f..3f, steps = 2
            )
    }
}

}
