# Navigation Block Design

**Date:** 2026-05-05
**Author:** RikkaHub Team

## Overview

Design a navigation block UI component for Amap deeplinks that provides a better user experience than plain text links.

## Data Format

### Message Format
Navigation blocks are identified by the `[navblock]` prefix followed by a JSON object:

```
[navblock]{"from":"当前位置","to":"郑州东站","type":"driving","url":"androidamap://..."}
```

### JSON Schema

```typescript
interface NavBlockData {
  from: string;           // 起点名称或坐标
  to: string;             // 终点名称或坐标
  type: "driving" | "transit" | "walking";  // 路线类型
  url: string;            // Amap deeplink
  distance?: string;      // 可选：距离，如 "10.5公里"
  eta?: string;           // 可选：预计时间，如 "25分钟"
}
```

## UI Component: NavigationBlock

### Layout Structure

```
┌─────────────────────────────────┐
│ 🗺️  导航到 {to}                │  ← 标题栏
├─────────────────────────────────┤
│ 📍  {from}                    │  ← 起点
│  │                             │  ← 连接线
│ 📍  {to}                      │  ← 终点
│                                 │
│ 🚗  驾车                       │  ← 路线类型
├─────────────────────────────────┤
│         [打开高德地图]          │  ← 操作按钮
└─────────────────────────────────┘
```

### Component Properties

```kotlin
@Composable
fun NavigationBlock(
  data: NavBlockData,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {}
)
```

### Design Details

- **Container**: Material3 Card with 12dp rounded corners
- **Padding**: 16dp internal padding
- **Title**: Icon + Text, medium emphasis
- **Route Info**: Two rows with location icons, connected by a vertical line
- **Route Type**: Icon + label (驾车/公交/步行)
- **Button**: Full-width, primary color, rounded corners
- **Theme**: Uses app's Material3 color scheme for consistency

## Integration Points

### 1. PhoneBridge.kt

Modify the `amap_link` tool to return the `[navblock]` format instead of plain text:

```kotlin
// Instead of returning just the URL:
return listOf(UIMessagePart.Text(uri))

// Return the navblock format:
val navBlockJson = buildJsonObject {
  put("from", fromName ?: fromLocation)
  put("to", toName ?: toLocation)
  put("type", routeType)
  put("url", uri)
}.toString()
return listOf(UIMessagePart.Text("[navblock]$navBlockJson"))
```

### 2. Markdown Rendering

Update both `Markdown.kt` and `MarkdownNew.kt` to detect and render the `[navblock]` format:

- In `preProcess()` or during parsing, detect the `[navblock]` prefix
- Parse the JSON data
- Render using the new `NavigationBlock` component instead of plain text

### 3. ChatMessage Integration

Ensure `ChatMessage.kt` properly handles the new component by passing through the navigation block rendering.

## Icons to Use

| Type | Icon |
|------|------|
| Map | 🗺️ or HugeIcons.Map |
| Location | 📍 or HugeIcons.Location |
| Driving | 🚗 or HugeIcons.Car |
| Transit | 🚌 or HugeIcons.Bus |
| Walking | 🚶 or HugeIcons.Walk |

## Error Handling

- If JSON parsing fails: Fall back to rendering as plain text
- If url is missing: Show error state in the block
- If from/to are missing: Show reasonable defaults

## Testing

Test cases:
1. Navigate with coordinates only
2. Navigate with place names only
3. Navigate from current location
4. Navigate with distance/ETA
5. Navigate with all route types
6. Invalid JSON fallback
7. Dark mode
8. Light mode

## Success Criteria

- LLM can reliably generate the `[navblock]` format
- Users can easily tap to open navigation
- UI is consistent with app design
- Gracefully handles errors
- Works in both light/dark modes
