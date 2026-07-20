package com.batteryopt.noroot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.ConfigManager

@Composable
fun HomeScreen(cfg: BatteryConfig, onConfigChange: (BatteryConfig) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryStd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "BatteryOptimizer NoRoot",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "v${com.batteryopt.noroot.XposedLoader.VERSION}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("模块总开关", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "开启后，所有已启用的省电优化功能将在目标应用中生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (cfg.masterEnabled) "已启用" else "已禁用", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = cfg.masterEnabled,
                        onCheckedChange = {
                            val nc = cfg.copy(masterEnabled = it)
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("使用说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("1. 在「功能」页勾选需要的省电优化项", style = MaterialTheme.typography.bodySmall)
                Text("2. 在 LSPosed/LSPatch 中勾选目标应用作用域", style = MaterialTheme.typography.bodySmall)
                Text("3. 强制停止目标应用后重新打开生效", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "硬性限制：仅优化当前 APP 自身耗电行为，不冻结其他 APP / 不改系统 doze / 不修改系统属性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                ConfigManager.resetAll()
                onConfigChange(BatteryConfig(packageName = "global"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认配置")
        }
    }
}
