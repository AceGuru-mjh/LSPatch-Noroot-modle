package com.vipunlock.noroot.ui.screens

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
import com.vipunlock.noroot.XposedLoader
import com.vipunlock.noroot.utils.ConfigManager
import com.vipunlock.noroot.utils.EnvDetector
import com.vipunlock.noroot.utils.LogStore
import com.vipunlock.noroot.utils.ShizukuHelper
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

                val musicVipCount = listOf(
                    cfg?.netEaseVipEnabled, cfg?.qqMusicVipEnabled,
                    cfg?.kugouVipEnabled, cfg?.kuwoVipEnabled
                ).count { it == true }
                data["Music VIP"] = "$musicVipCount active" +
                    (if (cfg?.netEaseVipEnabled == true) " NetEase" else "") +
                    (if (cfg?.qqMusicVipEnabled == true) " QQ" else "") +
                    (if (cfg?.kugouVipEnabled == true) " Kugou" else "") +
                    (if (cfg?.kuwoVipEnabled == true) " Kuwo" else "")

                val videoVipCount = listOf(
                    cfg?.iqiyiVipEnabled, cfg?.youkuVipEnabled,
                    cfg?.tencentVideoVipEnabled, cfg?.biliVipEnabled
                ).count { it == true }
                data["Video VIP"] = "$videoVipCount active" +
                    (if (cfg?.iqiyiVipEnabled == true) " iQiyi" else "") +
                    (if (cfg?.youkuVipEnabled == true) " Youku" else "") +
                    (if (cfg?.tencentVideoVipEnabled == true) " Tencent" else "") +
                    (if (cfg?.biliVipEnabled == true) " Bili" else "")

                val readVipCount = listOf(
                    cfg?.ximalayaVipEnabled, cfg?.toutiaoVipEnabled,
                    cfg?.zhihuVipEnabled
                ).count { it == true }
                data["Reading VIP"] = "$readVipCount active" +
                    (if (cfg?.ximalayaVipEnabled == true) " Ximalaya" else "") +
                    (if (cfg?.zhihuVipEnabled == true) " Zhihu" else "")

                val toolVipCount = listOf(
                    cfg?.baiduNetdiskVipEnabled, cfg?.wpsVipEnabled, cfg?.wereadVipEnabled
                ).count { it == true }
                data["Tool VIP"] = "$toolVipCount active" +
                    (if (cfg?.baiduNetdiskVipEnabled == true) " Baidu" else "") +
                    (if (cfg?.wpsVipEnabled == true) " WPS" else "") +
                    (if (cfg?.wereadVipEnabled == true) " WeRead" else "")

                val totalVip = musicVipCount + videoVipCount + readVipCount + toolVipCount
                data["Total VIP"] = "$totalVip apps"

                data["Verify Bypass"] = if (cfg?.bypassVerifyEnabled == true) "Active" else "Off"
                data["Remove Ads"] = if (cfg?.removeAdsEnabled == true) "Active" else "Off"
                data["Universal Try"] = if (cfg?.universalVipTryEnabled == true) "Active" else "Off"
                data["Shizuku DB"] = if (cfg?.shizukuVipDbEnabled == true) "Active" else "Off"

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
                DiagRow("Total VIP", diagData["Total VIP"] ?: "-")
                DiagRow("Logs Today", diagData["Logs Today"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("VIP Unlock Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Music VIP", diagData["Music VIP"] ?: "-")
                DiagRow("Video VIP", diagData["Video VIP"] ?: "-")
                DiagRow("Reading VIP", diagData["Reading VIP"] ?: "-")
                DiagRow("Tool VIP", diagData["Tool VIP"] ?: "-")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Experimental", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DiagRow("Verify Bypass", diagData["Verify Bypass"] ?: "-")
                DiagRow("Remove Ads", diagData["Remove Ads"] ?: "-")
                DiagRow("Universal Try", diagData["Universal Try"] ?: "-")
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
