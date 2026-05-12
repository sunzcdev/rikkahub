# Navigation Block Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a navigation block UI component for Amap deeplinks that provides a better user experience than plain text links.

**Architecture:** 
- Create a new `NavigationBlock` composable component
- Modify `PhoneBridge.kt` to return `[navblock]` format
- Update both `Markdown.kt` and `MarkdownNew.kt` to detect and render the new format
- Integrate seamlessly with existing chat message rendering

**Tech Stack:** Kotlin, Jetpack Compose, Material3, kotlinx.serialization

---

### Task 1: Create NavigationBlock Composable

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/NavigationBlock.kt`

**Data Model:**
Create a simple data class for the navigation block:
```kotlin
@Serializable
data class NavBlockData(
    val from: String,
    val to: String,
    val type: String = "driving", // "driving" | "transit" | "walking"
    val url: String,
    val distance: String? = null,
    val eta: String? = null
)
```

**Step 1: Write the NavigationBlock composable**
```kotlin
package me.rerere.rikkahub.ui.components.richtext

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.*
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
                Icon(
                    imageVector = getMapIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
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
                        text = data.from,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Connecting line
                Spacer(
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
                Icon(
                    imageVector = getRouteTypeIcon(data.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
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
                Icon(
                    imageVector = HugeIcons.Navigation01,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("打开高德地图")
            }
        }
    }
}

private fun getMapIcon(): ImageVector = HugeIcons.Map01

private fun getRouteTypeIcon(type: String): ImageVector = when (type.lowercase()) {
    "transit" -> HugeIcons.Bus01
    "walking" -> HugeIcons.Walk01
    else -> HugeIcons.Car01
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
        kotlinx.serialization.json.Json.decodeFromString<NavBlockData>(json)
    } catch (e: Exception) {
        null
    }
}
```

**Step 2: Verify imports are correct**
Check that all imports resolve. Use existing HugeIcons from the project.

**Step 3: Commit**
```bash
git add app/src/main/java/me/rerere/rikkahub/ui/components/richtext/NavigationBlock.kt
git commit -m "feat: add NavigationBlock component"
```

---

### Task 2: Modify PhoneBridge.kt to return [navblock] format

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/PhoneBridge.kt:871-960`

**Step 1: Update handleAmapNavigate to return navblock format**
Find the `handleAmapNavigate` function and modify the return statement:
```kotlin
private suspend fun handleAmapNavigate(
    fromLocation: String,
    toLocation: String,
    routeType: String
): List<UIMessagePart> {
    Log.d(TAG, "handleAmapNavigate: from=$fromLocation, to=$toLocation, type=$routeType")

    // Parse from location
    val fromResult = parseLocation(fromLocation)
    if (fromResult.isFailure) {
        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("error", true)
                put("message", "Invalid from location: ${fromResult.exceptionOrNull()?.message}")
            }.toString()
        ))
    }

    // Parse to location
    val toResult = parseLocation(toLocation)
    if (toResult.isFailure) {
        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("error", true)
                put("message", "Invalid to location: ${toResult.exceptionOrNull()?.message}")
            }.toString()
        ))
    }

    val (fromLat, fromLng, fromName) = fromResult.getOrNull() ?: return listOf(UIMessagePart.Text(
        buildJsonObject { put("error", true); put("message", "Failed to parse from location") }.toString()
    ))

    val (toLat, toLng, toName) = toResult.getOrNull() ?: return listOf(UIMessagePart.Text(
        buildJsonObject { put("error", true); put("message", "Failed to parse to location") }.toString()
    ))

    // Build Amap URI - support both from and to coordinates in route plan
    val uri = buildString {
        if (fromLat != null && fromLng != null && toLat != null && toLng != null) {
            // Both coordinates available - use route plan
            append("androidamap://route?")
            append("sourceApplication=RikkaHub&")
            append("slat=$fromLat&")
            append("slon=$fromLng&")
            append("sname=${Uri.encode(fromName ?: "Start")}&")
            append("dlat=$toLat&")
            append("dlon=$toLng&")
            append("dname=${Uri.encode(toName ?: "Destination")}&")
            append("dev=0&")
            val t = when (routeType.lowercase()) {
                "transit" -> 1
                "walking" -> 2
                else -> 0 // driving
            }
            append("t=$t")
        } else if (toLat != null && toLng != null) {
            // Destination coordinates available - use route plan (will use current location as start if no start coord)
            append("androidamap://route?")
            append("sourceApplication=RikkaHub&")
            if (fromLat != null && fromLng != null) {
                append("slat=$fromLat&")
                append("slon=$fromLng&")
                if (!fromName.isNullOrBlank()) {
                    append("sname=${Uri.encode(fromName)}&")
                }
            }
            append("dlat=$toLat&")
            append("dlon=$toLng&")
            append("dname=${Uri.encode(toName ?: "Destination")}&")
            append("dev=0&")
            val t = when (routeType.lowercase()) {
                "transit" -> 1
                "walking" -> 2
                else -> 0 // driving
            }
            append("t=$t")
        } else {
            // Only place names - use keyword navigation with destination, user can select start point in app
            append("androidamap://keywordNavi?")
            append("sourceApplication=RikkaHub&")
            append("keyword=${Uri.encode(toName ?: toLocation)}&")
            append("style=2")
        }
    }
    Log.d(TAG, "handleAmapNavigate: built uri=$uri")

    // Return navblock format instead of plain text
    val navBlockJson = buildJsonObject {
        put("from", fromName ?: fromLocation)
        put("to", toName ?: toLocation)
        put("type", routeType)
        put("url", uri)
    }.toString()
    
    return listOf(UIMessagePart.Text("[navblock]$navBlockJson"))
}
```

**Step 2: Update handleAmapShow to return navblock format**
```kotlin
private suspend fun handleAmapShow(
    location: String
): List<UIMessagePart> {
    Log.d(TAG, "handleAmapShow: location=$location")

    // Parse location
    val locationResult = parseLocation(location)
    if (locationResult.isFailure) {
        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("error", true)
                put("message", "Invalid location: ${locationResult.exceptionOrNull()?.message}")
            }.toString()
        ))
    }

    val (lat, lng, name) = locationResult.getOrNull() ?: return listOf(UIMessagePart.Text(
        buildJsonObject { put("error", true); put("message", "Failed to parse location") }.toString()
    ))

    // Build Amap URI
    val uri = if (lat != null && lng != null) {
        "androidamap://viewMap?sourceApplication=RikkaHub&poiname=${Uri.encode(name ?: "Location")}&lat=$lat&lon=$lng&dev=0"
    } else {
        "androidamap://keywordNavi?sourceApplication=RikkaHub&keyword=${Uri.encode(name ?: location)}&style=2"
    }

    Log.d(TAG, "handleAmapShow: built uri=$uri")

    // Return navblock format instead of plain text
    val navBlockJson = buildJsonObject {
        put("from", "")
        put("to", name ?: location)
        put("type", "driving") // Default for show location
        put("url", uri)
    }.toString()
    
    return listOf(UIMessagePart.Text("[navblock]$navBlockJson"))
}
```

**Step 3: Build to verify no errors**
```bash
./gradlew app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**
```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/tools/PhoneBridge.kt
git commit -m "feat: update PhoneBridge to return [navblock] format"
```

---

### Task 3: Update Markdown.kt to detect and render [navblock]

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt`

**Step 1: Add navblock detection in MarkdownBlock**
Find the `MarkdownBlock` composable and add detection at the beginning:
```kotlin
@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onClickCitation: (String) -> Unit = {}
) {
    // Check for navblock first
    if (content.startsWith("[navblock]")) {
        val navData = parseNavBlockData(content)
        if (navData != null) {
            NavigationBlock(
                data = navData,
                modifier = modifier
            )
            return
        }
    }

    // ... rest of existing MarkdownBlock code
}
```

**Step 2: Add the import for NavigationBlock**
At the top of Markdown.kt, add:
```kotlin
import me.rerere.rikkahub.ui.components.richtext.NavigationBlock
import me.rerere.rikkahub.ui.components.richtext.parseNavBlockData
```

**Step 3: Build to verify**
```bash
./gradlew app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**
```bash
git add app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt
git commit -m "feat: add navblock support to Markdown.kt"
```

---

### Task 4: Update MarkdownNew.kt to detect and render [navblock]

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/MarkdownNew.kt`

**Step 1: Add navblock detection in MarkdownNew**
Find the `MarkdownNew` composable and add detection at the beginning:
```kotlin
@Composable
fun MarkdownNew(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onClickCitation: (String) -> Unit = {},
) {
    // Check for navblock first
    if (content.startsWith("[navblock]")) {
        val navData = parseNavBlockData(content)
        if (navData != null) {
            NavigationBlock(
                data = navData,
                modifier = modifier
            )
            return
        }
    }

    // ... rest of existing MarkdownNew code
}
```

**Step 2: Add the import for NavigationBlock**
At the top of MarkdownNew.kt, add:
```kotlin
import me.rerere.rikkahub.ui.components.richtext.NavigationBlock
import me.rerere.rikkahub.ui.components.richtext.parseNavBlockData
```

**Step 3: Build to verify**
```bash
./gradlew app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**
```bash
git add app/src/main/java/me/rerere/rikkahub/ui/components/richtext/MarkdownNew.kt
git commit -m "feat: add navblock support to MarkdownNew.kt"
```

---

### Task 5: Test the implementation

**Files:** No changes - testing only

**Step 1: Build and install the APK**
```bash
./gradlew app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

**Step 2: Test navblock rendering**
1. Open RikkaHub
2. Select an assistant with PhoneBridge enabled
3. Send: "用 amap_link 显示天安门的位置"
4. Verify: Navigation block shows up with "导航到 天安门"
5. Tap "打开高德地图" - verify Amap opens

**Step 3: Test navigation with current location**
1. Send: "从当前位置导航到北京西站，驾车"
2. Verify: Navigation block shows from="当前位置", to="北京西站"
3. Tap button - verify Amap opens with navigation

**Step 4: Test fallback behavior**
1. Manually send an invalid navblock: `[navblock]invalid-json`
2. Verify: Falls back to plain text rendering

**Step 5: Test both light/dark modes**
1. Switch app theme
2. Verify navigation block looks good in both modes

---

## Self-Review

### 1. Spec coverage
✅ Data format - Task 1 defines NavBlockData
✅ UI Component - Task 1 implements NavigationBlock
✅ PhoneBridge changes - Task 2 updates to return [navblock]
✅ Markdown rendering - Tasks 3-4 add detection
✅ Error handling - Included in all tasks
✅ Testing - Task 5 covers all cases

### 2. Placeholder scan
✅ No TBD/TODO
✅ All code examples complete
✅ No missing type definitions

### 3. Type consistency
✅ NavBlockData defined once in Task 1, used consistently
✅ parseNavBlockData defined once, used in both Markdown renderers

---

Plan complete and saved to `.omc/plans/navigation-block-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
