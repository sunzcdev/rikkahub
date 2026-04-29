# Deep Interview Spec: 语音输入与后台播报

## Metadata
- Interview ID: di-voice-001
- Rounds: 3
- Final Ambiguity Score: 15%
- Type: brownfield
- Generated: 2026-04-28
- Threshold: 20%
- Status: PASSED

## Clarity Breakdown
| Dimension | Score | Weight | Weighted |
|-----------|-------|--------|----------|
| Goal Clarity | 0.9 | 35% | 0.315 |
| Constraint Clarity | 0.8 | 25% | 0.200 |
| Success Criteria | 0.8 | 25% | 0.200 |
| Context Clarity | 0.9 | 15% | 0.135 |
| **Total Clarity** | | | **0.850** |
| **Ambiguity** | | | **15%** |

## Goal
为 RikkaHub 对话页面添加语音输入（STT）和后台 TTS 播报功能，体验类似豆包。

## Constraints

### 语音输入（STT）
- 交互：按住输入框右侧麦克风按钮 → 说话 → 松手 → 自动转文字 → 直接发送
- STT 提供商：复用现有 `ai` 模块的 Provider 体系。在 `Provider` 接口添加 `transcribeAudio` 方法，各 AI 提供商可选择性实现
- 语言：自动检测
- 无需前台 Service，录音仅在 App 前台活跃时进行
- 需要 RECORD_AUDIO 权限
- 语音输入按钮位于输入框右侧

### 语音播报（TTS）
- 回复内容 TTS 播报已存在（`tts` 模块），基于现有体系扩展
- 后台播报：App 退到后台后 TTS 继续播放
- 通知栏控制：播放/暂停、停止、重听（从头重播当前回复）
- 通知栏仅显示控制按钮，不展示播报内容
- TTS 播报完成后通知自动消失
- 新消息自动播报：开启 TTS 自动播报后，新生成的 AI 回复自动朗读

## Non-Goals
- 不支持发送语音消息作为附件（仅 STT 转文字）
- 语音输入不需要后台 Service（录音仅限前台）
- 通知栏不显示播报内容文本

## Acceptance Criteria
- [ ] 输入框右侧显示麦克风按钮
- [ ] 按住麦克风按钮开始录音，RECORD_AUDIO 权限自动请求
- [ ] 松手后录音自动停止，音频发送到 STT 提供商转文字
- [ ] 转文字完成后自动填入输入框并发送消息
- [ ] STT 提供商通过现有 Provider 设置页面配置
- [ ] TTS 播报时 App 退到后台继续播放
- [ ] 后台播报显示通知栏，控制：播放/暂停、停止、重听
- [ ] TTS 播放完成后通知自动消失
- [ ] 开启自动播报后，新 AI 回复自动 TTS 朗读

## Assumptions Exposed & Resolved
| Assumption | Challenge | Resolution |
|------------|-----------|------------|
| 语音输入是录音附件 | 问用户想要 STT 还是录音 | STT，转文字发送 |
| 后台播报需要前台 Service | 确认是否需要 | 需要 Foreground Service 保证后台播放 |
| 通知完成后保留 | 问用户行为 | 完成后自动消失 |
| STT 单独建 Provider 体系 | 建议复用现有 ai 模块 | 在 Provider 接口加 transcribeAudio |

## Technical Context

### 已有能力
- **TTS 模块** (`tts/`): 完整可用。TtsController + AudioPlayer(ExoPlayer) + 8 providers（OpenAI、Gemini、SystemTTS、MiniMax、Qwen、Groq、xAI、MiMo）
- **TTS UI**: 消息内 TTS 按钮（`ChatMessageActions.kt`）、悬浮控制面板（`TTSController.kt`）、自动播报（`TTSAutoPlay.kt`）、CustomTtsState（`hooks/TTS.kt`）
- **AI Provider** (`ai/`): Provider 接口 + ProviderSetting(OpenAI/Google/Claude) + ProviderManager
- **权限框架**: `rememberPermissionState` + PermissionState（无 RECORD_AUDIO）
- **前台 Service**: WebServerService 作为 Foreground Service 存在，可参考

### 需要新增/修改
- **`ai` 模块**: Provider 接口增加 `transcribeAudio()` 方法；OpenAI 实现 Whisper STT；Google 实现 Gemini STT
- **`app` 模块**: 
  - 语音输入 UI 组件（麦克风按钮 + 录音动画 + 权限处理）
  - 后台 TTS Foreground Service（通知栏控制）
  - AndroidManifest: 注册 Foreground Service、RECORD_AUDIO 权限、FOREGROUND_SERVICE_MICROPHONE
  - 通知渠道：TTS 播报控制通知
  - STT 设置页面（选择使用哪个 Provider/Model 做 STT）
- **设置**: 添加 STT 相关设置（默认提供商等）

## Ontology (Key Entities)

| Entity | Type | Fields | Relationships |
|--------|------|--------|---------------|
| TtsController | core | isAvailable, isSpeaking, playbackState | uses AudioPlayer, TtsSynthesizer |
| AudioPlayer | supporting | ExoPlayer, playbackState | plays TTS audio |
| CustomTtsState | core | isAvailable, isSpeaking, playbackState, provider | wraps TtsController |
| TTSForegroundService | new | notification, mediaSession, TtsController | shows notification, controls TTS |
| STTProvider | new (in ai) | transcribeAudio() | implemented by OpenAI, Google, etc |
| VoiceInputButton | new UI | recording state, audio level | triggers STT, shows waveform |
| Provider | existing | generateText, streamText, transcribeAudio(new) | base interface for AI providers |
| ProviderSetting | existing | OpenAI, Google, Claude | configures provider instances |

## Interview Transcript
<details>
<summary>Full Q&A (3 rounds)</summary>

### Round 1
**Q:** 语音输入方式 + 后台播报确认
**A:** STT（语音转文字）+ 后台 TTS 继续播放，通知栏控制播放暂停、停止、重听

### Round 2
**Q:** STT 方案选择
**A:** 多种可配置，类似 TTS 的 Provider 模式

### Round 3
**Q:** 交互流程 + 按钮位置
**A:** 按住录音→松手转文字→自动发送，按钮在输入框右侧
**Ambiguity:** 15%
</details>
