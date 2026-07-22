package com.mjh.shizukufix.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mjh.shizukufix.XposedLoader
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.services.FloatingBallService
import com.mjh.shizukufix.utils.ConfigManager
import com.mjh.shizukufix.utils.LogEntry
import com.mjh.shizukufix.utils.LogStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    cfg: ShizukuFixConfig,
    onConfigChange: (ShizukuFixConfig) -> Unit,
    darkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {}
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val counter = remember { mutableLongStateOf(0L) }
    val ballRunning = remember { mutableStateOf(false) }
    val recentLogs = remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        try { counter.longValue = LogStore.getCounter() } catch (_: Throwable) {}
        try { counter.longValue = LogStore.getCounter() } catch (_: Throwable) {}
        try { recentLogs.value = LogStore.getRecentLogs(10) } catch (_: Throwable) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text("Shizuku Scene Fix", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text(
                    "已修复: ${counter.longValue} 次",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("模块总开关", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        "开启后所有功能将在目标应用生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("悬浮控制球", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        if (ballRunning.value) "运行中 - 点击右侧关闭" else "未运行 - 点击右侧启动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ballRunning.value,
                    onCheckedChange = { newVal ->
                        if (newVal) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                startFloatingBall(context)
                                ballRunning.value = true
                            }
                        } else {
                            stopFloatingBall(context)
                            ballRunning.value = false
                        }
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("实时统计", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    StatBox("已修复", counter.longValue.toString(), modifier = Modifier.weight(1f))
                    StatBox("日志", "${recentLogs.value.size}", modifier = Modifier.weight(1f))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("最近日志", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.heightIn(max = 240.dp).padding(8.dp).verticalScroll(rememberScrollState())) {
                        if (recentLogs.value.isEmpty()) {
                            Text("暂无日志", style = MaterialTheme.typography.bodySmall)
                        } else {
                            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                            recentLogs.value.takeLast(20).forEach { entry ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = fmt.format(Date(entry.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(
                                        text = "[${entry.type}] ${entry.message}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { try { LogStore.clear() } catch (_: Throwable) {}; recentLogs.value = emptyList(); counter.longValue = 0L },
                        modifier = Modifier.weight(1f)
                    ) { Text("清空日志") }
                    OutlinedButton(
                        onClick = { startFloatingBall(context); ballRunning.value = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("启动面板") }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

fun startFloatingBall(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
    }
    val intent = Intent(context, FloatingBallService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun stopFloatingBall(context: Context) {
    try {
        context.stopService(Intent(context, FloatingBallService::class.java))
    } catch (_: Throwable) {}
}
