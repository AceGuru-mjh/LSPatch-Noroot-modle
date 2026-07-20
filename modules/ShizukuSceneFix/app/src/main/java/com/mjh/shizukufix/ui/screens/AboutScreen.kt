package com.mjh.shizukufix.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mjh.shizukufix.XposedLoader

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text("Shizuku Scene Fix", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row2(Icons.Default.Person, "开发者", "MJH")
                Spacer(Modifier.height(12.dp))
                Row2(Icons.Default.Code, "项目地址", "github.com/mengjinghao/lsp-model")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mengjinghao/lsp-model"))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("在浏览器打开项目地址")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("功能简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• 修复 Scene 在 Shizuku 授权列表不显示问题", style = MaterialTheme.typography.bodySmall)
                Text("• Path A: Scene 启动后主动申请 Shizuku 权限", style = MaterialTheme.typography.bodySmall)
                Text("• Path B: 向 Shizuku 列表注入 Scene", style = MaterialTheme.typography.bodySmall)
                Text("• Shizuku 变体包名检测（兼容第三方 fork）", style = MaterialTheme.typography.bodySmall)
                Text("• [实验] Shizuku 服务保活", style = MaterialTheme.typography.bodySmall)
                Text("• [实验] 授权对话框自动确认辅助", style = MaterialTheme.typography.bodySmall)
                Text("• [实验] 隐藏模块自身存在", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("免责声明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
                Text(
                    "仅供学习研究使用。Shizuku 是开源项目，本模块不修改 Shizuku 源码，" +
                        "仅在运行时 Hook 帮助 Scene 出现在 Shizuku 授权列表中。" +
                        "使用本模块产生的任何后果由使用者自行承担。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Row2(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
