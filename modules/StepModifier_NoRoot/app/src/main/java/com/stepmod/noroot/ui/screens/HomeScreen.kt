package com.stepmod.noroot.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stepmod.noroot.XposedLoader
import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.ConfigManager

@Composable
fun HomeScreen(
    cfg: StepConfig,
    onConfigChange: (StepConfig) -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val ctx = LocalContext.current
    var importMessage by remember { mutableStateOf<String?>(null) }

    // 目标步数 Slider 局部状态（拖动时避免频繁写盘）
    val stepsState = remember(cfg) { mutableFloatStateOf(cfg.customSteps.toFloat()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            if (json.isBlank()) {
                importMessage = "导入失败：文件为空"
                return@rememberLauncherForActivityResult
            }
            val ok = ConfigManager.importConfig(json)
            importMessage = if (ok) {
                onConfigChange(ConfigManager.getGlobalConfig())
                "导入成功"
            } else {
                "导入失败：JSON 格式错误或解析失败"
            }
        } catch (e: Exception) {
            importMessage = "导入失败: ${e.message}"
        }
    }

    fun exportConfig() {
        try {
            val json = ConfigManager.exportConfig()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_TITLE, "StepModifier_NoRoot_config.json")
                type = "application/json"
            }
            ctx.startActivity(
                Intent.createChooser(sendIntent, "导出配置到...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            importMessage = "导出失败: ${e.message}"
        }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "切换暗色/亮色模式",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "StepModifier NoRoot",
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

        // 总开关卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("模块总开关", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "开启后，所有已启用的步数修改功能将在目标应用中生效",
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

        // 目标步数卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("目标步数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "拖动设置伪造的步数值，应用读取到的步数将替换为此值（含随机波动）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("当前: ${cfg.customSteps} 步", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.Slider(
                    value = stepsState.floatValue,
                    onValueChange = { stepsState.floatValue = it },
                    onValueChangeFinished = {
                        val nc = cfg.copy(customSteps = stepsState.floatValue.toInt())
                        ConfigManager.saveGlobalConfig(nc)
                        onConfigChange(nc)
                    },
                    valueRange = 1000f..50000f
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("使用说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("1. 在「功能」页勾选需要的步数修改项", style = MaterialTheme.typography.bodySmall)
                Text("2. 在 LSPosed/LSPatch 中勾选目标运动APP作用域", style = MaterialTheme.typography.bodySmall)
                Text("3. 强制停止目标应用后重新打开生效", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "免Root版仅应用进程内 Hook，不修改系统传感器服务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 配置管理
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("配置管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportConfig() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出配置")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导入配置")
                    }
                }
                importMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                ConfigManager.resetAll()
                importMessage = null
                onConfigChange(StepConfig(packageName = "global"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认配置")
        }
    }
}
