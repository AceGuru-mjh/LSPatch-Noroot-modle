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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videosaver.noroot.XposedLoader
import com.videosaver.noroot.utils.ConfigManager

@Composable
fun DiagnosticsScreen() {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    var xposedActive by remember { mutableStateOf(false) }
    var shizukuActive by remember { mutableStateOf(false) }

    xposedActive = try {
        Class.forName("de.robv.android.xposed.XposedBridge")
        true
    } catch (_: ClassNotFoundException) { false }

    shizukuActive = try {
        val cls = Class.forName("rikka.shizuku.Shizuku")
        val ping = cls.getMethod("pingBinder")
        ping.invoke(null) as? Boolean ?: false
    } catch (_: Throwable) { false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("环境诊断", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        DiagCard(
            icon = if (xposedActive) Icons.Default.CheckCircle else Icons.Default.Warning,
            title = "Xposed 框架",
            status = if (xposedActive) "已激活" else "未激活（模块进程内）",
            detail = if (xposedActive) "XposedBridge 已加载，Hook 可正常工作"
                     else "当前在模块自身进程，XposedBridge 不可见属正常。实际 Hook 生效需在目标 APP 内验证",
            ok = xposedActive
        )
        Spacer(Modifier.height(8.dp))

        DiagCard(
            icon = if (shizukuActive) Icons.Default.CheckCircle else Icons.Default.Info,
            title = "Shizuku 服务",
            status = if (shizukuActive) "已连接" else "未连接",
            detail = if (shizukuActive) "Shizuku 服务运行中"
                     else "Shizuku 未运行或未授权。免 Root 版主要功能不依赖 Shizuku，仅下载与保存相关系统级操作可选",
            ok = shizukuActive
        )
        Spacer(Modifier.height(8.dp))

        DiagCard(
            icon = Icons.Default.Info,
            title = "模块版本",
            status = "v${XposedLoader.VERSION}",
            detail = "包名: ${ctx.packageName}",
            ok = true
        )
        Spacer(Modifier.height(8.dp))

        val cfg = remember { try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { null } }
        DiagCard(
            icon = Icons.Default.BugReport,
            title = "配置状态",
            status = if (cfg != null) "已加载" else "未初始化",
            detail = if (cfg != null) "总开关: ${if (cfg.masterEnabled) "开" else "关"}\n配置文件: /data/data/${ctx.packageName}/shared_prefs/${ConfigManager.PREFS_NAME}.xml\n保存路径: ${cfg.customSavePath}"
                     else "ConfigManager 未初始化",
            ok = cfg != null
        )
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("诊断说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• 本页显示模块自身进程的状态，非目标 APP 进程", style = MaterialTheme.typography.bodySmall)
                Text("• Xposed 框架状态在此处显示\"未激活\"是正常的", style = MaterialTheme.typography.bodySmall)
                Text("• 实际 Hook 是否生效需在目标 APP 内通过 LSPosed 日志验证", style = MaterialTheme.typography.bodySmall)
                Text("• 视频/图片保存到 customSavePath，需目标 APP 有存储权限", style = MaterialTheme.typography.bodySmall)
                Text("• 免 Root 版仅 Hook 应用进程内方法，不修改系统", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DiagCard(icon: ImageVector, title: String, status: String, detail: String, ok: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null,
                 tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodyMedium,
                     color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
