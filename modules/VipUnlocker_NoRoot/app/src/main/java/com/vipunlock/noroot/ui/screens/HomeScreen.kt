package com.vipunlock.noroot.ui.screens

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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vipunlock.noroot.XposedLoader
import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.utils.ConfigManager

@Composable
fun HomeScreen(
    cfg: VipConfig,
    onConfigChange: (VipConfig) -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val ctx = LocalContext.current
    var importMessage by remember { mutableStateOf<String?>(null) }

    // 导入文件选择器
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

    /** 导出配置：通过 Intent.ACTION_SEND 分享 JSON 文本 */
    fun exportConfig() {
        try {
            val json = ConfigManager.exportConfig()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_TITLE, "VipUnlocker_NoRoot_config.json")
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
                // 暗色模式切换
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
                // 皇冠图标
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "VipUnlocker NoRoot",
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
                    "开启后，所有已启用的 VIP 解锁功能将在目标应用中生效",
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
                Text("1. 在「功能」页勾选需要解锁的 VIP 项目", style = MaterialTheme.typography.bodySmall)
                Text("2. 在 LSPosed/LSPatch 中勾选目标应用作用域", style = MaterialTheme.typography.bodySmall)
                Text("3. 强制停止目标应用后重新打开生效", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "免Root版仅 Hook 应用进程内 VIP 状态查询方法，不修改系统，不绕过服务端鉴权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 配置管理（导入/导出）
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
                onConfigChange(VipConfig(packageName = "global"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认配置")
        }
    }
}
