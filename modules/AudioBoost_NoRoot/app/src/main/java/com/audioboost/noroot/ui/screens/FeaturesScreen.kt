package com.audioboost.noroot.ui.screens

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
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.ui.components.FeatureCard
import com.audioboost.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: AudioConfig, onConfigChange: (AudioConfig) -> Unit) {
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
            "音量增强", "Hook AudioTrack/MediaPlayer.setVolume 放大播放音量�?00%~300%�?,
            cfg.volumeBoostEnabled,
            { val nc = cfg.copy(volumeBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "低音增强", "Hook AudioEffect.BassBoost.setStrength 提升低音强度�?%~100%�?,
            cfg.bassBoostEnabled,
            { val nc = cfg.copy(bassBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "均衡�?, "Hook AudioEffect.Equalizer.setBandLevel 调整 5 段均衡器频段增益",
            cfg.equalizerEnabled,
            { val nc = cfg.copy(equalizerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Shizuku 硬件增强（adb级，需 Shizuku 运行�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "tinymix 硬件音频桥接", "通过 Shizuku 执行 tinymix 直接设置 ALSA 混音器（扬声�?耳机/麦克�?低音强化�?,
            cfg.tinymixEnabled,
            { val nc = cfg.copy(tinymixEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "扬声器增�?, "Hook AudioManager.getStreamMaxVolume 突破应用层音量上限显�?,
            cfg.speakerBoostEnabled,
            { val nc = cfg.copy(speakerBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "麦克风增益增�?, "Hook AudioRecord.read 放大 PCM 样本，提升录音音�?,
            cfg.micBoostEnabled,
            { val nc = cfg.copy(micBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音质增强", "Hook MediaFormat/AudioRecord.Builder 提升采样�?位深到高保真",
            cfg.audioQualityEnhanceEnabled,
            { val nc = cfg.copy(audioQualityEnhanceEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        if (cfg.volumeBoostEnabled) {
            Text("音量增益级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.boostLevel}% (范围 100~300)", style = MaterialTheme.typography.bodySmall)
            val boostLevelState = remember(cfg) { mutableFloatStateOf(cfg.boostLevel.toFloat()) }
            Slider(
                value = boostLevelState.floatValue,
                onValueChange = { boostLevelState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(boostLevel = boostLevelState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 100f..300f
            )
        }

        if (cfg.bassBoostEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("低音强度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.bassLevel}% (范围 0~100)", style = MaterialTheme.typography.bodySmall)
            val bassLevelState = remember(cfg) { mutableFloatStateOf(cfg.bassLevel.toFloat()) }
            Slider(
                value = bassLevelState.floatValue,
                onValueChange = { bassLevelState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(bassLevel = bassLevelState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 0f..100f
            )
        }

        if (cfg.equalizerEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("均衡器频段（5段，单位 mb，范�?-1500~+1500�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val bandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            bandLabels.forEachIndexed { i, label ->
                Text("$label: ${cfg.eqBands.getOrElse(i) { 0 }} mb", style = MaterialTheme.typography.bodySmall)
                val bandState = remember(cfg, i) { mutableFloatStateOf(cfg.eqBands.getOrElse(i) { 0 }.toFloat()) }
                Slider(
                    value = bandState.floatValue,
                    onValueChange = { bandState.floatValue = it },
                    onValueChangeFinished = {
                        val newBands = cfg.eqBands.toMutableList().also { list ->
                            while (list.size <= i) list.add(0)
                            list[i] = bandState.floatValue.toInt()
                        }
                        val nc = cfg.copy(eqBands = newBands)
                        ConfigManager.saveGlobalConfig(nc)
                        onConfigChange(nc)
                    },
                    valueRange = -1500f..1500f
                )
            }
        }

        if (cfg.speakerBoostEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("扬声器突破上�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("额外刻度: ${cfg.speakerBoostMax} (范围 0~30)", style = MaterialTheme.typography.bodySmall)
            val sbState = remember(cfg) { mutableFloatStateOf(cfg.speakerBoostMax.toFloat()) }
            Slider(
                value = sbState.floatValue,
                onValueChange = { sbState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(speakerBoostMax = sbState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 0f..30f
            )
        }

        if (cfg.micBoostEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("麦克风增益级�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.micBoostLevel}% (范围 100~300)", style = MaterialTheme.typography.bodySmall)
            val mbState = remember(cfg) { mutableFloatStateOf(cfg.micBoostLevel.toFloat()) }
            Slider(
                value = mbState.floatValue,
                onValueChange = { mbState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(micBoostLevel = mbState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 100f..300f
            )
        }

        if (cfg.audioQualityEnhanceEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("音质参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("目标采样�? ${cfg.targetSampleRate} Hz", style = MaterialTheme.typography.bodySmall)
            val srState = remember(cfg) { mutableFloatStateOf(cfg.targetSampleRate.toFloat()) }
            Slider(
                value = srState.floatValue,
                onValueChange = { srState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(targetSampleRate = srState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 44100f..192000f
            )
            Spacer(Modifier.height(8.dp))
            Text("目标位深: ${cfg.targetBitDepth} bit", style = MaterialTheme.typography.bodySmall)
            val bdState = remember(cfg) { mutableFloatStateOf(cfg.targetBitDepth.toFloat()) }
            Slider(
                value = bdState.floatValue,
                onValueChange = { bdState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(targetBitDepth = bdState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 16f..32f, steps = 1
            )
        }
    }
}
