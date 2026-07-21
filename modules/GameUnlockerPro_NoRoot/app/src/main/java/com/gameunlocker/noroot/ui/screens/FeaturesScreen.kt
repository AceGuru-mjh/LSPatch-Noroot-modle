package com.gameunlocker.noroot.ui.screens

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.ui.components.FeatureCard
import com.gameunlocker.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: GameConfig, onConfigChange: (GameConfig) -> Unit) {
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
            "机型伪装", "伪装 Build/SystemProperties 为旗舰机型，规避机型检测",
            cfg.deviceSpoofEnabled,
            { val nc = cfg.copy(deviceSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "帧率解锁", "Hook Display/Surface/Unity/Unreal 强制目标帧率",
            cfg.frameRateUnlockEnabled,
            { val nc = cfg.copy(frameRateUnlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "环境隐藏", "隐藏 Xposed/Shizuku/LSPatch/Magisk 等敏感环境",
            cfg.detectionHideEnabled,
            { val nc = cfg.copy(detectionHideEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "进程优化", "提升渲染线程优先级 + Hook 热状态回调（仅应用层）",
            cfg.processOptimizeEnabled,
            { val nc = cfg.copy(processOptimizeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "分辨率伪装", "伪装 Display/DisplayMetrics 为 2K，强制加载高清材质",
            cfg.resolutionSpoofEnabled,
            { val nc = cfg.copy(resolutionSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Shizuku 系统级调优（adb级，需 Shizuku 运行）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 系统调优", "dumpsys SurfaceFlinger 检测显示能力 / wm size/density 分辨率 / cmd 电池优化豁免",
            cfg.shizukuSystemTuneEnabled,
            { val nc = cfg.copy(shizukuSystemTuneEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "触摸采样率提升", "Hook InputEventReceiver/InputQueue 提升事件线程优先级",
            cfg.touchSamplingBoostEnabled,
            { val nc = cfg.copy(touchSamplingBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网络延迟优化", "Hook Socket 设置 TCP_NODELAY + 扩大接收缓冲区",
            cfg.networkLatencyOptEnabled,
            { val nc = cfg.copy(networkLatencyOptEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音频优先级提升", "Hook AudioTrack 设 PERFORMANCE_MODE_LOW_LATENCY + 线程优先级",
            cfg.audioPriorityBoostEnabled,
            { val nc = cfg.copy(audioPriorityBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内存整理", "Hook MemoryInfo/TrimMemory 让游戏看到更充足内存 + GC 提示",
            cfg.memoryDefragEnabled,
            { val nc = cfg.copy(memoryDefragEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        if (cfg.frameRateUnlockEnabled) {
            Text("目标帧率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.targetFps} fps", style = MaterialTheme.typography.bodySmall)
            val targetFpsState = remember(cfg) { mutableFloatStateOf(cfg.targetFps.toFloat()) }
            Slider(
                value = targetFpsState.floatValue,
                onValueChange = { targetFpsState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(targetFps = targetFpsState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 60f..160f, steps = 19
            )
    }
}

}
