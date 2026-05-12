|<div align="center">
|  <img src="docs/icon.png" alt="App 图标" width="100" />
|  <h1>RikkaHub</h1>
|
|一个原生 Android LLM 聊天客户端，支持切换不同供应商进行对话 🤖💬
|
|[English](README.md) | [繁體中文](README_ZH_TW.md) | 简体中文
|
|点击链接加入群聊 👉 [【RikkaHub】](https://qm.qq.com/q/I8MS0UfK0u)
|
|</div>
|
|<div align="center">
|  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
|  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
|</div>
|
|## 🚀 下载
|
|🔗 [前往官网下载](https://rikka-ai.com/download)
|🔗 [前往 Google Play 下载](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)
|
|## 💖 赞助商
|
|<div align="center">
|  <img src="app/src/main/assets/icons/aihubmix-color.svg" alt="Aihubmix" width="50" />
|  <p style="font-size: 16px; font-weight: bold;">Aihubmix</p>
|  <p style="font-size: 14px;">感谢 <a href="https://aihubmix.com?aff=pG7r">aihubmix.com</a> 的资金支持。</p>
|</div>
|
|## ✨ 功能特色
|
|### 🤖 AI 聊天引擎
|
|- **多供应商支持** — OpenAI / Claude / Google Gemini / Vertex AI，兼容 OpenAI 格式的任意 API
|- **模型级联** — 全局默认模型 → 每个助手独立覆盖，灵活切换
|- **消息分支** — 树状对话结构，支持回溯、重新生成、切换分支
|- **流式输出** / **非流式** — 按需配置
|- **推理渲染** — 思考链（`think` 标签）提取与可视化
|- **多模态输入** — 图片理解（Vision）、PDF、DOCX、PPTX 文档自动转文本
|- **Tools/Function Calling** — 统一的 Tool 注册系统，跨供应商兼容
|- **MCP 协议** — 支持 SSE 和 Streamable HTTP 两种传输模式，每个助手独立挂载
|- **结构化输出** — 部分供应商支持 JSON 模式
|
- **对话模板** — Pebble 模板引擎，自动注入时间、日期、模型名等变量
|- **预设消息** — 每个助手可配置预设上下文 Prompt，注入 System Prompt
|- **Prompt 注入** — 模式注入（Mode）和世界观/角色设定（Lorebook）
|- **自定义请求** — 自定义 HTTP 请求头、请求体，适配特殊 API
|- **消息收藏** — 收藏/取消收藏对话消息
|- **对话全文检索** — SQLite FTS 全文搜索
|- **AI 翻译** — 内置翻译界面
|- **智能建议** — 基于对话上下文的快捷回复
|- **SillyTavern 卡片导入** — 导入角色设定
|
|### 🛠️ 本地 Tool 系统（Android 原生能力）
|
|每个助手可独立启用/关闭以下 Tool：
|
|Tool|能力|
|:---:|:---:|
|`eval_javascript`|QuickJS 引擎，ES2020 级别执行 JS 代码|
|`time_info`|当前时间、时区、星期几|
|`clipboard`|读取/写入系统剪贴板|
|`text_to_speech`|TTS 合成语音并播放|
|`get_weather`|OpenWeatherMap 天气查询（按城市或经纬度）|
|`query_perception`|查询历史感知数据（位置轨迹 + 天气历史）|
|`vibrate_device`|手机震动|
|`get_current_location`|高德 GPS 精确定位（含 POI/街道/地址）|
|`take_photo_camera`|调系统摄像头拍照|
|`open_external_app`|打开外部 App / deep-link|
|`search_contacts`|搜索通讯录|
|`make_phone_call`|打开拨号器|
|`amap_link`|高德地图导航连接|
|`list_directory_contents`|列出目录文件列表|
|`get_file_info`|获取文件元信息|
|`call_contact_by_name`|按姓名搜索并拨号|
|
- **技能系统** — 服务端 SKILL.md，AI 对话中可动态加载并执行专业技能
|
|### 🔍 搜索能力
|
|**15 个搜索供应商抽象**，为 AI 提供联网搜索能力：Tavily、Brave、Exa、Bing、Perplexity、Jina、Bocha、Zhipu、Grok、Firecrawl、LinkUp、Metaso、SearXNG 等
|
|每个助手可独立选择搜索服务，提供 `search_web`（搜索）+ `scrape_web`（抓取页面）两个接口。
|
|### 🧠 记忆系统
|
|- **类 ChatGPT 记忆** — AI 自动记录用户偏好、重要信息，跨对话持久化
|- **全局共享记忆 / 助手隔离记忆** — 可配置
|- **近期参考** — 引用最近对话上下文
|- **时间提醒** — 间隔提醒注入
|
|### 🔊 语音能力
|
|**TTS（文字转语音）** — 7 个供应商：
|OpenAI / Gemini / MiniMax / Qwen / Groq / XAI / 系统原生 TTS
|
|完整管线：长文分段 → 合成 → 播放
|
|**STT（语音识别）** — 独立音频录制插件，提供完整的语音输入→对话管线
|
|### 👥 群聊（多角色 AI 讨论）

| 特性 | 说明 |
|:---:|:------|
| **多角色群聊** | 将任意多个 Assistant 拉入同一个对话，每个使用自己的模型/Tools/记忆 |
| **自定义参与者** | 每个角色可选不同模型、不同 System Prompt、不同 Tool 集 |
| **说话顺序** | Sequential（轮流）、Random（随机）、Parallel（并行）三种模式 |
| **@提及** | 在群聊中用 `@角色名` 指定发言人，其他 AI 等待 |
| **自动讨论** | 启动后 AI 角色自动轮流传话，无需人工介入，用户可随时加入或中断 |
| **用户可参与** | 用户可以在群聊中发消息，所有 AI 角色都能看到并回应「用户说: ...」 |
| **实时流式** | 每个角色生成的内容实时流式显示，像真群聊一样逐字出现 |
| **角色元信息** | 每条消息携带 `participantId`，界面区分发言人头像/名称/颜色 |

实现：`GroupChatManager` → `AutoDiscussManager`（自动轮询）→ `GroupChatMessageBuilder`（角色转换/花名册注入）

### 🖥️ 内嵌 Web 服务器
|
|- **Ktor CIO 引擎** — 纯 Kotlin 协程，不依赖 Android WebView
|- **REST API** — 对话 CRUD、设置读写、文件管理、图标素材
|- **JWT 认证** — 密码 → Token，30 天有效
|- **WebDAV 同步** — 压缩/ZIP、增量、双向同步
|- **NSD 局域网发现** — 自动注册 mDNS 服务
|- **SSE 事件流** — 实时数据推送
|
|### 📱 唧唧（主动式个人助手）
|
|RikkaHub 内置主动式 AI 助手——唧唧。它作为 Android 前台服务运行，定时感知用户状态并主动推送：
|
|- **感知采集** — 每 1 分钟定位（高德）、每 1 小时天气（OpenWeatherMap）
|- **熵驱动存储** — 同区域不写、同温度区间不写，不浪费存储
|- **基线学习** — 从对话中归纳用户的生活规律和偏好
|- **偏差检测** — 检测时间异常、天气变化、长期沉默、偏好偏差等
|- **搭话生成** — AI 优先 + 规则兜底双模，通知栏主动推送
|- **感知历史查询** — 对话中可查询过去的位置轨迹和天气变化
|- **感知数据导入导出** — 完整 JSON 序列化，数据可迁移
|
|### 🎨 界面与体验
|
|- **Material You 设计** — 现代化 Android 设计语言
|- **暗色模式** — 全局暗色主题
|- **预设主题** — 春天 / 海洋 / 樱花 / 黑色 / 秋天 多种配色
|- **代码高亮** — 内置 Highlight 引擎
|- **Markdown 渲染** — 支持代码高亮、LaTeX 数学公式、表格、Mermaid 图表
|- **运营商二维码导入导出** — 扫码分享/还原供应商配置
|- **App 分享接收** — 一键将外部内容发送给 AI 处理
|- **硬件按键配置** — 自定义物理按键触发特定操作
|- **崩溃上报** — 内置 CrashHandler
|- **更新检查** — 应用内版本检测
|
|## 🏗️ 模块架构
|
|```
|RikkaHub/
|├── app/          — 主模块（UI、ViewModel、核心逻辑、唧唧）
|├── ai/           — AI 供应商抽象层（OpenAI / Claude / Google）
|├── common/       — 公共工具与扩展
|├── document/     — 文档解析（PDF / DOCX / PPTX）
|├── highlight/    — 代码语法高亮
|├── search/       — 搜索 SDK（15 个供应商抽象）
|├── stt/          — 语音识别
|├── tts/          — 文字转语音（7 个供应商）
|├── web/          — 内嵌 Ktor Web 服务器
|├── web-ui/       — Web 管理界面前端（React）
|├── talkio/       — 桌面端（Tauri）
|└── locale-tui/   — 本地化辅助工具
|```
|
|## ✨ 贡献
|
|本项目使用 [Android Studio](https://developer.android.com/studio) 开发，欢迎提交 PR。
|
|技术栈：Kotlin | Jetpack Compose | Koin | Room | DataStore | Ktor | OkHttp | Coil | Material You
|
|> [!TIP]
|> 你需要在 `app` 文件夹下添加 `google-services.json` 文件才能构建应用。
|>
|> [!IMPORTANT]
|> 以下 PR 将被拒绝：
|> 1. 添加新语言
|> 2. 添加新功能
|> 3. AI 生成的大规模重构和更改
|
|## 💰 捐赠
|
|* [Patreon](https://patreon.com/rikkahub)
|* [爱发电](https://afdian.com/a/reovo)
|
|## ⭐ Star History
|
|[![Star History Chart](https://api.star-history.com/svg?repos=re-ovo/rikkahub&type=Date)](https://star-history.com/#re-ovo/rikkahub&Date)
|
|## 📄 许可证
|
|[License](LICENSE)
