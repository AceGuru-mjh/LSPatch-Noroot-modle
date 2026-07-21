package com.audioboost.noroot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioboost.noroot.XposedLoader
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.ConfigManager

@Composable
fun HomeScreen(
    cfg: AudioConfig,
    onConfigChange: (AudioConfig) -> Unit,
    darkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {}
) {
    val scroll = rememberScrollState()
    val logs = remember { mutableStateListOf<String>() }
    val stats = remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("AudioBoost NoRoot", style = MaterialTheme.typography.headlineSmall)
                }
                Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "音量增强 ${stats.longValue} 次",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("模块总开关", style = MaterialTheme.typography.titleMedium)
                    Text("开启后所有功能将在目标应用生效", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = cfg.masterEnabled,
                    onCheckedChange = { newVal ->
                        val nc = cfg.copy(masterEnabled = newVal)
                        ConfigManager.saveGlobalConfig(nc)
                        onConfigChange(nc)
                    }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("实时统计", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    StatBox("增强", stats.longValue.toString(), modifier = Modifier.weight(1f))
                    StatBox("dB增益", "${stats.longValue * 6}", modifier = Modifier.weight(1f))
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { logs.clear() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清空日志")
                    }
                    OutlinedButton(
                        onClick = { /* 导出配置 */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("控制台", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${logs.size} 条", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier
                        .heightIn(max = 200.dp)
                        .padding(8.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text("暂无日志", style = MaterialTheme.typography.bodySmall)
                        } else {
                            logs.takeLast(50).forEach { log ->
                                Text(log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
