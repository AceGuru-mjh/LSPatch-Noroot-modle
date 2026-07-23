package com.stepmod.noroot.ui.screens

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.ui.components.FeatureCard
import com.stepmod.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: StepConfig, onConfigChange: (StepConfig) -> Unit) {
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
            "步数修改总开关", "总开关，关闭后下方三项基础 Hook 全部失效",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数传感器 Hook", "拦截 SensorManager 注册监听 + onSensorChanged 修改步数读数",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数上报 Hook", "拦截各运动APP的步数上报方法（支付宝/微信/小米/华为等）",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "StepCounter Hook", "Hook android.hardware.StepCounter/StepDetector 类",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Shizuku 系统级（adb级，需 Shizuku 运行）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "ContentProvider 步数注入", "通过 Shizuku 执行 content insert 直接向运动 App 的 ContentProvider 注入步数数据",
            cfg.contentProviderInjectEnabled,
            { val nc = cfg.copy(contentProviderInjectEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数传感器阻断", "完全阻断应用注册 TYPE_STEP_COUNTER/DETECTOR（激进方案，可能导致APP读数为0）",
            cfg.sensorBlockEnabled,
            { val nc = cfg.copy(sensorBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "多APP步数同步", "Hook 跨APP步数查询（ContentResolver/Provider），统一伪造步数",
            cfg.multiAppSyncEnabled,
            { val nc = cfg.copy(multiAppSyncEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数历史伪造", "Hook SQLite/SharedPreferences 读取，伪造步数历史趋势数据",
            cfg.stepHistoryFakeEnabled,
            { val nc = cfg.copy(stepHistoryFakeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "定时步数策略", "按时间段切换目标步数，如8-12点设置5000步，13-18点设置8000步",
            cfg.scheduleStepEnabled,
            { val nc = cfg.copy(scheduleStepEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数→卡路里转换", "根据步数和体重计算卡路里消耗并注入健康APP（体重${cfg.userWeight}kg）",
            cfg.calorieCalcEnabled,
            { val nc = cfg.copy(calorieCalcEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "多设备竞赛模式", "多APP间步数同步，通过 SharedPreferences 共享步数数据",
            cfg.competitionModeEnabled,
            { val nc = cfg.copy(competitionModeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "反检测自然模式", "添加随机波动±${cfg.fluctuationRange}步并模拟休息期，避免行为被识别",
            cfg.antiDetectionEnabled,
            { val nc = cfg.copy(antiDetectionEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "GPX路线模拟", "解析GPX轨迹数据注入虚假GPS定位，模拟真实移动路径",
            cfg.gpxRouteEnabled,
            { val nc = cfg.copy(gpxRouteEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("步数参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("目标步数: ${cfg.customSteps} 步", style = MaterialTheme.typography.bodySmall)
        val stepsState = remember(cfg) { mutableFloatStateOf(cfg.customSteps.toFloat()) }
        Slider(
            value = stepsState.floatValue,
            onValueChange = { stepsState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(customSteps = stepsState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 1000f..50000f
        )

        Spacer(Modifier.height(16.dp))
        Text("随机波动: ±${cfg.randomFluctuation} 步", style = MaterialTheme.typography.bodySmall)
        val flState = remember(cfg) { mutableFloatStateOf(cfg.randomFluctuation.toFloat()) }
        Slider(
            value = flState.floatValue,
            onValueChange = { flState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(randomFluctuation = flState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 0f..1000f
        )
        Spacer(Modifier.height(32.dp))
    }
}
