package com.adblockerx.noroot.ui.screens

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
import com.adblockerx.noroot.XposedLoader
import com.adblockerx.noroot.utils.AdBlockList
import com.adblockerx.noroot.utils.ConfigManager
import com.adblockerx.noroot.utils.EnvDetector
import com.adblockerx.noroot.utils.LogStore
import com.adblockerx.noroot.utils.ShizukuHelper
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

                data["Blocked Ads"] = "${ConfigManager.getBlockedCount()}"
                data["Built-in Domains"] = "${AdBlockList.BUILTIN_AD_DOMAINS.size}"
                val cfg = try { ConfigManager.getGlobalConfig() } catch (_: Exception) { null }
                data["Custom Domains"] = "${cfg?.customBlocklist?.size ?: 0}"
                data["WebView Hook"] = if (cfg?.webviewAdEnabled == true) "Active" else "Off"
                data["OkHttp Hook"] = if (cfg?.okHttpAdEnabled == true) "Active" else "Off"
                data["Hosts Filter"] = if (cfg?.hostsFilterEnabled == true) "Active" else "Off"
                data["AdView Hide"] = if (cfg?.adViewHideEnabled == true) "Active" else "Off"

                val tracker = if (cfg?.trackerBlockEnabled == true) "On" else "Off"
                val cookie = if (cfg?.cookieCleanEnabled == true) "On" else "Off"
                val redirect = if (cfg?.redirectBlockEnabled == true) "On" else "Off"
                data["Experimental"] = "Tracker:$tracker Cookie:$cookie Redirect:$redirect"

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
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ad Blocking Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Ads Blocked", diagData["Blocked Ads"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Built-in Domains", diagData["Built-in Domains"] ?: "-")
                DiagRow("Custom Domains", diagData["Custom Domains"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Logs Today", diagData["Logs Today"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Active Hooks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("WebView Hook", diagData["WebView Hook"] ?: "-")
                DiagRow("OkHttp Hook", diagData["OkHttp Hook"] ?: "-")
                DiagRow("Hosts Filter", diagData["Hosts Filter"] ?: "-")
                DiagRow("AdView Hide", diagData["AdView Hide"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Experimental", diagData["Experimental"] ?: "-")
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
