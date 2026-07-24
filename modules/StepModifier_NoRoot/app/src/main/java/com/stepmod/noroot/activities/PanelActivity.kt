package com.stepmod.noroot.activities

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.ConfigManager
import com.stepmod.noroot.utils.LogEntry
import com.stepmod.noroot.utils.LogStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { ConfigManager.init(applicationContext) } catch (_: Throwable) {}
        try { LogStore.init(applicationContext) } catch (_: Throwable) {}
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0.4f)
        val params = window.attributes
        params.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.CENTER
        window.attributes = params
        setContent { GlassmorphismPanel(onClose = { finish() }) }
    }

    override fun onDestroy() {
        com.stepmod.noroot.services.FloatingBallService.panelOpen = false
        super.onDestroy()
    }
}
}

@Composable
fun GlassmorphismPanel(onClose: () -> Unit) {
    val cfgState = remember { mutableStateOf<StepConfig?>(null) }
    val logsState = remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    val counter = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        try { cfgState.value = ConfigManager.getGlobalConfig() } catch (_: Throwable) {}
        try { counter.longValue = LogStore.getCounter() } catch (_: Throwable) {}
        try { logsState.value = LogStore.getRecentLogs(20) } catch (_: Throwable) {}
    }

    Box(
        modifier = Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ComposeColor.White.copy(alpha = 0.85f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("StepModifier NoRoot 控制面板", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("已处理: ${counter.longValue}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))

                cfgState.value?.let { realCfg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("总开关", style = MaterialTheme.typography.titleMedium)
                            Text(if (realCfg.masterEnabled) "已启用" else "已停用", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = realCfg.masterEnabled,
                            onCheckedChange = { newVal ->
                                try {
                                    val nc = realCfg.copy(masterEnabled = newVal)
                                    ConfigManager.saveGlobalConfig(nc)
                                    cfgState.value = nc
                                    LogStore.add("info", if (newVal) "总开关已启用" else "总开关已停用")
                                } catch (_: Throwable) {}
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("最近日志 (${logsState.value.size} 条)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = ComposeColor.Black.copy(alpha = 0.05f))) {
                    Column(modifier = Modifier.heightIn(max = 300.dp).padding(8.dp).verticalScroll(rememberScrollState())) {
                        if (logsState.value.isEmpty()) {
                            Text("暂无日志", style = MaterialTheme.typography.bodySmall)
                        } else {
                            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                            logsState.value.forEach { entry ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = fmt.format(Date(entry.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ComposeColor.Gray,
                                        modifier = Modifier.padding(end = 8.dp)
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
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            try { LogStore.clear() } catch (_: Throwable) {}
                            logsState.value = emptyList()
                            counter.longValue = 0L
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("清空日志") }
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("关闭") }
                }
            }
        }
    }
}