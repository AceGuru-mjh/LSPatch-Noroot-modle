package com.gameunlocker.noroot.activities

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

class PanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0.4f)
        val params = window.attributes
        params.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.CENTER
        window.attributes = params

        setContent { GlassmorphismPanel() }
    }
}

@Composable
fun GlassmorphismPanel() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ComposeColor.White.copy(alpha = 0.85f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("GameUnlocker NoRoot 控制面板", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("总开关", modifier = Modifier.weight(1f))
                    Switch(checked = true, onCheckedChange = { /* ... */ })
                }
                Spacer(Modifier.height(12.dp))
                Text("帧率解锁 12 次", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))

                Text("最近日志", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = ComposeColor.Black.copy(alpha = 0.05f))) {
                    Column(modifier = Modifier
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                    ) {
                        listOf("解锁帧率到 120 FPS", "伪装 Build 型号", "...").forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { /* finish() */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}
