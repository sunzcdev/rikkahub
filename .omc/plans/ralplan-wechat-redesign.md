# RALPLAN: WeChat-Style UI Redesign

## RALPLAN-DR Summary

### Principles
1. **Minimal disruption** — only modify chat-related files; settings/backup/webview/translator/img-gen/stats/debug untouched
2. **Progressive enhancement** — ship bottom nav + main page first (Phase 1-2), then contacts tab (Phase 3), then chat interface (Phase 4), then polish
3. **Existing data integrity** — all conversations, assistant configs, database schema unchanged
4. **Navigation3 first** — use existing nav3 framework, no new navigation library
5. **Composable extraction over rewrite** — extract and compose from existing code rather than deleting+rewriting
6. **No feature loss** — every feature currently in the drawer must be explicitly rehomed before drawer deletion
7. **Tablet UX preserved** — big screen users must not regress to a single-column experience

### Decision Drivers
1. Bottom nav structure needs a new root screen (Screen.Main) since current initial route is directly Screen.Chat
2. Conversation list currently lives ONLY in ChatDrawer — must extract to standalone page for "Chats" tab
3. Drawer contains ~8 distinct features besides conversation list — each needs explicit migration target
4. `navigateToChatPage` uses `clearAndNavigate` which destroys the back stack — incompatible with MainScreen host
5. Big screen uses PermanentNavigationDrawer — removing it without replacement is a regression

### Viable Options

| Option | Approach | Pros | Cons |
|--------|----------|------|------|
| **A: MainScreen host (phone)** | New Screen.Main as initial route, bottom nav inside, push Chat on top | Clean MVVM, works with nav3 back stack | Big screen loses side panel |
| **A2: MainScreen + keep PermanentDrawer on tablet** | Phone: bottom nav. Tablet: permanent drawer with ChatListTab | No tablet regression | Two conversation list renderings |
| **B: Bottom nav in RouteActivity** | Wrap NavDisplay in bottom nav, control visibility per screen | Screen-level control, no new Screen needed | Complex visibility logic, couples routing |
| **C: Split pane (tablet)** | MainScreen for phone; split pane (list+chat) for tablet | Best tablet UX | Higher complexity, spec doesn't require split pane |

**Chosen: Option A2** — MainScreen as nav3 host with bottom nav on phone. On big screen, keep PermanentNavigationDrawer but populate it with ConversationListTab (simplified list, no user profile/actions). Bottom nav hidden when Chat is active on phone. Big screen detection remains in ChatPage to toggle permanent drawer.

### Key Architecture Fixes (from Architect Review)
1. **Navigation**: Replace `clearAndNavigate` with `navigate` in `navigateToChatPage`. Audit all 10 call sites. New chats use `navigate` + popUpTo(Main).
2. **ConversationList**: Make `current` parameter nullable. Gate auto-scroll LaunchedEffect behind null check.
3. **Drawer features**: Every drawer feature explicitly rehomed before deletion.
4. **ChatDrawerVM**: Rename to `ConversationListVM` to match its role.
5. **Tab state**: Use `rememberSaveable` for tab selection to survive config changes.
6. **Group chat settings**: Keep as bottom sheet (Phase 5 deferred).
7. **Bottom nav visibility**: MainScreen detects when it's the top entry via NavDisplay back stack observation — bottom bar visible only on MainScreen, hidden when Chat is pushed on top.

---

## Implementation Plan (7 Phases)

### Phase 0: Pre-requisite Refactoring

**Files:**
- **MODIFY** `ui/pages/chat/ConversationList.kt` — Make `current: Conversation?`, gate auto-scroll
- **MODIFY** `utils/ChatUtil.kt` — Change `navigateToChatPage` from `clearAndNavigate` to `navigate` + popUpTo
- **MODIFY** `ui/pages/chat/ChatDrawerVM.kt` — Rename to `ConversationListVM`
- **MODIFY** `di/ViewModelModule.kt` — Update Koin registration
- **AUDIT** all call sites of `navigateToChatPage` (3 in ChatDrawer.kt, 3 in ChatPage.kt, 1 each in FavoritePage.kt, ShareHandlerPage.kt, HistoryPage.kt, SearchPage.kt — 10 total across 6 files)

**ConversationList.kt changes:**
```kotlin
fun ColumnScope.ConversationList(
    current: Conversation?,  // nullable — allows use without a "current" selection
    ...
) {
    val hasScrolledToCurrent by remember(current?.id) { mutableStateOf(false) }
    
    LaunchedEffect(current?.id, conversations.itemCount, hasScrolledToCurrent) {
        if (current == null || hasScrolledToCurrent) return@LaunchedEffect
        // ... existing scroll logic, gated on non-null
    }
    
    // Selection comparison:
    is ConversationListItem.Item -> {
        ConversationItem(
            selected = item.conversation.id == current?.id,
            ...
        )
    }
}
```

**ChatUtil.kt changes:**
```kotlin
fun navigateToChatPage(navigator: Navigator, chatId: Uuid = Uuid.random()) {
    navigator.navigate(
        Screen.Chat(chatId.toString()),
        options = navigateOptions {
            popUpTo(Screen.Main)  // keep Main in stack
            launchSingleTop = true
        }
    )
}

// For "new chat from scratch" where we want to clear the current chat:
fun navigateToNewChatPage(navigator: Navigator) {
    navigator.navigate(
        Screen.Chat(Uuid.random().toString()),
        options = navigateOptions {
            launchSingleTop = true
        }
    )
}
```

---

### Phase 1: Bottom Navigation Foundation

**Files:**
- **NEW** `ui/pages/main/MainScreen.kt` — Bottom nav host with 2 tabs
- **MODIFY** `Screen` sealed class in RouteActivity.kt — Add `Screen.Main`
- **MODIFY** `RouteActivity.kt` — Change startScreen to `Screen.Main`, add `entry<Screen.Main>`

**Details:**

1. Add to Screen sealed interface:
```kotlin
@Serializable
data object Main : Screen
```

2. `MainScreen.kt`:
```kotlin
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(HugeIcons.Chatting01, null) },
                    label = { Text("聊天") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(HugeIcons.UserGroup, null) },
                    label = { Text("通讯录") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ChatListTab(modifier = Modifier.padding(padding))
            1 -> ContactsTab(modifier = Modifier.padding(padding))
        }
    }
}
```

3. In RouteActivity, change startScreen:
```kotlin
val startScreen = Screen.Main
```

4. Add entry:
```kotlin
entry<Screen.Main> {
    MainScreen()
}
```

5. Handle `onNewIntent` for external navigation intents.

---

### Phase 2: Chat List Tab (Extract from Drawer)

**Files:**
- **NEW** `ui/pages/main/ChatListTab.kt` — Conversation list as main page
- **MODIFY** `ui/pages/chat/ChatDrawer.kt` — Remove conversation list, keep AssistantPicker + bottom actions for now

**Details:**

`ChatListTab.kt` — conversation list with top bar:
```
┌──────────────────────────────┐
│ RikkaHub                  [+ │ ← TopAppBar title + button
├──────────────────────────────┤
│ [ConversationList]           │  ← Reuses existing ConversationList composable
│  - Chat 1                    │
│  - Chat 2                    │
│  - ...                       │
└──────────────────────────────┘
```

Top bar actions:
- "+" button shows dropdown: 新建群聊, 添加助手
  - 新建群聊: Show CreateGroupChatDialog (reuse existing)
  - 添加助手: Navigate to Screen.Assistant

User profile editing and advanced features go into a top bar menu or are deferred to the contacts tab.

Reuses `ConversationListVM` (renamed ChatDrawerVM via DI) for paging data.

---

### Phase 3: Contacts Tab

**Files:**
- **NEW** `ui/pages/main/ContactsTab.kt` — WeChat-style contacts page

**Details:**

```
┌──────────────────────┐
│ 🔍 搜索              │  ← Search bar (filters assistant list)
├──────────────────────┤
│ 👤 新的朋友          │  ← Fixed → navigate to Screen.Assistant (manage assistants)
│ 💬 群聊              │  ← Fixed → navigate to filtered conversation list
├──────────────────────┤
│  A                   │  ← Section header
│  Alice               │  ← Assistant item → tap = single chat
│  Bob                 │
│  C                   │
│  Charlie             │
│  ...                 │
├──────────────────────┤
│ ⚙️ 设置              │  ← Bottom → navigate to Screen.Setting
│ ⭐ 收藏              │  ← Bottom → navigate to Screen.Favorite  
│ 📊 统计数据          │  ← Bottom → navigate to Screen.Stats
└──────────────────────┘
```

Right sidebar: A-Z index for quick scroll. Assistants sorted alphabetically.

Bottom section includes: 设置, 收藏, 统计数据 (rehomes these from the drawer).

---

### Phase 4: Chat Page Restructure

**Files:**
- **MODIFY** `ui/pages/chat/ChatPage.kt` — Remove drawer, add big screen permanent drawer with ChatListTab
- **MODIFY** `ui/pages/chat/ChatTopBar.kt` — Rewrite to WeChat style
- **DELETE** `ui/pages/chat/ChatDrawer.kt` — After all features rehomed

**ChatPage.kt — big screen strategy:**
```kotlin
if (isBigScreen) {
    PermanentNavigationDrawer(
        drawerContent = {
            ChatListTab(
                // Simplified: conversation list only, no top bar
                // Uses same ConversationListVM
            )
        }
    ) {
        ChatPageContent(...)  // No TopBar in content, uses simplified layout
    }
} else {
    // Phone: simple Scaffold, no drawer
    Scaffold(topBar = { WeChatTopBar(...) }, bottomBar = { ChatInput(...) }) {
        ChatList(...)
    }
}
```

On big screen, the permanent drawer shows conversation list (from ChatListTab). The main content is the chat. Bottom nav is hidden when Chat is active (controlled via RouteActivity or a shared state).

On phone, the chat page is a simple Scaffold with WeChat-style top bar + chat input + message list. Back button returns to MainScreen.

**ChatTopBar.kt — WeChat style:**
```
┌─────────────────────────────────┐
│ ←        Contact Name        ⋮ │
└─────────────────────────────────┘
```

- `← Back` → `navController.popBackStack()`
- Title centered: conversation title
- `⋮ More` dropdown:
  - 新聊天 (navigate to new chat)
  - 聊天设置 (open ChatSettingsBottomSheet or assistant settings)
  - 搜索聊天内容 (navigate to Screen.MessageSearch)

**Orphaned feature migration table (from ChatDrawer.kt):**

| Drawer Feature | New Home | Phase |
|----------------|----------|-------|
| User avatar/nickname | ChatListTab first item section | Phase 2 |
| Search (MessageSearch) | ChatListTab top bar search icon | Phase 2 |
| History | ChatListTab "+" menu or top bar | Phase 2 |
| BackupReminderCard | ChatListTab top banner (dismissible, moves from drawer) | Phase 2 |
| UpdateCard | Removed (only shown in drawer on non-PlayStore builds) | Phase 2 |
| AssistantPicker | ChatListTab top bar as dropdown (quick assistant switch) | Phase 2 |
| Favorites entry | ContactsTab bottom section | Phase 3 |
| Stats entry | ContactsTab bottom section | Phase 3 |
| Translator/ImageGen | ChatListTab "+" menu (secondary group below separator) | Phase 2 |
| Settings entry | ContactsTab bottom section | Phase 3 |

---

### Phase 5: Chat Input Reorganization

**Files:**
- **MODIFY** `ui/components/ai/ChatInput.kt` — Reorganize layout

**Details:**

Target input layout matching WeChat mixed style:
```
┌──────────────────────────────────────────┐
│ [🎤]  [Text Field..................] [📎]│
│ [Model] [Search] [Reasoning] [MCP]       │
└──────────────────────────────────────────┘
```

Changes:
- VoiceInputButton to LEFT of text field
- Send button behavior: appears as a right-side attachment when text is non-empty (replacing voice button position contextually)
- 📎 (Add01) to RIGHT of text field — expands file pickers
- Tool strip unchanged (model, search, reasoning, MCP)

---

### Phase 6: Group Chat Settings (Bottom Sheet — keep as-is)

**Decision:** Defer full-page migration. Keep `ChatSettingsBottomSheet` as a bottom sheet. It works correctly with the simplified ChatPage. No Screen.GroupChatSettings added.

---

### Phase 7: Cleanup & Polish

**Files:**
- DELETE `ui/pages/chat/ChatDrawer.kt` — Only after all features verified migrated
- Rename `ChatDrawerVM` → `ConversationListVM` during Phase 0
- Update imports, remove unused code

**Verification checklist:**
1. All navigation paths work: Main → Chat → back → Main
2. Bottom nav tab switching survives rotation
3. Big screen: permanent drawer shows conversation list on left
4. Contacts tab: alphabetical index scroll works
5. Chat top bar: ← back, ⋮ menu items function correctly
6. Existing features: voice, TTS, file attachments, group chat, auto discuss all work
7. Non-chat pages (settings, backup, translator, etc.) completely unaffected

---

## File Change Summary

| File | Action | Phase |
|------|--------|-------|
| `ConversationList.kt` | Make `current` nullable | 0 |
| `ChatUtil.kt` | `clearAndNavigate` → `navigate` + popUpTo | 0 |
| `ChatDrawerVM.kt` → `ConversationListVM.kt` | Rename | 0 |
| `ViewModelModule.kt` | Update Koin registration | 0 |
| `Screen` sealed class | Add `Screen.Main` | 1 |
| `RouteActivity.kt` | Change startScreen, add entry | 1 |
| `MainScreen.kt` (NEW) | Bottom nav host | 1 |
| `ChatListTab.kt` (NEW) | Conversation list as tab | 2 |
| `ContactsTab.kt` (NEW) | Contacts page with index | 3 |
| `ChatPage.kt` | Remove drawer, big screen permanent drawer | 4 |
| `ChatTopBar.kt` | Rewrite to WeChat style | 4 |
| `ChatDrawer.kt` | DELETE (after migration verified) | 4,7 |
| `ChatInput.kt` | Reorganize: 🎤 left, 📎 right | 5 |
| `ChatSettingsBottomSheet.kt` | Keep as-is (defer full page) | 6 |

## Files NOT Modified (per spec constraint)
Setting*.kt, Backup*.kt, WebView*, Translator*, ImgGen*, Stats*, Debug*, Developer*, Log*, Extensions*, Skills*, Prompt*, QuickMessages*, AI provider implementations, database entities/DAOs, repository layer.

## Acceptance Criteria Mapping
1. Bottom nav 2 tabs → Phase 1
2. Tab switching animation → Phase 1
3. Chat tab: conversation list + "RikkaHub" title + "+" button → Phase 2
4. "+" menu: 新建群聊, 添加助手 → Phase 2
5. Tap conversation → ChatPage with back → Phase 0+2+4
6. Contacts tab: search, 新的朋友, 群聊, index sidebar, 设置 → Phase 3
7. Tap assistant → single chat → Phase 3
8. Chat top bar: ← Back | Name | ⋮ More → Phase 4
9. ⋮ menu: 新聊天, 聊天设置, 搜索聊天内容 → Phase 4
10. Chat input: 🎤 text field 📎 + tool strip → Phase 5
11. Group chat settings: full page → DEFERRED (keep bottom sheet)
12. Side drawer removed → Phase 4+7
13. All chat functionality preserved → Phase 4+5+7
14. No regressions in non-chat pages → Phase 7
15. Settings accessible from contacts → Phase 3
