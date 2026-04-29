# Implementation Plan: 语音输入与后台 TTS 播报

## RALPLAN-DR Summary

### Principles
1. **独立可配置** — STT 提供商选择与聊天 Provider 解耦。用户可用 Claude 聊天 + OpenAI Whisper 做 STT
2. **复用架构模式** — 借鉴 TTS 模块架构（独立模块 + Provider 接口 + Manager），保证一致性
3. **非侵入式 UI** — 语音按钮融入现有 ChatInput 布局，不破坏现有交互流程
4. **后台可靠** — Foreground Service + 通知栏，保证 TTS 后台播报不被系统杀死
5. **生命周期安全** — TtsController 跨 Service/Compose 边界共享，避免状态竞争

### Decision Drivers
1. **ISP 原则** — Provider 接口已有 4 项职责，不宜再增加 STT（接口隔离）
2. **最小改动** — 减少对现有 `ai` 模块的侵入，STT 独立演进
3. **用户体验** — 按住录音→松手发送的交互必须流畅，权限请求无感知
4. **架构一致性** — 与 TTS 模块架构对称（独立 module + Provider 接口 + Manager）

### Viable Options

#### Option A: 独立 STT 模块（推荐）
创建独立 `stt/` Gradle 模块，类似 `tts/`。定义 `SttProvider` 接口，通过适配器包装 AI Provider。
- **Pros:** ISP 合规，STT 选择与聊天解耦，可扩展本地 STT（SpeechRecognizer/Whisper.cpp），与 TTS 架构对称
- **Cons:** 新增 Gradle 模块，需额外配置代码量

#### Option B: Provider 接口扩展（被否决）
在 `Provider<T>` 接口添加 `transcribeAudio()` 默认方法。
- **Invalidation rationale:** 违反 ISP（接口已有 4 项职责）；STT 提供商选择与聊天 Provider 耦合（无法用 Claude 聊天 + Whisper STT）；实际使用必然演变为独立选择，届时 Option B 的接口污染已成定局

#### Option C: Android SpeechRecognizer 直连
直接使用 Android 内置 SpeechRecognizer，不通过 Provider 体系。
- **Invalidation rationale:** 不可配置，效果一般，不符合项目"用户可选择 Provider"理念

## Revised Plan

### Step 1: 创建 STT 模块

**新建模块:**
- `stt/` Gradle 模块（参考 `tts/` 模块结构）
- `settings.gradle.kts` 添加 `include(":stt")`（现有第37-44行列出了所有模块）
- `app/build.gradle.kts` 添加 `implementation(project(":stt"))`
- `stt/build.gradle.kts` 创建，依赖：`libs.okhttp`、`libs.kotlinx-serialization-json`、`libs.koin`

**新建文件:**
- `stt/src/main/java/me/rerere/stt/model/SttResult.kt`
  - `SttResult(text: String, language: String?, durationMs: Long)`
  - 不使用 `ByteArray` data class（equals/hashCode break），音频数据通过 byte[] 参数传递
- `stt/src/main/java/me/rerere/stt/provider/SttProvider.kt`
  ```kotlin
  interface SttProvider {
      suspend fun transcribe(audioData: ByteArray, format: SttAudioFormat, sampleRate: Int): SttResult
  }
  ```
- `stt/src/main/java/me/rerere/stt/provider/SttProviderSetting.kt`
  - Sealed class: OpenAIWhisper, Gemini
  - OpenAIWhisper 携带 apiKey、baseUrl 参数（由 app 模块通过 PreferencesStore 传递）
- `stt/src/main/java/me/rerere/stt/provider/SttManager.kt`
  - 路由到对应 Provider 实现

### Step 2: 实现 OpenAI Whisper STT Provider

**新建文件:**
- `stt/src/main/java/me/rerere/stt/provider/providers/OpenAIWhisperProvider.kt`

**实现:**
- 调用 OpenAI `/audio/transcriptions` API（multipart/form-data），使用 OkHttp `MultipartBody`
- 支持多语言自动检测
- API Key 从 `SttProviderSetting.OpenAIWhisper.apiKey` 读取（由 app 模块通过 PreferencesStore 管理，类似 TTS 模式）
- API Key 配置通过 `PreferencesStore` 存储，`stt` 模块不直接读写 DataStore，由 app 模块注入

### Step 3: 实现 Gemini STT Provider

**新建文件:**
- `stt/src/main/java/me/rerere/stt/provider/providers/GeminiSttProvider.kt`

**实现:**
- 调用 Gemini API `generateContent`，音频作为 `inlineData`
- 提示词："Transcribe the audio, including the language detection."

### Step 4: AndroidManifest — 权限和 Service 声明

**文件:**
- `app/src/main/AndroidManifest.xml`

**改动:**
- 添加 `RECORD_AUDIO` 权限
- 添加 `FOREGROUND_SERVICE_MICROPHONE` 权限
- 添加 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限（Android 14+ 要求 mediaPlayback 类型）
- 注册 `TTSForegroundService`，声明 `android:foregroundServiceType="mediaPlayback"`
  ```xml
  <service
      android:name=".service.TTSForegroundService"
      android:exported="false"
      android:foregroundServiceType="mediaPlayback" />
  ```
- 添加 `<uses-feature android:name="android.hardware.microphone" android:required="false" />`

### Step 5: 通知渠道

**文件:**
- `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt`
- `app/src/main/res/values/strings.xml`

**改动:**
- RikkaHubApp 添加 `notification_channel_tts_playback` 通知渠道
- strings.xml 添加对应字符串资源

### Step 6: TtsController 重构为 DI 单例

**文件:**
- `tts/src/main/java/me/rerere/tts/controller/TtsController.kt`
- `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/hooks/TTS.kt`
- `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt`

**改动:**
- TtsController 改为由 Koin DI 管理（单例，作用域为 Application）
- `AppModule.kt` 添加 TtsController 提供者定义（Koin `single { TtsController(...) }`）。Koin 单例惰性初始化，无需在 Application.onCreate 中显式创建
- `TtsController.dispose()` 改为仅取消内部子 Job，不取消 SupervisorJob（避免单例永久失效）。或者引入 `reset()` 方法替代 `dispose()` 重建状态
- `CustomTtsStateImpl` 通过 Koin inject 注入 TTS Controller，而非在 `remember{}` 中创建
- TtsController 添加播放状态回调/事件，用于 Service 同步

### Step 7: 后台 TTS Foreground Service

**新建文件:**
- `app/src/main/java/me/rerere/rikkahub/service/TTSForegroundService.kt`

**功能:**
- Foreground Service，通过 Koin 注入 TtsController 单例
- 通知栏构建：播放/暂停、停止、重听（从头播放）三个 Action（`NotificationCompat.Action`）
- ExoPlayer 实例由 TtsController 管理，Service 仅控制播放状态
- Service 生命周期：有 TTS 播放时 startForeground，播放完成 stopSelf
- `onTaskRemoved()` 回调：用户滑动移除应用时，Service 不自杀，维持前台通知（系统会显示低优先级通知）

**通知栏 Action 实现:**
- 播放/暂停：调用 ttsController.pause() / ttsController.resume()
- 停止：调用 ttsController.stop() + stopSelf()
- 重听：调用 ttsController.stop() → ttsController.speak(text)

### Step 8: CustomTtsState 集成 Service

**文件:**
- `app/src/main/java/me/rerere/rikkahub/ui/hooks/TTS.kt`

**改动:**
- CustomTtsStateImpl.speak() 中，调用 controller.speak() 之前，使用 `context.startForegroundService(Intent(context, TTSForegroundService::class.java))` 启动 Service
- CustomTtsStateImpl 监听播放状态，播放完成/停止时调用 `context.stopService(Intent(context, TTSForegroundService::class.java))`
- Service 通过 Koin 共享 TtsController 实例，因此 Service 和 UI 操作同一播放状态

### Step 9: 音频录制模块

**新建文件:**
- `app/src/main/java/me/rerere/rikkahub/utils/AudioRecorder.kt`

**功能:**
- 使用 `AudioRecord`（非 MediaRecorder），PCM 16-bit 单声道 16kHz（Whisper API 兼容格式）
- PCM → WAV 转换（添加 WAV header），OpenAI Whisper 要求 WAV/MP3/M4A/Opus 格式
- 录音状态管理（Idle → Recording → Completed）
- 最大录音时长 60s
- 振幅回调（用于 UI 波形显示）

### Step 10: 语音输入 UI 组件

**新建文件:**
- `app/src/main/java/me/rerere/rikkahub/ui/components/voice/VoiceInputButton.kt`

**前置:**
- `PermissionTypes.kt` 定义 `PermissionMicrophone`（`Manifest.permission.RECORD_AUDIO`），遵循 `PermissionCamera` 模式

**功能:**
- 麦克风按钮（HugeIcons.Mic01），位于输入框右侧发送按钮旁
- 录音状态管理：Idle → Recording (long press) → Processing → Done
- combinedClickable:
  - `onLongClick` → 开始录音
  - `onClick` → 无操作（或短按触发其它功能）
- 录音动画（波纹/声波振幅效果）
- RECORD_AUDIO 权限自动请求（复用 `PermissionManager` + `rememberPermissionState`）
- 录音结束后调用 SttManager.transcribe()
- 转文字完成后自动发送消息（调用 onSendCallback）

### Step 11: ChatInput 集成语音按钮

**文件:**
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt`

**改动:**
- 在发送按钮（ArrowUp02）右侧添加 VoiceInputButton
- 传递 onVoiceResult: (String) -> Unit 回调，填入输入框并发送

### Step 12: STT 设置 + DataStore

**文件:**
- `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- 设置 UI 页面

**改动:**
- PreferencesStore 添加 `STT_PROVIDERS` 键（存储已配置 Provider 列表，JSON 序列化）和 `SELECTED_STT_PROVIDER` 键（当前选中），镜像 TTS 的 `TTS_PROVIDERS`/`SELECTED_TTS_PROVIDER` 模式
- STT Provider 设置 UI（复用现有 Provider 管理 UI 或新建简单的选择器）

### Step 13: 自动播报增强

**文件:**
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/TTSAutoPlay.kt`

**改动:**
- 触发点: `CustomTtsStateImpl.speak()` 中先启动 Service，再调用 TtsController
- 后台播报不受前台生命周期影响（Service 处理播放，UI 仅监听状态变化）
- Composable TTSAutoPlay 无需感知 Service 存在，CustomTtsStateImpl 的 speak() 内部统一处理 Service 启动

## Acceptance Criteria
- [ ] 输入框右侧显示麦克风按钮
- [ ] 长按麦克风开始录音，RECORD_AUDIO 权限自动请求
- [ ] 录音动画显示声波/振幅效果
- [ ] 松手后录音停止，音频编码为 WAV/Opus
- [ ] 调用 SttManager.transcribe()，转文字
- [ ] 转文字完成后自动填入输入框并发送
- [ ] STT 设置独立于聊天 Provider（可用 Claude 聊天 + Whisper STT）
- [ ] OpenAI Whisper STT 正常工作
- [ ] Google Gemini STT 正常工作
- [ ] TTS 播报时 App 退到后台继续播放
- [ ] 通知栏显示播放/暂停、停止、重听按钮
- [ ] TTS 播放完成后通知自动消失
- [ ] 开启自动播报后，新 AI 回复自动朗读（前台+后台）
- [ ] 没有麦克风的设备可正常安装（uses-feature not required）
- [ ] Lint 通过：`./gradlew :app:lintDebug`

## Risks and Mitigations
| Risk | Mitigation |
|------|------------|
| **RECORD_AUDIO 权限被拒** | 使用 PermissionManager + rationale dialog |
| **录音文件过大** | 60s 最大时长；PCM→WAV 编码优化 |
| **后台 TTS 被系统杀** | Foreground Service + 高优先级通知 |
| **TtsController 跨边界状态竞争** | DI 单例 + 同步锁（synchronized） |
| **STT Provider 不支持** | 降级提示用户选择其他 Provider |
| **Whisper API 不支持 PCM** | AudioRecorder 输出 WAV 格式（含 header） |
| **多语言识别不准** | 自动检测 + 后续支持手动指定语言 |

## Verification Steps
1. 编译：`./gradlew :app:assembleDebug`
2. Lint：`./gradlew :app:lintDebug`
3. 手动测试：长按录音 → 松手 → 转文字 → 自动发送
4. 手动测试：TTS 播报 → Home → 通知栏控制播放/暂停/停止/重听
5. 手动测试：TTS 播放完 → 通知自动消失
6. 手动测试：自动播报开关 → 新消息是否朗读
7. 手动测试：Claude 聊天 + OpenAI Whisper STT 混合配置

## ADR

### Decision
创建独立 `stt/` Gradle 模块，定义 `SttProvider` 接口。OpenAI Whisper 和 Gemini 通过适配器实现。STT 设置独立于聊天 Provider。

### Drivers
- **ISP 合规**: Provider 接口已有 4 项职责，不宜再增
- **解耦**: 用户必须能独立选择 STT Provider（如 Claude 聊天 + Whisper STT）
- **对称性**: 与 TTS 模块架构一致，降低认知负担
- **可扩展**: 未来可添加 Android SpeechRecognizer 等本地 STT

### Alternatives considered
- **Provider 接口扩展**: 被否决。违反 ISP，STT 选择与聊天 Provider 耦合，实际使用必然演变为独立选择。Architect 审批评语："Option A 会不可避免地演变成 Option B，但会带着 Option B 所有的复杂性加上 Option A 的接口污染"
- **Android SpeechRecognizer**: 被否决。不符合可配置理念

### Why chosen
- 独立模块架构与 TTS 对称，开发者可快速理解
- 用户可为 STT 选择任意 Provider，不受聊天 Provider 限制
- 新增 Gradle 模块的成本远低于后续解耦的重构成本

### Consequences
- 需要在 `app/build.gradle.kts` 添加 `implementation(project(":stt"))`
- 用户在设置页面需额外选择 STT Provider（但模式与 TTS 完全相同）
- ChatGPT 和 Gemini 需要重复 API Key 配置（复用现有设置）

### Follow-ups
- 添加 Android SpeechRecognizer 作为免费离线 STT 选项
- 支持流式 STT（边说边转写，降低等待感）
- 添加 ModelAbility.STT 枚举用于 Provider 声明 STT 能力（跨模块共享）

## Changelog
- v2 (Architect review): 独立 STT 模块取代 Provider 接口扩展。TtsController DI 单例取代 remember 内创建。修复文件路径引用。添加 ByteArray 警告。添加 uses-feature microphone。
- v3 (Critic review): 添加 foregroundServiceType="mediaPlayback"+FOREGROUND_SERVICE_MEDIA_PLAYBACK。settings.gradle.kts 添加 :stt。明确 API Key 注入模式（app→SttManager）。修复协程作用域（dispose→子 Job 取消）。AudioRecord 确定（非 MediaRecorder）。图标 Mic→Mic01。添加 PermissionMicrophone。STT_PROVIDERS+SELECTED_STT_PROVIDER 双键。Step 13 精确触发点。
