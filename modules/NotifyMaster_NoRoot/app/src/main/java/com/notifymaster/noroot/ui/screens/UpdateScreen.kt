package .ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import .XposedLoader
import .utils.ApkDownloader
import .utils.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun UpdateScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val pauseFlag = remember { AtomicBoolean(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloaded by remember { mutableStateOf(false) }
    var autoCheck by remember { mutableStateOf(UpdateChecker.isAutoCheckEnabled()) }
    var cacheSize by remember { mutableStateOf(UpdateChecker.getDownloadCacheSize(ctx)) }
    var showReleaseNotes by remember { mutableStateOf(false) }
    var currentMirror by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (UpdateChecker.isAutoCheckEnabled()) {
            checking = true
            val r = withContext(Dispatchers.IO) { UpdateChecker.checkUpdate(XposedLoader.VERSION) }
            checking = false
            if (r != null && r.hasUpdate && !r.isIgnored) {
                info = r
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)
    ) {
        Text("Hot Update", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Check GitHub Release, download and install in-app. CDN mirrors for faster downloads.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        // Current version card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Version", style = MaterialTheme.typography.labelSmall)
                    Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Auto check toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Check on entering page (5min throttle)", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoCheck, onCheckedChange = {
                    autoCheck = it; UpdateChecker.setAutoCheck(it)
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        // Check update button
        Button(
            onClick = {
                checking = true; error = null; info = null; downloaded = false
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        UpdateChecker.checkUpdate(XposedLoader.VERSION, force = true)
                    }
                    checking = false
                    if (result != null) info = result else error = "Check failed, check network"
                }
            },
            enabled = !checking && !downloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("  Checking...", modifier = Modifier.padding(start = 8.dp))
            } else {
                Icon(Icons.Default.Refresh, null)
                Text("  Check Update", modifier = Modifier.padding(start = 8.dp))
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        statusMsg?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                }
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }

        info?.let { ui ->
            Spacer(Modifier.height(16.dp))

            // Update status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (ui.hasUpdate) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (ui.hasUpdate) Icons.Default.NewReleases else Icons.Default.CheckCircle,
                        null,
                        tint = if (ui.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (ui.hasUpdate) "v${ui.latestVersion} Available" else "Up to date",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        Text("Published: ${ui.publishDate.take(10)}", style = MaterialTheme.typography.bodySmall)
                        if (ui.isIgnored) {
                            Text("(Ignored)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Release notes
            if (ui.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Release Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showReleaseNotes = !showReleaseNotes }) {
                                Text(if (showReleaseNotes) "Collapse" else "Expand")
                            }
                        }
                        if (showReleaseNotes) {
                            Spacer(Modifier.height(8.dp))
                            Text(ui.releaseNotes, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(ui.releaseNotes.take(80) + if (ui.releaseNotes.length > 80) "..." else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Download & install
            if (ui.hasUpdate && !ui.isIgnored) {
                Spacer(Modifier.height(12.dp))
                val apk = UpdateChecker.findMatchingApk(ui, "NotifyMaster_NoRoot")
                if (apk != null) {
                    Text("${apk.name} (%.1f MB)".format(apk.sizeBytes / 1024.0 / 1024.0),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))

                    if (downloading || downloaded) {
                        // Progress bar + pause/resume
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${(progress * 100).toInt()}% CDN #${currentMirror + 1}",
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            if (!downloaded) {
                                IconButton(onClick = {
                                    paused = !paused
                                    pauseFlag.set(!pauseFlag.get())
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        if (paused) "Resume" else "Pause",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (downloaded) {
                                Icon(Icons.Default.DownloadDone, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Text(" Complete", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                downloading = true; paused = false; downloaded = false
                                progress = 0f; statusMsg = "Starting..."
                                scope.launch {
                                    val mirrors = ApkDownloader.generateMirrors(apk.downloadUrl)
                                    currentMirror = 0

                                    // Try mirrors one by one
                                    var result: ApkDownloader.DownloadResult? = null
                                    for ((idx, mirror) in mirrors.withIndex()) {
                                        if (pauseFlag.get()) break
                                        currentMirror = idx
                                        statusMsg = "CDN ${idx + 1}/${mirrors.size}"
                                        result = withContext(Dispatchers.IO) {
                                            ApkDownloader.download(
                                                ctx, listOf(mirror), apk.name,
                                                { p -> progress = p },
                                                { s -> statusMsg = s },
                                                pauseFlag
                                            )
                                        }
                                        if (result.success) break
                                    }

                                    downloading = false
                                    cacheSize = UpdateChecker.getDownloadCacheSize(ctx)
                                    if (result != null && result.success) {
                                        downloaded = true
                                        statusMsg = "Downloaded - tap notification to install"
                                    } else {
                                        error = "Download failed: ${result?.errorMsg ?: "No mirrors worked"}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, null)
                            Text("  Download (${mirrorCount(apk.downloadUrl)} mirrors)", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { UpdateChecker.ignoreVersion(ui.latestVersion) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ignore") }
                    OutlinedButton(
                        onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ui.releaseUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Browser") }
                }
            }

            if (ui.isIgnored) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        UpdateChecker.clearIgnored()
                        checking = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { UpdateChecker.checkUpdate(XposedLoader.VERSION, force = true) }
                            checking = false
                            if (r != null) info = r
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Un-ignore & Re-check") }
            }
        }

        // Cache management
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download Cache", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${cacheSize / 1024} KB", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = {
                    UpdateChecker.clearDownloadCache(ctx); cacheSize = 0L
                    statusMsg = "Cache cleared"
                }) {
                    Icon(Icons.Default.Delete, "Clear cache", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Info", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("- Source: GitHub Releases + jsDelivr CDN + FastGit mirror", style = MaterialTheme.typography.bodySmall)
                Text("- Supports resume on network loss (Range header)", style = MaterialTheme.typography.bodySmall)
                Text("- Allow 'Install unknown apps' permission to auto-install", style = MaterialTheme.typography.bodySmall)
                Text("- Re-enable in LSPatch/LSPosed after update", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun mirrorCount(url: String): Int {
    return ApkDownloader.generateMirrors(url).size
}
