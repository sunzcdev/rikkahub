package me.rerere.rikkahub.jiji

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 唧唧的设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JijiSettingsScreen(
    configStore: JijiConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val config by configStore.configFlow.collectAsState(initial = JijiConfig.DEFAULT)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("唧唧设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 总开关
            SettingsSwitch(
                title = "启用唧唧",
                subtitle = "开启后唧唧会在后台感知并主动找您聊天",
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        val updated = config.copy(enabled = enabled)
                        configStore.saveConfig(updated)
                        // 启停后台服务
                    }
                },
            )

            HorizontalDivider()

            // 感知设置
            Text(
                text = "感知设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingsSwitch(
                title = "位置感知",
                subtitle = "唧唧会知道您的大致位置",
                checked = config.locationEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        configStore.saveConfig(config.copy(locationEnabled = enabled))
                    }
                },
            )

            SettingsSwitch(
                title = "天气感知",
                subtitle = "唧唧会查看您所在城市的天气",
                checked = config.weatherEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        configStore.saveConfig(config.copy(weatherEnabled = enabled))
                    }
                },
            )

            // OpenWeatherMap API Key
            OutlinedTextField(
                value = config.openWeatherApiKey,
                onValueChange = { key ->
                    scope.launch {
                        configStore.saveConfig(config.copy(openWeatherApiKey = key))
                    }
                },
                label = { Text("OpenWeatherMap API Key") },
                placeholder = { Text("输入 API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            // 推送设置
            Text(
                text = "推送设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingsSwitch(
                title = "熵驱动主动搭话",
                subtitle = "开启后唧唧会随机主动找您聊天，不再受每日次数限制",
                checked = config.entropyEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        configStore.saveConfig(config.copy(entropyEnabled = enabled))
                    }
                },
            )

            Text(
                text = "推送冷却时间: ${config.cooldownMinutes} 分钟",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = config.cooldownMinutes.toFloat(),
                onValueChange = { value ->
                    scope.launch {
                        configStore.saveConfig(config.copy(cooldownMinutes = value.toInt()))
                    }
                },
                valueRange = 15f..120f,
                steps = 6,
            )

            Spacer(modifier = Modifier.weight(1f))

            // 关于
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "关于唧唧",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "唧唧（Jiji）是您的主动式个人助手。她会在后台感知时间、位置和天气，记住您告诉她的每一件事，在合适的时机主动找您聊天。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
