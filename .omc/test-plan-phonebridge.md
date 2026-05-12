# Test Plan: Phone Hardware Bridge

## Environment
- **Device:** Android real device (API 33+ recommended)
- **Build:** `./gradlew app:assembleDebug`
- **Install:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- **Amap API Key:** Required for GPS tests. Configure in Settings → Phone Hardware Bridge
- **Permissions to grant:** CAMERA, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_EXTERNAL_STORAGE (API<33) / READ_MEDIA_IMAGES (API 33+)

## Pre-Test Setup

### 1. Enable PhoneBridge in Assistant
1. Open RikkaHub → Settings → Assistants
2. Select or create an assistant
3. Go to "Local Tools" tab
4. Toggle ON "Phone Hardware Bridge"

### 2. Grant Permissions
1. Settings → Apps → RikkaHub → Permissions
2. Grant: Camera, Location (Fine), Notifications

### 3. Configure Amap API Key (for GPS)
1. Settings → Phone Hardware Bridge
2. Enter valid Amap API key
3. Save

---

## Test Case 1: Vibrate Device (vibrate_device)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC1.1 | Basic vibration | 1. Send: "vibrate for 500ms"<br>2. Agent calls vibrate_device(500) | Device vibrates for ~500ms. Response: `{"success": true, "duration_ms": 500}` | ⬜ |
| TC1.2 | Custom duration | 1. Send: "vibrate for 2 seconds"<br>2. Agent calls vibrate_device(2000) | Device vibrates for ~2s. Response: `duration_ms: 2000` | ⬜ |
| TC1.3 | Zero duration | 1. Send: "vibrate for 0ms" | Tool accepts 0. No vibration (or very brief pulse). Not an error. | ⬜ |
| TC1.4 | No parameter (default) | 1. Send: "vibrate"<br>2. Agent calls vibrate_device() | Default 500ms applied. Device vibrates. | ⬜ |
| TC1.5 | Very long duration | 1. Send: "vibrate for 30 seconds"<br>2. Agent calls vibrate_device(30000) | Device vibrates for 30s (system may cap). Response returns 30000. | ⬜ |

---

## Test Case 2: Make Phone Call (make_phone_call)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC2.1 | Basic dial | 1. Send: "call 10086"<br>2. Agent calls make_phone_call("10086")<br>3. **Approval dialog appears** → approve | Dialer opens with 10086 pre-filled. User must tap call button. Response: `{"success": true, "action": "dial_initiated", "number": "10086"}` | ⬜ |
| TC2.2 | International number | 1. Send: "call +1-555-0123" | Dialer opens with +15550123 | ⬜ |
| TC2.3 | Approval rejected | 1. Agent triggers make_phone_call | Approval dialog shows. Reject it. Agent notified call not placed. | ⬜ |
| TC2.4 | Missing number | 1. Agent calls without phone_number | Error: "phone_number is required" | ⬜ |

---

## Test Case 3: Get Current Location (get_current_location)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC3.1 | GPS success (with valid Amap key) | 1. Send: "where am I?"<br>2. Agent calls get_current_location() | Response includes: latitude, longitude, address, city, province. Values reflect actual location. | ⬜ |
| TC3.2 | Missing Amap API key | 1. Clear API key in Settings<br>2. Send: "where am I?" | Error: "Amap API Key is not configured." | ⬜ |
| TC3.3 | Location permission denied | 1. Revoke location permission<br>2. Send: "where am I?" | Error: "GPS permission (ACCESS_FINE_LOCATION) not granted." | ⬜ |
| TC3.4 | GPS disabled | 1. Turn off GPS on device<br>2. Send: "where am I?" | Tool may timeout. Error: "Failed to obtain GPS location." | ⬜ |
| TC3.5 | Indoor (weak GPS) | 1. Go to basement/interior room<br>2. Send: "where am I?" | May return coarse location or error. Graceful handling. | ⬜ |
| TC3.6 | Rapid successive calls | 1. Send: "get location 3 times fast" | Each call returns independently. No crash or stale data. | ⬜ |

---

## Test Case 4: Take Photo Camera (take_photo_camera)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC4.1 | Capture photo | 1. Send: "take a photo"<br>2. Agent calls take_photo_camera()<br>3. Camera app opens → take photo | Returns JSON with success + image_url. Image displayed in chat. | ⬜ |
| TC4.2 | Cancel capture | 1. Send: "take a photo"<br>2. Camera opens → press back/cancel | Error: "Camera capture was cancelled or failed." | ⬜ |
| TC4.3 | Camera permission denied | 1. Revoke camera permission<br>2. Send: "take a photo" | System camera may still work (intent-based). Or get permission denied from system. | ⬜ |
| TC4.4 | Photo in chat display | 1. Take photo via agent<br>2. Check photo appears in message | Image renders as UIMessagePart.Image in chat history. Tappable to preview. | ⬜ |

---

## Test Case 5: Open External App (open_external_app)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC5.1 | Open by package name | 1. Send: "open settings"<br>2. Agent calls open_external_app("com.android.settings") | Settings app opens. Response: `{"success": true, "launched": "com.android.settings"}` | ⬜ |
| TC5.2 | Open by URL | 1. Send: "open google.com"<br>2. Agent calls open_external_app("https://www.google.com") | Browser opens with Google. | ⬜ |
| TC5.3 | Invalid package | 1. Send: "open com.nonexistent.fakeapp" | Error: "App not found or invalid URL" | ⬜ |
| TC5.4 | Deep link (tel:) | 1. Send: "open tel:10086"<br>2. Agent calls open_external_app("tel:10086") | Dialer opens (intent ACTION_VIEW with tel URI). | ⬜ |
| TC5.5 | Missing identifier | 1. Agent calls without app_identifier | Error: "app_identifier is required" | ⬜ |

---

## Test Case 6: List Directory Contents (list_directory_contents)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC6.1 | List root | 1. Send: "what files are on my device?"<br>2. Agent calls list_directory_contents("/") | Returns list of top-level directories/files with name, size, last_modified. | ⬜ |
| TC6.2 | List subdirectory | 1. Send: "list files in Downloads"<br>2. Agent calls list_directory_contents("Download") | Lists Download directory contents. | ⬜ |
| TC6.3 | Nonexistent directory | 1. Send: "list /fake_folder_xyz" | Error: "Directory does not exist: /fake_folder_xyz" | ⬜ |
| TC6.4 | Permission denied | 1. Revoke storage permission<br>2. Send: "list my files" | Error: "Storage read permission not granted." | ⬜ |
| TC6.5 | Empty directory | 1. Create empty dir or use cache dir | Returns empty files array with file_count: 0 | ⬜ |

---

## Test Case 7: Get File Info (get_file_info)

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC7.1 | File metadata | 1. Send: "check file info for Download/somefile.txt"<br>2. Calls get_file_info("Download/somefile.txt") | Returns name, size, last_modified, is_file, extension. File content NOT included. | ⬜ |
| TC7.2 | Nonexistent file | 1. Send: "get info for /fake.txt" | Error: "File not found: /fake.txt" | ⬜ |
| TC7.3 | Directory info | 1. Get info for "/Download" | Returns is_file: false, extension: "" | ⬜ |
| TC7.4 | Permission denied | 1. Revoke storage permission | Error: "Storage read permission not granted." | ⬜ |

---

## Integration Test Cases

| ID | Scenario | Steps | Expected | Status |
|----|----------|-------|----------|--------|
| TC8.1 | PhoneBridge toggle OFF | 1. Disable PhoneBridge in assistant tools<br>2. Ask agent to vibrate | Agent cannot call vibrate_device. Tool not available. | ⬜ |
| TC8.2 | Multiple tools in one session | 1. Enable PhoneBridge + other tools<br>2. Use vibrate, location, photo in sequence | All tools work independently in same conversation. | ⬜ |
| TC8.3 | Concurrent tool calls | 1. Ask agent to get location AND take photo | Room for debate: agent should call sequentially, not in parallel. Check error handling. | ⬜ |
| TC8.4 | make_call approval flow | 1. Ask agent to call a number<br>2. Approval dialog appears | Dialog shows tool name, number, and confirm/cancel. | ⬜ |

---

## Permission Matrix

| Action | Permission Required | Manifest Declared | Runtime Checked |
|--------|-------------------|-------------------|-----------------|
| vibrate_device | VIBRATE | ✅ | ❌ (always available) |
| make_phone_call | None (ACTION_DIAL) | — | ❌ |
| get_current_location | ACCESS_FINE_LOCATION | ✅ | ✅ |
| take_photo_camera | CAMERA (system app) | ✅ | ✅ (via system intent) |
| open_external_app | None | — | ❌ |
| list_directory_contents | READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES | ✅ | ✅ |
| get_file_info | READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES | ✅ | ✅ |

## Known Limitations

1. **ACTION_DIAL not ACTION_CALL**: Opens dialer UI. User must tap call. No direct call.
2. **Amap dependency**: GPS requires Amap SDK + API key. No fallback to Android LocationManager.
3. **Camera intent-based**: Depends on system camera app. No CameraX direct capture.
4. **Scoped Storage (API 30+)**: `Environment.getExternalStorageDirectory()` returns restricted view. Full file system not accessible.
5. **needsApproval**: Only `make_phone_call` has approval flag. All other PhoneBridge tools execute without user confirmation.
6. **Vibration**: System may cap vibration duration or ignore if device is in vibrate-free mode (DND).
