
# Deep Interview Spec: Amap Integration

## Metadata
- Interview ID: amap-integration
- Rounds: 4
- Final Ambiguity Score: 25%
- Type: brownfield
- Generated: 2026-05-01
- Threshold: 20%
- Status: BELOW_THRESHOLD_EARLY_EXIT (user asked to proceed)

## Clarity Breakdown
| Dimension | Score | Weight | Weighted |
|-----------|-------|--------|----------|
| Goal Clarity | 0.85 | 0.35 | 0.30 |
| Constraint Clarity | 0.75 | 0.25 | 0.19 |
| Success Criteria | 0.70 | 0.25 | 0.18 |
| Context Clarity | 0.95 | 0.15 | 0.14 |
| **Total Clarity** | | | **0.81** |
| **Ambiguity** | | | **0.19 (19%)** |

## Goal

Add 4 new tools to PhoneBridge for Amap (高德地图) integration:

1. `amap_navigate_open` - Open Amap navigation directly (requires approval)
2. `amap_navigate_link` - Return Amap navigation deeplink (no approval)
3. `amap_show_open` - Open Amap to show a location directly (no approval)
4. `amap_show_link` - Return Amap location deeplink (no approval)

**Key features:**
- All tools accept: lat/lng coordinates, place names, or "current" (auto-fetches current location via Amap)
- Navigation tools also accept `route_type` (driving/transit/walking, default: driving)
- If Amap not installed: return error (don't fall back to system map)
- Builds on existing PhoneBridge pattern and Amap integration

## Constraints

- Must follow existing PhoneBridge code patterns
- Must reuse existing Amap API key and location logic
- Must integrate with existing `getAllTools()` list
- Only `amap_navigate_open` requires approval
- If Amap not installed: return error, don't fall back
- Use Amap URI scheme for deeplinks

## Non-Goals

- Not adding Amap SDK beyond existing location SDK
- Not implementing custom map UI within RikkaHub
- Not supporting Google Maps or other map apps
- Not implementing turn-by-turn within RikkaHub

## Acceptance Criteria

- [ ] 4 new tools added to PhoneBridge
- [ ] All tools support 3 location input types: coordinates, names, "current"
- [ ] Navigation tools support route_type parameter
- [ ] `amap_navigate_open` has `needsApproval = true`
- [ ] Tools correctly handle "current" location (auto-fetch via existing Amap location logic)
- [ ] If Amap not installed: return clear error
- [ ] All tools properly registered in `getAllTools()`
- [ ] Code builds successfully
- [ ] Tested on real device:
  - [ ] Navigation from coordinate to coordinate
  - [ ] Navigation from current location to place name
  - [ ] Showing a location by name
  - [ ] Showing a location by coordinates
  - [ ] Link-only modes return correct URIs

## Assumptions Exposed &amp; Resolved

| Assumption | Challenge | Resolution |
|------------|-----------|------------|
| How many tools? | User chose 4 separate tools | 2 navigation (open/link), 2 show (open/link) |
| Direct vs link? | User wanted separate tools for each mode | Each behavior is a distinct tool |
| Fallback to system map? | User chose error | Return clear error if Amap not installed |
| Auto-fetch current location? | User yes | Tools automatically fetch if input is "current" |

## Technical Context

Brownfield project with existing PhoneBridge implementation:

- File: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/PhoneBridge.kt`
- Pattern: Lazy Tool properties with execute function calling private handleXxx()
- Existing Amap integration: `get_current_location` tool uses Amap Location SDK 6.4.1
- Existing patterns: permission checks, Intent launching, JSON serialization via kotlinx-serialization
- Tool registration: `getAllTools()` returns list of all tools
- Approval mechanism: `needsApproval` property on Tool

**Amap URI Scheme (from docs):**

- Show location: `amapuri://route/plan/?dlat={lat}&amp;dlng={lng}&amp;dname={name}&amp;dev=0&amp;t=0`
- Navigation: `amapuri://route/plan/?slat={startLat}&amp;slng={startLng}&amp;sname={startName}&amp;dlat={endLat}&amp;dlng={endLng}&amp;dname={endName}&amp;dev=0&amp;t={type}`
- Route types: t=0=driving, t=1=transit, t=2=walking
- Search: `amapuri://poi/around?keywords={name}&amp;dev=0`

## Ontology (Key Entities)

| Entity | Type | Fields | Relationships |
|--------|------|--------|---------------|
| AmapNavigateOpenTool | core | name, description, params (from, to, route_type), needsApproval=true | Extends PhoneBridge |
| AmapNavigateLinkTool | core | name, description, params (from, to, route_type) | Extends PhoneBridge |
| AmapShowOpenTool | core | name, description, params (location) | Extends PhoneBridge |
| AmapShowLinkTool | core | name, description, params (location) | Extends PhoneBridge |

## Ontology Convergence

| Round | Entity Count | New | Changed | Stable | Stability Ratio |
|-------|--------------|-----|---------|--------|----------------|
| 1 | 3 | 3 | - | - | - |
| 2 | 4 | 1 | 0 | 3 | 75% |
| 3 | 4 | 0 | 0 | 4 | 100% |
| 4 | 4 | 0 | 0 | 4 | 100% |

## Interview Transcript

&lt;details&gt;
&lt;summary&gt;Full Q&amp;A (4 rounds)&lt;/summary&gt;

### Round 1
**Q:** Navigation modes, location types, tool count
**A:** Both options for navigation, all location types, let me think more

### Round 2
**Q:** Tool design (3/1/2 tools), approval needed, route type option, auto-fetch current location
**A:** 2 tools, only navigation needs approval, route type optional default driving, auto-fetch yes

### Round 3
**Q:** Tool output format, Amap fallback, testing scope
**A:** User clarified: need both direct open and link return modes, LLM decides

### Round 4
**Q:** Final tool structure (4 tools vs 2+param), proceed?
**A:** 4 separate tools, proceed to implementation

&lt;/details&gt;

