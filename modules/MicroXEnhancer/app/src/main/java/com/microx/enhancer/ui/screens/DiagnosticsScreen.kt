package com.microx.enhancer.ui.screens

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
import com.microx.enhancer.XposedLoader
import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.EnvDetector
import com.microx.enhancer.utils.LogStore
import com.microx.enhancer.utils.ShizukuHelper
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
                data["Anti-Recall"] = if (cfg?.antiRecallEnabled == true) "Active" else "Off"
                data["Ad Block"] = if (cfg?.adBlockEnabled == true) "Active" else "Off"
                data["Red Packet"] = if (cfg?.autoRedPacketEnabled == true) "Active" else "Off"
                data["Auto Transfer"] = if (cfg?.autoTransferEnabled == true) "Active" else "Off"
                data["Privacy"] = if (cfg?.privacyEnabled == true) "Active" else "Off"
                data["Moment Protect"] = if (cfg?.momentProtectEnabled == true) "Active" else "Off"
                data["UI Mod"] = if (cfg?.uiModEnabled == true) "Active" else "Off"
                data["Batch Manage"] = if (cfg?.batchManageEnabled == true) "Active" else "Off"
                data["Auto Reply"] = if (cfg?.autoReplyEnabled == true) "Active" else "Off"
                data["Voice Export"] = if (cfg?.voiceMessageExportEnabled == true) "Active" else "Off"
                data["Msg Search"] = if (cfg?.messageSearchEnhanceEnabled == true) "Active" else "Off"
                data["Bypass Detection"] = if (cfg?.bypassDetectionEnabled == true) "Active" else "Off"
                data["Shizuku DB"] = if (cfg?.shizukuDbAccessEnabled == true) "Active" else "Off"
                data["Custom Theme"] = if (cfg?.customThemeEnabled == true) "Active" else "Off"

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
                DiagRow("Logs Today", diagData["Logs Today"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Core Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Anti-Recall", diagData["Anti-Recall"] ?: "-")
                DiagRow("Ad Block", diagData["Ad Block"] ?: "-")
                DiagRow("Red Packet", diagData["Red Packet"] ?: "-")
                DiagRow("Auto Transfer", diagData["Auto Transfer"] ?: "-")
                DiagRow("Privacy", diagData["Privacy"] ?: "-")
                DiagRow("Moment Protect", diagData["Moment Protect"] ?: "-")
                DiagRow("Bypass Detection", diagData["Bypass Detection"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Extended Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("UI Mod", diagData["UI Mod"] ?: "-")
                DiagRow("Batch Manage", diagData["Batch Manage"] ?: "-")
                DiagRow("Auto Reply", diagData["Auto Reply"] ?: "-")
                DiagRow("Voice Export", diagData["Voice Export"] ?: "-")
                DiagRow("Msg Search", diagData["Msg Search"] ?: "-")
                DiagRow("Custom Theme", diagData["Custom Theme"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Shizuku DB", diagData["Shizuku DB"] ?: "-")
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
