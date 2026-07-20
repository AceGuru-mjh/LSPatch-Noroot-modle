package com.gameunlocker.noroot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
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
import com.gameunlocker.noroot.XposedLoader
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.ConfigManager

@Composable
fun HomeScreen(cfg: GameConfig, onConfigChange: (GameConfig) -> Unit) {
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
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "GameUnlocker NoRoot",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "v${XposedLoader.VERSION}",
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
                    "开启后，所有已启用的游戏优化功能将在目标游戏中生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
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
                Text("1. 在「功能」页勾选需要的优化项与目标帧率", style = MaterialTheme.typography.bodySmall)
                Text("2. 在 LSPosed/LSPatch 中勾选目标游戏作用域", style = MaterialTheme.typography.bodySmall)
                Text("3. 强制停止目标游戏后重新打开生效", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "免 Root 版仅应用进程内 Java 层 Hook，" +
                    "不含温控屏蔽/GPU 调频/系统属性修改（这些仅在 Root 版提供）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                ConfigManager.resetAll()
                onConfigChange(GameConfig(packageName = "global"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认配置")
        }
    }
}
