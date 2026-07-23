package com.videosaver.noroot.ui.screens

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
import com.videosaver.noroot.XposedLoader
import com.videosaver.noroot.utils.ConfigManager
import com.videosaver.noroot.utils.EnvDetector
import com.videosaver.noroot.utils.LogStore
import com.videosaver.noroot.utils.ShizukuHelper
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
                val platforms = listOf(
                    cfg?.douyinNoWatermark, cfg?.kuaishouNoWatermark,
                    cfg?.xhsNoWatermark, cfg?.biliDownload
                ).count { it == true }
                data["Platforms Active"] = "$platforms"
                data["Douyin"] = if (cfg?.douyinNoWatermark == true) "Watermark-Free" else "Off"
                data["Kuaishou"] = if (cfg?.kuaishouNoWatermark == true) "Watermark-Free" else "Off"
                data["Xiaohongshu"] = if (cfg?.xhsNoWatermark == true) "Watermark-Free" else "Off"
                data["Bilibili"] = if (cfg?.biliDownload == true) "Download" else "Off"
                data["Save Path"] = cfg?.customSavePath ?: "/sdcard/Download/VideoSaver/"
                data["Auto Rename"] = if (cfg?.autoRenameEnabled != false) "On" else "Off"
                data["Auto Download"] = if (cfg?.autoDownloadEnabled == true) "Active" else "Off"
                data["Remove Ads"] = if (cfg?.removeAdsEnabled == true) "Active" else "Off"
                data["Original Quality"] = if (cfg?.saveOriginalQualityEnabled == true) "Forced" else "Off"
                data["Batch Download"] = if (cfg?.batchDownloadEnabled == true) "Active" else "Off"
                data["Shizuku Capture"] = if (cfg?.shizukuCaptureEnabled == true) "Active" else "Off"

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
                Text("Active Platforms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Total Active", diagData["Platforms Active"] ?: "-")
                DiagRow("Douyin", diagData["Douyin"] ?: "-")
                DiagRow("Kuaishou", diagData["Kuaishou"] ?: "-")
                DiagRow("Xiaohongshu", diagData["Xiaohongshu"] ?: "-")
                DiagRow("Bilibili", diagData["Bilibili"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Download Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Save Path", diagData["Save Path"] ?: "-")
                DiagRow("Auto Rename", diagData["Auto Rename"] ?: "-")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagRow("Auto Download", diagData["Auto Download"] ?: "-")
                DiagRow("Remove Ads", diagData["Remove Ads"] ?: "-")
                DiagRow("Original Quality", diagData["Original Quality"] ?: "-")
                DiagRow("Batch Download", diagData["Batch Download"] ?: "-")
                DiagRow("Shizuku Capture", diagData["Shizuku Capture"] ?: "-")
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
