# Deep Interview Spec: Phone Hardware Bridge

## Metadata
- Interview ID: phonebridge-001
- Rounds: 3
- Final Ambiguity Score: 18.75%
- Type: brownfield
- Generated: 2026-05-01
- Threshold: 20%
- Status: PASSED

## Clarity Breakdown
| Dimension | Score | Weight | Weighted |
|-----------|-------|--------|----------|
| Goal Clarity | 0.95 | 35% | 0.3325 |
| Constraint Clarity | 0.85 | 25% | 0.2125 |
| Success Criteria | 0.50 | 25% | 0.1250 |
| Context Clarity | 0.95 | 15% | 0.1425 |
| **Total Clarity** | | | **0.8125** |
| **Ambiguity** | | | **18.75%** |

## Goal
Provide AI agent with centralized access to phone hardware capabilities — GPS (Amap), camera (intent-based), vibration, phone calls, external app launch, and file system (read-only metadata) — togglable per-assistant via existing LocalToolOption system.

## Constraints
- **GPS**: Amap (高德) SDK only, single-shot location, needs API key configured in settings
- **Camera**: Intent-based system camera (ActivityResultContracts.TakePicture), reuses existing AppEventBus → RouteActivity infrastructure
- **Phone calls**: ACTION_DIAL (opens dialer UI, not direct call), only action needing user approval — but needsApproval is per-Tool, requiring action split
- **File system**: Read-only metadata (name, size, modified time). No file content reading for security
- **External apps**: Package name or deep-link URL
- **Vibration**: One-shot vibration with configurable duration
- **Permissions**: No runtime checks in tool handlers — assumes permissions granted via Compose permission system

## Non-Goals
- Direct ACTION_CALL (phone dial without user confirmation) — intentionally avoided for safety
- File content read/write — metadata only
- Continuous GPS tracking — single-shot only
- Direct CameraX capture — intent-based only
- iOS or desktop support — Android only

## Acceptance Criteria
- [ ] PhoneBridge toggle appears in Assistant Local Tools settings page
- [ ] Agent with PhoneBridge enabled can vibrate device
- [ ] Agent can open dialer with specified number
- [ ] Agent can get current location (with valid Amap API key)
- [ ] Agent can trigger system camera and receive photo URI
- [ ] Agent can open external app by package name
- [ ] Agent can open URL via deep link
- [ ] Agent can list directory contents (read-only metadata)
- [ ] Agent can get single file info
- [ ] Tool description accurately reflects all 6 capabilities
- [ ] Error handling for missing Amap API key returns clear message
- [ ] Error handling for cancelled camera returns clear message
- [ ] Invalid package name for open_external_app returns error
- [ ] Nonexistent file path returns error
- [ ] `make_call` shows approval dialog before execution (requires action split)

## Assumptions Exposed & Resolved
| Assumption | Challenge | Resolution |
|------------|-----------|------------|
| PhoneBridge not yet implemented | Checked android_studio branch | Not on android_studio, but fully implemented on dev |
| Camera needs new code | Checked existing infra | Reuses AppEventBus.TakePhoto + RouteActivity |
| GPS needs new LocationManager | Asked user preference | Keep Amap, no fallback needed |
| All actions safe from approval | Asked user preference | Only make_call should require approval |
| needsApproval per-action works | Checked GenerationHandler | needsApproval is per-Tool — make_call needs to be split into separate tool |

## Technical Context
### Existing Infrastructure (dev branch)
- `PhoneBridge.kt` — class with 6 action handlers
- `LocalToolOption.PhoneBridge` — sealed class entry with SerialName("phone_bridge")
- `LocalTools.getTools()` — includes PhoneBridge when toggled
- `AssistantLocalToolPage.kt` — UI toggle present
- `AppModule.kt` — LocalTools injected into ChatService + GroupChatManager
- `libs.amapLocation` — Amap SDK in version catalog
- `PreferencesStore.AMAP_API_KEY` — API key setting exists
- `AppEvent.TakePhoto` — camera event bridging
- `FileProvider` — configured in manifest + file_paths.xml

### Issues to Resolve
1. **needsApproval architecture**: PhoneBridge is 1 Tool with 6 actions. needsApproval is per-Tool. make_call needs approval but others don't. Solution: split make_call into standalone Tool `make_phone_call` with `needsApproval = true`
2. **ACTION_DIAL vs ACTION_CALL**: Current uses ACTION_DIAL (opens dialer UI). Consider if user wants direct calling
3. **No permission checks**: Runtime permission check should be added to GPS (ACCESS_FINE_LOCATION) and File System (READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES)
4. **No Amap SDK proguard rules**: May need to verify proguard config
5. **Single descriptive string too long**: Current tool description lists all 6 actions in one string. Could optimize for token cost

## Ontology (Key Entities)
| Entity | Type | Fields | Relationships |
|--------|------|--------|---------------|
| PhoneBridge | core domain | context, eventBus, getAmapApiKey | contains 6 action handlers |
| LocalToolOption.PhoneBridge | configuration | serialName | enables/disables PhoneBridge in getTools() |
| AppEvent.TakePhoto | event | onResult callback | bridges PhoneBridge → RouteActivity camera launcher |
| AMapLocationClient | external SDK | apiKey, option | provides GPS location |
| Tool | framework | name, description, parameters, execute, needsApproval | wraps each phone bridge action |

## Interview Transcript
<details>
<summary>Full Q&A (3 rounds)</summary>

### Round 1
**Q:** Which hardware features essential for v1 vs nice-to-have?
**A:** Only input hardware (GPS + Camera)
**Ambiguity:** 51.5%

### Round 2
**Q:** Camera capture mode + GPS mode?
**A:** Check existing camera code first (reuse), GPS single-shot
**Ambiguity:** ~40%

### Round 3
**Q:** GPS approach + approval requirements?
**A:** Keep Amap, only make_call needs approval
**Ambiguity:** 18.75%

</details>
