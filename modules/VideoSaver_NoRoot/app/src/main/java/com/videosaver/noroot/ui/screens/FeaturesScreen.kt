package com.videosaver.noroot.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videosaver.noroot.models.VideoConfig
import com.videosaver.noroot.ui.components.FeatureCard
import com.videosaver.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: VideoConfig, onConfigChange: (VideoConfig) -> Unit) {
    val scroll = rememberScrollState()
    var savePath by remember(cfg) { mutableStateOf(cfg.customSavePath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "抖音无水印下载",
            "Hook 抖音/抖音极速版视频下载方法，去除 URL 上的水印参数（playwm -> play）",
            cfg.douyinNoWatermark,
            { val nc = cfg.copy(douyinNoWatermark = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "快手无水印下载",
            "Hook 快手/快手极速版视频 URL getter，移除 /watermark/ 路径段",
            cfg.kuaishouNoWatermark,
            { val nc = cfg.copy(kuaishouNoWatermark = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "小红书无水印下载",
            "Hook 小红书图片/视频 URL，去除缩放参数恢复原图原视频",
            cfg.xhsNoWatermark,
            { val nc = cfg.copy(xhsNoWatermark = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "B站视频下载解锁",
            "Hook B站下载方法，解锁画质限制，支持通过 avid+cid 拉取原画质 URL",
            cfg.biliDownload,
            { val nc = cfg.copy(biliDownload = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("保存路径", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = savePath,
            onValueChange = { savePath = it },
            label = { Text("自定义保存目录") },
            placeholder = { Text("/sdcard/Download/VideoSaver/") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "自动重命名",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = cfg.autoRenameEnabled,
                        onCheckedChange = {
                            val nc = cfg.copy(autoRenameEnabled = it)
                            ConfigManager.saveGlobalConfig(nc)
                            onConfigChange(nc)
                        }
                    )
                }
                Text(
                    "保存文件命名为：平台_时间戳.扩展名",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val nc = cfg.copy(customSavePath = savePath)
                        ConfigManager.saveGlobalConfig(nc)
                        onConfigChange(nc)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存路径")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Shizuku 系统级（adb级，需 Shizuku 运行）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "截图/点击 UI 自动化", "通过 Shizuku 执行 screencap + input tap，辅助视频下载按钮自动化操作",
            cfg.shizukuCaptureEnabled,
            { val nc = cfg.copy(shizukuCaptureEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "自动下载",
            "播放视频时自动触发保存（Hook MediaPlayer/ExoPlayer/IjkMediaPlayer）",
            cfg.autoDownloadEnabled,
            { val nc = cfg.copy(autoDownloadEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "去视频广告",
            "Hook 字节/快手/腾讯广告 SDK 的 loadAd/show 方法，阻断广告加载与展示",
            cfg.removeAdsEnabled,
            { val nc = cfg.copy(removeAdsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "强制原画质下载",
            "Hook 画质管理方法，强制返回最高画质（Hook getCurrentQuality/setQuality）",
            cfg.saveOriginalQualityEnabled,
            { val nc = cfg.copy(saveOriginalQualityEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "批量下载",
            "Hook 用户主页/合集视频列表加载，自动批量下载所有视频（上限 50）",
            cfg.batchDownloadEnabled,
            { val nc = cfg.copy(batchDownloadEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(32.dp))
    }
}
