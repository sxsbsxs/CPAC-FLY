package com.cpacfly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        onStartFlight = { /* Camera flow is added in the next milestone. */ },
                        onOpenRecords = { /* Records screen is added in a later milestone. */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onStartFlight: () -> Unit,
    onOpenRecords: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("CPAC FLY", style = MaterialTheme.typography.headlineLarge)
            Text(
                "纸飞机智能飞行计时与训练分析",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("准备好试飞了吗？", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("固定手机机位，让纸飞机完整进入镜头。首版使用手机内置摄像头。")
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onStartFlight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始试飞")
                    }
                }
            }
        }
        item {
            Text("训练工具", style = MaterialTheme.typography.titleLarge)
        }
        item {
            FeatureCard("飞行分析", "回看录像、慢动作与二维画面轨迹")
        }
        item {
            FeatureCard("我的纸飞机", "记录机型、纸张、尺寸与折叠参数")
        }
        item {
            FeatureCard("训练记录", "保存每次试飞成绩与视频")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenRecords) {
                    Text("查看训练记录")
                }
            }
        }
        item {
            Text(
                "开发预览版 · 自动识别与计时尚未启用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
