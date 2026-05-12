# Test Plan: Voice Input (STT) & Background TTS Playback

## Overview

Test plan for `feature_voice_input` branch. Covers voice input via microphone (Speech-to-Text) and background TTS playback features.

## Test Environment

- **Device**: Real Android phone (Android 12+ recommended)
- **Network**: Wi-Fi (required for cloud STT/TTS providers)
- **API Keys Needed**:
  - OpenAI Whisper: valid API key with audio transcription access
  - Gemini: valid API key with Gemini API access
  - TTS: System TTS (built-in, no key) or OpenAI TTS API key

---

## Test Case 1: STT Provider Configuration

### TC1.1: Default STT provider exists
1. Go to Settings > STT Settings
2. Verify default "AiHubMix (OpenAI Whisper)" provider is listed
3. Verify provider has configurable: baseUrl, model, apiKey

### TC1.2: Add OpenAI Whisper provider with valid key
1. Configure OpenAI Whisper provider with valid API key and base URL
2. Save configuration
3. Verify config persists after app restart

### TC1.3: Add Gemini STT provider
1. Add Gemini STT provider with valid API key
2. Save and verify persistence

### TC1.4: Switch STT provider
1. Switch between OpenAI Whisper and Gemini providers
2. Verify selection persists

### TC1.5: Invalid API key handling
1. Configure STT provider with invalid/wrong API key
2. Attempt voice input
3. Verify appropriate error (no crash, error logged)

---

## Test Case 2: Microphone Permission

### TC2.1: First launch — permission dialog
1. Clear app data / fresh install
2. Tap voice input button
3. Verify Android microphone permission dialog appears
4. Verify "Allow" enables recording

### TC2.2: Deny permission
1. Deny microphone permission when prompted
2. Tap voice input button again
3. Verify permission dialog shows again (not permanently denied)

### TC2.3: Permanently deny permission
1. Deny permission twice (or select "Don't ask again")
2. Tap voice input button
3. Verify no crash, button stays idle

### TC2.4: Permission granted state
1. Grant microphone permission in system settings
2. Verify voice input button functions correctly

---

## Test Case 3: Voice Input Recording

### TC3.1: Tap to record — basic flow
1. Open chat page
2. Tap voice input button (mic icon next to send button)
3. Verify UI shows recording state (icon turns primary color, circle background appears)
4. Speak a short phrase (e.g., "Hello world")
5. Release tap
6. Verify button shows processing state briefly
7. Verify transcribed text appears in chat input
8. Verify message auto-sends

### TC3.2: Short recording (< 1 second)
1. Quick tap and immediately release voice button
2. Verify no crash, button returns to idle state

### TC3.3: Long recording (near 60s limit)
1. Hold voice button for extended period (~30s)
2. Verify recording continues smoothly
3. Release and verify transcription works

### TC3.4: Cancel recording mid-way
1. Start recording (press and hold)
2. Slide finger off the button before releasing (implicit cancellation via gesture)
3. Verify button returns to idle, no transcription sent

### TC3.5: Record in different languages
1. Test voice input with: English, Chinese, mixed languages
2. Verify transcription accuracy matches provider capability

### TC3.6: Background noise
1. Record with moderate background noise
2. Verify transcription still returns results (may be less accurate)

---

## Test Case 4: AudioRecorder — Technical Verification

### TC4.1: Audio format verification
1. Record short audio
2. Verify output is WAV format (PCM 16-bit, 16000Hz, mono)
3. Verify WAV header is valid (RIFF + WAVE chunks)

### TC4.2: State machine correctness
1. Verify state transitions: Idle → Recording → Completed → Idle
2. Verify error state on failure
3. Verify cancel() returns to Idle

### TC4.3: Multiple recording sessions
1. Record → transcribe → record again (3+ cycles)
2. Verify no resource leaks (AudioRecord properly released each cycle)

---

## Test Case 5: Transcription — OpenAI Whisper

### TC5.1: Successful transcription
1. Configure OpenAI Whisper provider
2. Record and send for transcription
3. Verify: response contains non-empty text, no errors

### TC5.2: Network error
1. Enable airplane mode
2. Attempt voice input
3. Verify: graceful error handling, no crash, button returns to idle

### TC5.3: Empty audio
1. Record in complete silence
2. Verify: either empty text (not sent) or appropriate handling

---

## Test Case 6: Transcription — Gemini

### TC6.1: Successful transcription
1. Configure Gemini provider (model: gemini-2.5-flash)
2. Record and transcribe
3. Verify text result is returned

### TC6.2: Invalid API key
1. Set wrong Gemini API key
2. Attempt transcription
3. Verify error logged, no crash

---

## Test Case 7: TTS Provider Configuration

### TC7.1: Default TTS providers
1. Go to TTS settings
2. Verify two providers listed: System TTS (built-in) and AiHubMix (OpenAI TTS)
3. Verify each shows configurable parameters

### TC7.2: Switch TTS provider
1. Switch between System TTS and OpenAI TTS
2. Verify setting persists

### TC7.3: TTS-only quoted text setting
1. Go to Display Settings
2. Verify "TTS Only Read Quoted" option exists
3. Toggle and verify effect

### TC7.4: Auto-play TTS after generation
1. Go to Display Settings
2. Verify "Auto Play TTS After Generation" option exists
3. Enable and verify AI response auto-plays via TTS

---

## Test Case 8: TTS Playback — Basic

### TC8.1: Manual TTS playback
1. Receive an AI response message
2. Tap TTS play button on the message
3. Verify audio starts playing from speaker
4. Verify foreground notification appears with controls

### TC8.2: TTS notification controls
1. Start TTS playback
2. Pull down notification shade
3. Verify notification shows: title, play/pause button, stop button, restart button
4. Test Play/Pause — verify audio pauses/resumes
5. Test Stop — verify audio stops, notification dismissed
6. Test Restart — verify audio restarts from beginning

### TC8.3: Pause and resume
1. Start TTS playback
2. Pause from notification
3. Resume from notification
4. Verify audio continues from pause position

### TC8.4: Stop TTS playback
1. Start TTS playback
2. Tap stop button
3. Verify audio stops, notification dismissed, service stops

### TC8.5: Playback state when app closed
1. Start TTS playback
2. Press home button (app goes to background)
3. Verify playback continues
4. Verify notification remains
5. Kill app from recents
6. Verify playback stops, notification removed

---

## Test Case 9: TTS — Long Text Handling

### TC9.1: Long message chunking
1. Generate or input a long AI response (500+ words)
2. Start TTS playback
3. Verify: text is chunked (~160 chars each), chunks are queued and played sequentially

### TC9.2: Skip to next chunk
1. Play TTS for multi-chunk text
2. Tap skip/next button
3. Verify current chunk stops, next chunk plays

### TC9.3: Fast forward
1. Play TTS
2. Tap fast forward button (if available)
3. Verify audio jumps forward ~5 seconds

### TC9.4: Playback speed
1. Play TTS
2. Adjust playback speed
3. Verify speed change takes effect

---

## Test Case 10: TTS — Error & Edge Cases

### TC10.1: No provider configured
1. Remove/disable all TTS providers
2. Attempt TTS playback
3. Verify: error state reported, no crash

### TC10.2: Empty text
1. Attempt TTS with empty message
2. Verify no crash, no playback started

### TC10.3: Network error (cloud TTS provider)
1. Enable airplane mode
2. Attempt TTS with cloud provider (OpenAI TTS)
3. Verify error handling, no crash

### TC10.4: System TTS offline
1. Use System TTS provider
2. Verify it works without network (device-local)

---

## Test Case 11: TTS + STT Integration

### TC11.1: Full voice interaction loop
1. Configure both STT and TTS providers
2. Use voice input to ask a question
3. Verify question is transcribed and sent to AI
4. Wait for AI response
5. Verify auto-TTS plays the response (if auto-play enabled)
6. Or manually trigger TTS on response

### TC11.2: Voice input while TTS is playing
1. Start TTS playback
2. Tap voice input button
3. Verify recording starts correctly (TTS continues or is handled gracefully)

### TC11.3: TTS during voice recording
1. Start voice recording
2. Trigger TTS (if possible)
3. Verify no audio conflicts

---

## Test Case 12: UI & Visual

### TC12.1: Voice button states
1. Verify 3 states have distinct visual appearance:
   - Idle: default icon color, subtle background
   - Recording: primary color icon, ripple circle background
   - Processing: (brief transition state)

### TC12.2: ChatInput layout after merge
1. After syncing with master, verify ChatInput layout:
   - Voice button on LEFT side of text input
   - Text input in center (expands)
   - Send button on right
   - Attachment/plus button on right

### TC12.3: Voice button position on different screen sizes
1. Test on phone and tablet
2. Verify voice button is properly positioned in all layouts

---

## Test Case 13: Service & Resource Management

### TC13.1: No resource leak
1. Perform 10+ recording → transcription cycles
2. Verify no OOM or performance degradation
3. Monitor logcat for AudioRecord release errors

### TC13.2: TTSForegroundService lifecycle
1. Start TTS, verify service is running: `adb shell dumpsys activity services | grep TTSForegroundService`
2. Stop TTS, verify service terminates
3. Verify service type is `mediaPlayback`

### TC13.3: App restart persistence
1. Configure STT and TTS providers
2. Force-stop and restart app
3. Verify provider configurations are still saved

---

## Test Execution Record

| TC# | Result (PASS/FAIL/BLOCKED) | Notes |
|-----|---------------------------|-------|
| TC1.1 | | |
| TC1.2 | | |
| ... | | |
