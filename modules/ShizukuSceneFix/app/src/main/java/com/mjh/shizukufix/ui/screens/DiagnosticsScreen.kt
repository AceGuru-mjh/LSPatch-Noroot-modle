package com.mjh.shizukufix.ui.screens

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
import com.mjh.shizukufix.XposedLoader
import com.mjh.shizukufix.utils.ConfigManager
import com.mjh.shizukufix.utils.EnvDetector
import com.mjh.shizukufix.utils.LogStore
import com.mjh.shizukufix.utils.PackageHelper
import com.mjh.shizukufix.utils.ShizukuHelper
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

                val sceneInstalled = try {
                    PackageHelper.isPackageInstalled(ctx, PackageHelper.SCENE_PACKAGE)
                } catch (_: Exception) { false }
                data["Scene"] = if (sceneInstalled) "Installed" else "Not Found"

                val sceneUid = try {
                    if (sceneInstalled) "${PackageHelper.getUid(ctx, PackageHelper.SCENE_PACKAGE)}" else "N/A"
                } catch (_: Exception) { "N/A" }
                data["Scene UID"] = sceneUid

                val cfg = try { ConfigManager.getGlobalConfig() } catch (_: Exception) { null }
                data["Scene Fix"] = if (cfg?.sceneFixEnabled == true) "Active" else "Off"
                data["List Injector"] = if (cfg?.listInjectorEnabled == true) "Active" else "Off"
                data["Variant Detect"] = if (cfg?.variantDetectEnabled == true) "Active" else "Off"
                data["Service Watchdog"] = if (cfg?.serviceWatchdogEnabled == true) "Active (${cfg.watchdogIntervalSec}s)" else "Off"
                data["Auto Grant"] = if (cfg?.autoGrantHelperEnabled == true) "Active (${cfg.autoGrantDelayMs}ms)" else "Off"
                data["Hide from Scene"] = if (cfg?.hideFromSceneEnabled == true) "Active" else "Off"
                data["PM Grant"] = if (cfg?.pmGrantEnabled == true) "Active (${cfg.pmGrantPackages.size} apps)" else "Off"

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
                DiagRow("Shizuku Service", diagData["Shizuku"] ?: "-")
                DiagRow("Config", diagData["Config"] ?: "-")
                DiagRow("Logs Today", diagData["Logs Today"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Scene Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Scene Package", diagData["Scene"] ?: "-")
                DiagRow("Scene UID", diagData["Scene UID"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Fix Methods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Scene Fix (Path A)", diagData["Scene Fix"] ?: "-")
                DiagRow("List Injector (Path B)", diagData["List Injector"] ?: "-")
                DiagRow("Variant Detect", diagData["Variant Detect"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Service Watchdog", diagData["Service Watchdog"] ?: "-")
                DiagRow("Auto Grant", diagData["Auto Grant"] ?: "-")
                DiagRow("Hide from Scene", diagData["Hide from Scene"] ?: "-")
                DiagRow("PM Grant", diagData["PM Grant"] ?: "-")
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
