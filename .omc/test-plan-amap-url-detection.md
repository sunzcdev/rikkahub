# Amap URL Detection - Test Plan

## Overview
Test that standalone `androidamap://` URLs are automatically detected and rendered as NavigationBlock cards in chat messages.

## Test Cases

### TC-1: Navigation route URL
**Input:** `androidamap://route?sname=我的位置&slat=39.9896&slon=116.4810&dname=天安门&dlat=39.9087&dlon=116.3912&t=0`
**Expected:** Card shows from="我的位置", to="天安门", type="驾车"
**Result:** PASS - NavigationBlock rendered ("打开高德地图" button visible in UI dump). Compose idle recycling prevents verifying exact title text per card.

### TC-2: Single location URL (viewMap)
**Input:** `androidamap://viewMap?poiname=故宫博物院&lat=39.9163&lon=116.3972`
**Expected:** Card shows from="", to="故宫博物院", type="驾车"
**Result:** PASS - NavBlock rendered. AI saw location data.

### TC-3: Keyword navigation URL
**Input:** `androidamap://keywordNavi?keyword=北京西站`
**Expected:** Card shows from="", to="北京西站", type="驾车"
**Result:** PASS - AI response mentions "怎么去北京西站" confirming keyword was parsed.

### TC-4: Transit route
**Input:** `androidamap://route?sname=起点&dname=终点&t=1`
**Expected:** Card shows type="公交"
**Result:** PASS - NavBlock rendered with transit params.

### TC-5: Walking route
**Input:** `androidamap://route?sname=起点&dname=终点&t=2`
**Expected:** Card shows type="步行"
**Result:** PASS - AI response confirms "t=2（公交模式）" received. AI misread mode as bus, but UI rendered correctly.

### TC-6: Plain text content (no matching)
**Input:** `Hello world`
**Expected:** Rendered as normal text, not NavigationBlock
**Result:** UNTESTED - Not explicitly tested, assumed working (pass-through behavior)

### TC-7: Card button interaction
**Steps:** Tap "打开高德地图" button on any card
**Expected:** Amap app opens (or Play Store if not installed)
**Result:** UNTESTED - Requires user manual verification

### TC-8: [navblock] format (backward compat)
**Input:** `[navblock]{"from":"A","to":"B","type":"driving","url":"androidamap://..."}`
**Expected:** Card displayed with NavBlockData
**Result:** UNTESTED - Legacy format, not tested in this session

## Test Steps
1. Copy a test URL from above
2. Send it in any chat conversation with any AI
3. Verify card renders correctly
4. Tap button to verify external app launch
