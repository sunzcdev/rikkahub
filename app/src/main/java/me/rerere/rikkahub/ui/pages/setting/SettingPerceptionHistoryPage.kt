package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.perception.PerceptionMemory
import me.rerere.rikkahub.data.perception.PerceptionStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PerceptionRecord(
    val type: String,       // "定位" or "天气"
    val timestamp: Long,
    val city: String,
    val detail: String,     // 具体数据描述
    val subtitle: String = "", // 第二行补充信息
    val color: Color,       // 类型色标
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPerceptionHistoryPage() {
    val perceptionStore: PerceptionStore = koinInject()
    val memory by perceptionStore.memoryFlow.collectAsStateWithLifecycle(
        initialValue = PerceptionMemory()
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 合并排序：位置 + 天气，按时间倒序
    val records = buildList {
        memory.locationHistory.forEach { loc ->
            val locDetail = buildString {
                // POI > 街道地址 > 区域
                if (!loc.poiName.isNullOrBlank()) append(loc.poiName)
                if (!loc.street.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(loc.street)
                    if (!loc.streetNum.isNullOrBlank()) append(loc.streetNum)
                }
                if (isEmpty()) append(loc.district ?: loc.city)
            }
            val locSubtitle = buildString {
                append(loc.district?.let { "$it, " } ?: "")
                append(loc.city)
                if (loc.latitude != null && loc.longitude != null) {
                    append(" (${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)})")
                }
            }
            add(
                PerceptionRecord(
                    type = "📍 定位",
                    timestamp = loc.timestamp,
                    city = loc.city,
                    detail = locDetail,
                    subtitle = locSubtitle,
                    color = Color(0xFF4CAF50),
                )
            )
        }
        memory.weatherHistory.forEach { w ->
            val tempDesc = when {
                w.temperature > 35 -> "${w.temperature}°C 🔥"
                w.temperature > 30 -> "${w.temperature}°C 🌡️"
                w.temperature < 0 -> "${w.temperature}°C ❄️"
                w.temperature < 10 -> "${w.temperature}°C 🥶"
                else -> "${w.temperature}°C 🌤️"
            }
            add(
                PerceptionRecord(
                    type = "🌤 天气",
                    timestamp = w.timestamp,
                    city = w.city,
                    detail = "${w.condition} / $tempDesc / 湿度${w.humidity}%",
                    color = Color(0xFF2196F3),
                )
            )
        }
    }.sortedByDescending { it.timestamp }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("感知数据列表") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂无感知数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "开启唧唧后自动采集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "共 ${records.size} 条记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(records) { record ->
                    RecordCard(record)
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordCard(record: PerceptionRecord) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.type,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = record.color,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.city,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = sdf.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.detail,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (record.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
