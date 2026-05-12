|<div align="center">
|  <img src="docs/icon.png" alt="App Icon" width="100" />
|  <h1>RikkaHub</h1>
|
|  [![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/rikkahub/rikkahub)
|  [![Ask DeepWiki](https://img.shields.io/badge/zread.ai-blue?style=flat&logo=readthedocs)](https://zread.ai/rikkahub/rikkahub)
|
|A native Android LLM chat client with multi-provider switching, proactive AI assistant,
|on-device tools, and a built-in web server. 🤖💬
|
|Click to join our Discord server 👉 [【RikkaHub】](https://discord.gg/9weBqxe5c4)
|
|[简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_TW.md) | English
|</div>
|
|<div align="center">
|  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
|  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
|</div>
|
|## 🚀 Download
|
|🔗 [Download from Website](https://rikka-ai.com/download)
|
|🔗 [Download from Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)
|
|## 💖 Sponsors
|
|<div align="center">
|  <img src="app/src/main/assets/icons/aihubmix-color.svg" alt="Aihubmix" width="50" />
|  <p style="font-size: 16px; font-weight: bold;">Aihubmix</p>
|  <p style="font-size: 14px;">Thanks to <a href="https://aihubmix.com?aff=pG7r">aihubmix.com</a> for their support.</p>
|</div>
|
|## ✨ Features
|
|### 🤖 AI Chat Engine
|
|- **Multi-Provider** — OpenAI / Claude / Google Gemini / Vertex AI, plus any OpenAI-compatible API
|- **Model Cascading** — Global default model → per-assistant override
|- **Message Branching** — Tree-structured conversations: branch, regenerate, switch paths
|- **Streaming / Non-streaming** — Configurable per assistant
|- **Reasoning Rendering** — `think` tag extraction & visualization
|- **Multimodal Input** — Vision, PDF, DOCX, PPTX auto-conversion
|- **Tools / Function Calling** — Unified Tool registration system, cross-provider
|- **MCP Protocol** — SSE + Streamable HTTP transports, per-assistant mounting
|- **Structured Output** — JSON mode on supported providers
|- **Prompt Templates** — Pebble engine, auto-injects time/date/model name
|- **Preset Messages** — Per-assistant default context and system prompt injection
|- **Mode Injection & Lorebook** — Scenario/role settings injected into context
|- **Custom HTTP Headers & Bodies** — Flexible API adapters
|- **Message Favorites** — Bookmark and recall messages
|- **Full-Text Search** — SQLite FTS across conversation history
|- **AI Translation** — Built-in translator page
|- **Smart Suggestions** — Contextual quick replies
|- **SillyTavern Card Import** — Import character cards
|
|### 🛠️ Local Tool System (Native Android)
|
|Toggle on/off per assistant:
|
|Tool|Capability|
|:---:|:---:|
|`eval_javascript`|QuickJS engine, ES2020 JavaScript execution|
|`time_info`|Current time, timezone, day of week|
|`clipboard`|Read / write system clipboard|
|`text_to_speech`|TTS synthesis and playback|
|`get_weather`|OpenWeatherMap (by city or coordinates)|
|`query_perception`|Query historical location/weather data|
|`vibrate_device`|Phone vibration|
|`get_current_location`|Amap GPS with POI/street/address|
|`take_photo_camera`|System camera capture|
|`open_external_app`|Launch apps or deep-links|
|`search_contacts`|Search contacts|
|`make_phone_call`|Open dialer|
|`amap_link`|Amap navigation deeplink|
|`list_directory_contents`|List files on device storage|
|`get_file_info`|Get file metadata|
|`call_contact_by_name`|Search & dial by name|
|
|- **Skills System** — Server-side SKILL.md, dynamically loaded during AI conversation
|
|### 🔍 Search
|
|**15 search providers under one abstraction**: Tavily, Brave, Exa, Bing, Perplexity, Jina, Bocha, Zhipu, Grok, Firecrawl, LinkUp, Metaso, SearXNG, and more.
|
|Per-assistant selection, with `search_web` + `scrape_web` tools.
|
|### 🧠 Memory System
|
|- **ChatGPT-like Memory** — AI auto-records preferences and facts across sessions
|- **Global / Per-Assistant Memory** — Configurable memory isolation
|- **Recent Chat Reference** — Inject recent conversation context
|- **Time Reminder** — Periodic injection
|
|### 🔊 Voice
|
|**TTS** — 7 providers: OpenAI / Gemini / MiniMax / Qwen / Groq / XAI / System TTS
|Full pipeline: text chunking → synthesis → playback
|
|**STT** — Speech recognition plugin, voice input → chat pipeline
|
|### 👥 Group Chat (Multi-Agent Discussion)

| Feature | Description |
|:---:|:---|
| **Multi-Agent Chat** | Pull multiple Assistants into one conversation, each with its own model/Tools/memory |
| **Custom Participants** | Each role can have a different model, system prompt, and tool set |
| **Speaking Order** | Sequential, Random, or Parallel modes |
| **@Mentions** | Use `@角色名` to direct a message to specific participants |
| **Auto-Discuss** | AI participants automatically take turns without human intervention |
| **User Participation** | Users can jump in at any time — AI sees "[User said]: ..." |
| **Real-time Streaming** | Each role's generation streams in real-time, like a real group chat |
| **Participant Metadata** | Every message carries `participantId`, UI distinguishes avatars/names/colors |

Implementation: `GroupChatManager` → `AutoDiscussManager` → `GroupChatMessageBuilder`

### 🖥️ Embedded Web Server
|
|- **Ktor CIO Engine** — Pure Kotlin coroutines, no WebView dependency
|- **REST API** — Conversations CRUD, settings, file management, icons
|- **JWT Auth** — Password → Token, 30-day validity
|- **WebDAV Sync** — Compressed, incremental, bidirectional
|- **NSD Discovery** — Automatic mDNS registration on LAN
|- **SSE** — Real-time event streaming
|
|### 📱 Jiji — Proactive Personal Assistant
|
|RikkaHub ships with a proactive AI assistant — Jiji. It runs as an Android foreground service,
|continuously sensing your state and reaching out when it matters:
|
|- **Perception Collection** — Location (Amap, 1-min interval), Weather (OpenWeatherMap, 1-hour interval)
|- **Entropy-Driven Storage** — Same area → skip, same temperature range → skip. No wasted writes.
|- **Baseline Learning** — Infer life patterns and preferences from conversation
|- **Deviation Detection** — Time mismatch, weather change, long silence, preference gaps
|- **Proactive Chat Generation** — AI-first with rule-based fallback, delivered as notifications
|- **Perception History Query** — Ask "where was I yesterday?" during conversation
|- **Import / Export** — Full JSON serialization, data is portable
|
|### 🎨 UI & Experience
|
|- **Material You Design** — Modern Android design language
|- **Dark Mode** — Full dark theme support
|- **Preset Themes** — Spring / Ocean / Sakura / Black / Autumn
|- **Code Highlighting** — Embedded highlight engine
|- **Markdown Rendering** — Code blocks, LaTeX, tables, Mermaid diagrams
|- **QR Import/Export** — Share provider configs via QR code
|- **Share Receiver** — One-tap send external content to AI
|- **Hardware Key Config** — Custom actions for physical buttons
|- **Crash Reporting** — Built-in CrashHandler
|- **Update Checker** — In-app version detection
|
|## 🏗️ Module Architecture
|
|```
|RikkaHub/
|├── app/          — Main module (UI, ViewModels, Jiji)
|├── ai/           — AI provider abstraction (OpenAI / Claude / Google)
|├── common/       — Shared utilities and extensions
|├── document/     — Document parsing (PDF / DOCX / PPTX)
|├── highlight/    — Code syntax highlighting
|├── search/       — Search SDK (15 providers)
|├── stt/          — Speech recognition
|├── tts/          — Text-to-speech (7 providers)
|├── web/          — Embedded Ktor web server
|├── web-ui/       — Web management UI (React)
|├── talkio/       — Desktop client (Tauri)
|└── locale-tui/   — Localization helper tool
|```
|
|## ✨ Contributing
|
|Built with [Android Studio](https://developer.android.com/studio). PRs welcome!
|
|Tech stack: Kotlin | Jetpack Compose | Koin | Room | DataStore | Ktor | OkHttp | Coil | Material You
|
|> [!TIP]
|> You need a `google-services.json` file at `app` folder to build the app.
|>
|> [!IMPORTANT]
|> The following PRs will be rejected:
|> 1. Translation-related changes (adding new languages)
|> 2. New features — this project is opinionated
|> 3. Large-scale AI-generated refactoring
|
|## 💰 Donate
|
|* [Patreon](https://patreon.com/rikkahub)
|* [爱发电](https://afdian.com/a/reovo)
|
|## ⭐ Star History
|
|If you like this project, please give it a star ⭐
|
|[![Star History Chart](https://api.star-history.com/svg?repos=re-ovo/rikkahub&type=Date)](https://star-history.com/#re-ovo/rikkahub&Date)
|
|## 📄 License
|
|[License](LICENSE)
