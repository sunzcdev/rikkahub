package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.context.LocalNavController
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.export.PerceptionMemorySerializer
import me.rerere.rikkahub.data.export.rememberExporter
import me.rerere.rikkahub.data.export.rememberImporter
import me.rerere.rikkahub.data.perception.PerceptionMemory
import me.rerere.rikkahub.data.perception.PerceptionStore
import me.rerere.rikkahub.jiji.PerceptionManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPerceptionPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val perceptionStore: PerceptionStore = koinInject()
    val perceptionManager: PerceptionManager = koinInject()
    val memory by perceptionStore.memoryFlow.collectAsStateWithLifecycle(
        initialValue = PerceptionMemory()
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val exporter = rememberExporter(memory, PerceptionMemorySerializer)
    val importer = rememberImporter(PerceptionMemorySerializer) { result ->
        result.onSuccess { imported ->
            scope.launch {
                perceptionStore.replaceMemory(imported)
                Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { e ->
            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("感知数据管理") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 概要
            Text(
                text = "位置记录: ${memory.locationHistory.size} 条",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "天气记录: ${memory.weatherHistory.size} 条",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (memory.locationHistory.isNotEmpty()) {
                Text(
                    text = "最早位置: ${formatTimestamp(memory.locationHistory.first().timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "最新位置: ${formatTimestamp(memory.locationHistory.last().timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("暂无感知数据", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            Button(
                onClick = { exporter.exportToFile() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导出感知数据")
            }

            Button(
                onClick = { importer.importFromFile() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导入感知数据")
            }

            Button(
                onClick = {
                    scope.launch {
                        perceptionStore.clearMemory()
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清空所有感知数据")
            }

            Button(
                onClick = { navController.navigate(Screen.SettingPerceptionHistory) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看数据列表")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 手动采集按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            perceptionManager.collector.collectLocationOnce(force = true)
                            Toast.makeText(context, "定位已采集", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("手动采集定位")
                }
                Button(
                    onClick = {
                        scope.launch {
                            val err = perceptionManager.collector.collectWeatherOnce(force = true)
                            if (err != null) {
                                Toast.makeText(context, "天气采集失败: $err", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "天气已采集", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("手动采集天气")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "感知数据由唧唧自动采集，记录你的位置变化和天气变化历史。" +
                        "数据存储在本地，不会上传到云端。你可以随时导出备份或导入恢复。" +
                        "\n\n位置每 1 分钟采集一次（同区域不重复记录）。" +
                        "天气每小时采集一次（同温度区间不重复记录）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
