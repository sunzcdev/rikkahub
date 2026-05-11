# Deep Interview Spec: Navigation Menu Redesign

## Metadata
- Interview ID: wechat-redesign-nav-001
- Rounds: 4
- Final Ambiguity Score: ~8%
- Type: brownfield
- Generated: 2026-05-01
- Threshold: 20%
- Status: PASSED

## Goal

Reorganize all app navigation entry points following WeChat-like UX principles. "+" = quick actions. Contacts = hub for contacts, tools, and creation. Settings = all config + stats. ChatPage menu = current chat only. No redundant entries.

## Changes

### 1. "+" Menu (ChatListTab TopBar)

**Remove:**
- 新建群聊 → moved to Contacts tab
- 收藏 → moved to Contacts tab
- 统计数据 → moved to Settings
- AI 翻译 → moved to Contacts tab (as assistant entry)
- AI 绘图 → moved to Contacts tab (as assistant entry)

**Keep:**
- 搜索聊天 → Screen.MessageSearch (global search, single entry point)

**Add:**
- 添加助手 → inline create dialog (新助手 name/avatar/model form)

**Final "+" menu:** 添加助手 | 搜索聊天 | 设置

File: `app/src/main/java/me/rerere/rikkahub/ui/pages/main/ChatListTab.kt`

### 2. Contacts Tab

**New layout (top to bottom):**
1. Search bar (existing, filter assistants)
2. 新建群聊 — entry with group icon, opens CreateGroupChatDialog
3. 收藏 section — FavoritesPage entry
4. AI tools section — 翻译 and 绘图 as clickable entries (navigate to Screen.Translator / Screen.ImageGen)
5. Assistant list (existing)

File: `app/src/main/java/me/rerere/rikkahub/ui/pages/main/ContactsTab.kt`

### 3. ChatPage ⋮ Menu (ChatTopBar)

**Remove:**
- 新聊天 → unnecessary (assistant tap in Contacts creates new chat)
- 搜索聊天 → deduplicated (search is in "+" menu for global, ChatSettings for within-chat)

**Keep:**
- 聊天设置 → opens ChatSettingsBottomSheet

**Final ⋮ menu:** 聊天设置 only

File: `app/src/main/java/me/rerere/rikkahup/ui/pages/chat/ChatTopBar.kt`

### 4. ChatSettingsBottomSheet

No structural changes. Already has:
- Manage participants
- Group-only: speaking order, auto discuss rounds, custom system prompt
- 搜索聊天记录 (search within current conversation) — keep as-is

### 5. Settings Page

**Add:**
- 统计数据 section (moved from "+" menu), navigates to Screen.Stats or inline stats display

File: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt`

### 6. Import/Icons Changes

**ChatListTab.kt:** Remove unused icon imports (InLove, ChartColumn, LanguageCircle, Image02, Copy01). Keep Search01, Settings03, Add01.
**ContactsTab.kt:** Add imports for CreateGroupChatDialog, Copy01/AddTeam icon, InLove, LanguageCircle, Image02.
**ChatTopBar.kt:** Remove MessageAdd01, Search01 imports if no longer used.
