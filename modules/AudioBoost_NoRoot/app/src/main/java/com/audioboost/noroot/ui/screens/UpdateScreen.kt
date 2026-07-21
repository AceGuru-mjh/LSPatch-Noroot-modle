package com.audioboost.noroot.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NewReleases
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
import com.audioboost.noroot.XposedLoader
import com.audioboost.noroot.utils.ApkDownloader
import com.audioboost.noroot.utils.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var progress by remember { mutableStateOf(0f) }
    var autoCheck by remember { mutableStateOf(UpdateChecker.isAutoCheckEnabled()) }
    var cacheSize by remember { mutableStateOf(UpdateChecker.getDownloadCacheSize(ctx)) }
    var showReleaseNotes by remember { mutableStateOf(false) }

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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("热更�?, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "检�?GitHub Release 新版本，支持应用内下载安�?,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("当前版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自动检查更�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("进入页面自动检查（5分钟内不重复�?, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoCheck, onCheckedChange = {
                    autoCheck = it
                    UpdateChecker.setAutoCheck(it)
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                checking = true
                error = null
                info = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        UpdateChecker.checkUpdate(XposedLoader.VERSION, force = true)
                    }
                    checking = false
                    if (result != null) {
                        info = result
                    } else {
                        error = "检查失败，请检查网�?
                    }
                }
            },
            enabled = !checking && !downloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                Text("  检查中...")
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("  检查更�?)
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error!!, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        statusMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        info?.let { ui ->
            Spacer(Modifier.height(16.dp))

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
                        contentDescription = null,
                        tint = if (ui.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (ui.hasUpdate) "发现新版�?v${ui.latestVersion}" else "已是最新版�?,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("发布日期: ${ui.publishDate.take(10)}",
                             style = MaterialTheme.typography.bodySmall)
                        if (ui.isIgnored) {
                            Text("(此版本已忽略)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (ui.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("更新说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showReleaseNotes = !showReleaseNotes }) {
                                Text(if (showReleaseNotes) "收起" else "展开")
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

            if (ui.hasUpdate && !ui.isIgnored) {
                Spacer(Modifier.height(12.dp))
                val apk = UpdateChecker.findMatchingApk(ui, "AudioBoost_NoRoot")
                if (apk != null) {
                    Text("下载: ${apk.name} (${"%.2f".format(apk.sizeBytes / 1024.0 / 1024.0)} MB)", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (downloading) {
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Button(
                            onClick = {
                                downloading = true
                                progress = 0f
                                statusMsg = "开始下�?.."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        ApkDownloader.download(
                                            ctx, apk.downloadUrl, apk.name,
                                            { p -> progress = p },
                                            { s -> statusMsg = s }
                                        )
                                    }
                                    downloading = false
                                    cacheSize = UpdateChecker.getDownloadCacheSize(ctx)
                                    if (!result.success) {
                                        error = "下载失败: ${result.errorMsg}"
                                    } else {
                                        statusMsg = "下载完成，请在弹出的安装界面确认安装"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Text("  下载并安�?)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { UpdateChecker.ignoreVersion(ui.latestVersion) },
                        modifier = Modifier.weight(1f)
                    ) { Text("忽略此版�?) }
                    OutlinedButton(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(ui.releaseUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("浏览器打开") }
                }
            }

            if (ui.isIgnored) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        UpdateChecker.clearIgnored()
                        checking = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                UpdateChecker.checkUpdate(XposedLoader.VERSION, force = true)
                            }
                            checking = false
                            if (r != null) info = r
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消忽略并重新检�?) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("下载缓存", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("占用: ${cacheSize / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    UpdateChecker.clearDownloadCache(ctx)
                    cacheSize = 0L
                    statusMsg = "缓存已清�?
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "清理缓存", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("说明", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("�?更新�? github.com/AceGuru-mjh/LSPatch-Noroot-modle/releases", style = MaterialTheme.typography.bodySmall)
                Text("�?下载完成自动弹出安装界面（需允许\"安装未知应用\"�?, style = MaterialTheme.typography.bodySmall)
                Text("�?模块更新后需�?LSPosed/LSPatch 重新启用并重启目�?APP", style = MaterialTheme.typography.bodySmall)
                Text("�?自动检查间隔最�?分钟，避免频繁请�?GitHub API", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
