# Deep Interview Spec: WeChat-Style UI Redesign

## Metadata
- Interview Rounds: 11
- Final Ambiguity: ~8%
- Type: brownfield
- Project: RikkaHub (Android Jetpack Compose)
- Status: PASSED

## Clarity Breakdown
| Dimension | Score | Weight | Weighted |
|-----------|-------|--------|----------|
| Goal Clarity | 0.95 | 0.35 | 0.3325 |
| Constraint Clarity | 0.90 | 0.25 | 0.2250 |
| Success Criteria | 0.92 | 0.25 | 0.2300 |
| Context Clarity | 0.90 | 0.15 | 0.1350 |
| **Total Clarity** | | | **0.9225** |
| **Ambiguity** | | | **7.75%** |

## Goal
Transform RikkaHub's chat-related UI to match WeChat's interaction pattern while keeping the app's AI chat functionality intact. The redesign covers: main page (conversation list as home), contacts page (assistants as contacts), chat interface (simplified top bar), and navigation structure (bottom tabs replacing side drawer).

## Constraints
1. **Chat-only scope**: Only modify chat-related pages. Settings, backup, webview, translator, image gen, stats, extensions, debug, etc. remain unchanged in structure.
2. **Keep AI functionality**: Model picker, web search toggle, reasoning level, voice input, MCP picker etc. must remain accessible (reorganized, not removed).
3. **Existing data migration**: All existing conversations and assistant configs must remain intact.
4. **Bottom navigation**: Only 2 tabs — 聊天 (Chats) and 通讯录 (Contacts). No 发现 or 我 tabs.
5. **Single Activity**: Must continue using the existing navigation3 framework with Screen sealed class.
6. **No drawer**: The side navigation drawer must be completely removed.
7. **Responsive design**: Must still work on both phone and large screen (tablet/landscape) modes.

## Non-Goals
- Changing the settings pages layout
- Modifying the backup/restore system
- Altering the AI provider integration
- Redesigning non-chat pages (translator, image gen, stats, etc.)
- Changing the message bubble design (stays as-is unless user requests later)
- Adding real contacts/social features (only AI assistants as "contacts")

## Acceptance Criteria
- [ ] Bottom navigation bar with 2 tabs: 聊天, 通讯录
- [ ] Tab switching works with smooth animation
- [ ] Chat tab shows conversation list as main page (with title "RikkaHub" and "+" button)
- [ ] "+" button shows menu with: 新建群聊, 添加助手
- [ ] Tapping a conversation opens the chat page with proper back navigation
- [ ] Contacts tab shows assistant list with:
  - Search bar at top
  - "新的朋友" and "群聊" fixed entries
  - Alphabetical index sidebar
  - "设置" entry at bottom
- [ ] Tapping an assistant in contacts opens a single chat conversation
- [ ] Chat top bar: `← Back | Contact Name (center) | ⋮ More`
- [ ] "⋮" menu contains: 新聊天, 聊天设置, 搜索聊天内容
- [ ] Chat input area: `🎤 Text field 📎` with expandable tool strip (model, search, reasoning)
- [ ] Group chat settings: independent full page (not bottom sheet)
- [ ] Side drawer completely removed
- [ ] All existing chat functionality preserved (voice, TTS, file attachments, etc.)
- [ ] No crashes or regressions in non-chat pages
- [ ] Settings accessible from contacts page bottom entry

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bottom tabs | 2 tabs: 聊天 + 通讯录 | Only modify chat area, minimal disruption |
| Main page | Conversation list (was in drawer) | WeChat-style home screen |
| Single chat start | From contacts tab | Streamlined, tap contact → chat |
| "+" menu | 新建群聊 + 添加助手 | No "新建单聊" since contacts handle that |
| Drawer | Removed entirely | No longer needed with new nav structure |
| Chat top bar | ← Back + Name + ⋮ More | Match WeChat exactly |
| Settings entry | Contacts page footer | Clean way to access settings without drawer |
| Group settings | Full page | Match WeChat style |
| ⋮ Menu items | 新聊天/聊天设置/搜索 | Standard set for chat actions |
| Contacts style | Search + alphabetical index | WeChat-style contacts |
| Chat input | Voice + text + expandable tool strip | Mixed style: WeChat-like but retains AI tools |

## Implementation Scope

### New/Modified Files (estimated)
1. **New**: Bottom nav bar component
2. **Modified**: RouteActivity.kt or main scaffold — add bottom navigation structure
3. **Modified**: ChatPage.kt — strip drawer dependency, simplify TopBar
4. **Modified**: ChatTopBar.kt — rewrite to WeChat style (← back + name + ⋮)
5. **New/Modified**: Conversation list becomes main page for "Chats" tab
6. **Modified**: AssistantPage.kt → Contacts page with index sidebar
7. **Modified**: ChatInput.kt — reorganize to voice + text + expandable tools
8. **Modified**: ChatSettingsBottomSheet.kt → GroupChatSettingsPage.kt (full page)
9. **Modified**: ChatDrawer.kt — remove or gut
10. **Modified**: Navigation/Screen definitions

### Files NOT touched
- Setting*.kt, Backup*.kt, WebView*, Translator*, ImgGen*, Stats*, Debug*, Developer*, Log*, Extensions*, Skills*, Prompt*, QuickMessages*
- AI provider implementations
- Database entities and DAOs
- Repository layer

## Assumptions Exposed & Resolved

| Assumption | Challenge | Resolution |
|------------|-----------|------------|
| "Need full WeChat redesign" | Scope? | Confirmed: chat-only, not all pages |
| "Need bottom nav with 4 tabs" | User choice | Confirmed: only 2 tabs (聊天+通讯录) |
| "Need single chat in + menu" | User preference | Rejected: single chat from contacts only |
| "Drawer needed" | User preference | Removed: completely gone |
| "Need app drawer | User preference | Replaced with bottom nav |
