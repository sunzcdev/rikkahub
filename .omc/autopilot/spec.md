# Spec: WeChat Redesign — UI Cleanup & Contacts Tab Fixes

## Overview

Clean up duplicate menu items and fix Contacts tab behavior per user feedback.

## Changes

### 1. ChatListTab "+" Dropdown Menu Cleanup

File: `app/src/main/java/me/rerere/rikkahub/ui/pages/main/ChatListTab.kt`

**Remove:**
- "助手设置" → `Screen.Assistant` — duplicates Contacts tab assistant list
- "聊天历史" → `Screen.History` — main page already shows conversation list

**Add:**
- "设置" → `Screen.Setting` — moved from ContactsTab bottom

Updated menu order: 新建群聊 | 设置 | divider | 搜索聊天 | 收藏 | 统计数据 | divider | AI 翻译 | AI 绘图

### 2. ContactsTab Cleanup

File: `app/src/main/java/me/rerere/rikkahub/ui/pages/main/ContactsTab.kt`

**2.1 Remove "新的朋友"** — no-op entry, concept doesn't apply here
**2.2 Remove "群聊"** — no-op entry, group chat creation already in "+" menu on main page
**2.3 Assistant click opens single chat** — change onClick from `Screen.AssistantDetail` to:
   - Inject `SettingsStore` via `koinInject()`
   - Call `settingsStore.updateAssistant(assistant.id)` to switch current assistant
   - Navigate to new chat via `navigateToChatPage(navController)`
**2.4 Remove Settings row at bottom** — moved to "+" menu on main page

### 3. Import Changes

**ContactsTab.kt:**
- Remove: `Screen`, `Settings03`, `HugeIcons.Settings03`
- Add: `SettingsStore`, `kotlinx.coroutines.launch`, `rememberCoroutineScope`, `navigateToChatPage`

**ChatListTab.kt:**
- Add: `Settings03` icon import
