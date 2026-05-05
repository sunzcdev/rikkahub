package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Location01
import me.rerere.rikkahub.utils.openUrl

@Serializable
data class NavBlockData(
    val from: String,
    val to: String,
    val type: String = "driving", // "driving" | "transit" | "walking"
    val url: String,
    val distance: String? = null,
    val eta: String? = null
)

@Composable
fun NavigationBlock(
    data: NavBlockData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "导航到 ${data.to}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Route info
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // From
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Location01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = data.from.ifEmpty { "当前位置" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Connecting line - use Material color
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .padding(start = 6.dp)
                        .width(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // To
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Location01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = data.to,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Route type
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = getRouteTypeLabel(data.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                // Distance and ETA if available
                if (data.distance != null || data.eta != null) {
                    Text(
                        text = buildString {
                            if (data.distance != null) append(data.distance)
                            if (data.distance != null && data.eta != null) append(" · ")
                            if (data.eta != null) append(data.eta)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Open button
            Button(
                onClick = { context.openUrl(data.url) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("打开高德地图")
            }
        }
    }
}

private fun getRouteTypeLabel(type: String): String = when (type.lowercase()) {
    "transit" -> "公交"
    "walking" -> "步行"
    else -> "驾车"
}

// Helper function to parse navblock data
fun parseNavBlockData(content: String): NavBlockData? {
    return try {
        val json = content.removePrefix("[navblock]")
        Json.decodeFromString<NavBlockData>(json)
    } catch (e: Exception) {
        null
    }
}
