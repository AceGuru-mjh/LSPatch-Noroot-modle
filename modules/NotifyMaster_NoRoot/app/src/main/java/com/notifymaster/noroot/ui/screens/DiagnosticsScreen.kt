package com.notifymaster.noroot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notifymaster.noroot.XposedLoader
import com.notifymaster.noroot.utils.ConfigManager
import com.notifymaster.noroot.utils.EnvDetector
import com.notifymaster.noroot.utils.LogStore
import com.notifymaster.noroot.utils.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsScreen() {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    var diagData by remember { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val data = mutableMapOf<String, String>()
                data["Version"] = XposedLoader.VERSION
                data["Mode"] = if (EnvDetector.isLocalMode) "Local" else "Integrated"
                data["Shizuku"] = if (try { ShizukuHelper.isAvailable() } catch (_: Exception) { false }) "Connected" else "N/A"
                data["Config"] = if (ConfigManager.isInitialized()) "Loaded" else "Not Ready"

                val cfg = try { ConfigManager.getGlobalConfig() } catch (_: Exception) { null }
                data["Notify Filter"] = if (cfg?.notifyFilterEnabled == true) "Active (${cfg.filterKeywords.size} keywords)" else "Off"
                data["Anti-Recall"] = if (cfg?.antiRecallNotifyEnabled == true) "Active" else "Off"
                data["Notify History"] = if (cfg?.notifyHistoryEnabled == true) "Active" else "Off"
                data["Notify Beautify"] = if (cfg?.notifyBeautifyEnabled == true) "Active" else "Off"
                data["Batch Notify"] = if (cfg?.batchNotifyEnabled == true) "Active (max ${cfg.batchMaxCount})" else "Off"
                data["Priority Override"] = if (cfg?.priorityOverrideEnabled == true) "Active (Lvl ${cfg.priorityOverrideLevel})" else "Off"
                data["Silent Notify"] = if (cfg?.silentNotifyEnabled == true) "Active (${cfg.silentTargetApps.size} apps)" else "Off"
                data["Target Apps"] = "${cfg?.targetApps?.size ?: 0}"
                data["Shizuku Cmd"] = if (cfg?.shizukuNotifyCmdEnabled == true) "Active" else "Off"

                val logs = LogStore.getRecentLogs(100)
                data["Logs Today"] = "${logs.size}"

                diagData = data
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Version", diagData["Version"] ?: "-")
                DiagRow("Mode", diagData["Mode"] ?: "-")
                DiagRow("Shizuku", diagData["Shizuku"] ?: "-")
                DiagRow("Config", diagData["Config"] ?: "-")
                DiagRow("Target Apps", diagData["Target Apps"] ?: "-")
                DiagRow("Logs Today", diagData["Logs Today"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Active Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Notify Filter", diagData["Notify Filter"] ?: "-")
                DiagRow("Anti-Recall", diagData["Anti-Recall"] ?: "-")
                DiagRow("Notify History", diagData["Notify History"] ?: "-")
                DiagRow("Notify Beautify", diagData["Notify Beautify"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Batch Notify", diagData["Batch Notify"] ?: "-")
                DiagRow("Priority Override", diagData["Priority Override"] ?: "-")
                DiagRow("Silent Notify", diagData["Silent Notify"] ?: "-")
                DiagRow("Shizuku Cmd", diagData["Shizuku Cmd"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val recentLogs = remember { LogStore.getRecentLogs(10) }
                if (recentLogs.isEmpty()) {
                    Text("No recent activity", style = MaterialTheme.typography.bodySmall)
                } else {
                    recentLogs.forEach { entry ->
                        Text(
                            "[${entry.type}] ${entry.message}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
