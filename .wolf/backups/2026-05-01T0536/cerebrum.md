# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-04-28

## User Preferences

<!-- How the user likes things done. Code style, tools, patterns, communication. -->

## Key Learnings

- **Project:** rikkahub
- **Description:** <div align="center">
- **Compose Locale:** Use `LocalConfiguration.current` not `Locale.getDefault()` in `@Composable` functions. Wrap in `remember(configuration)` for recomposition on locale change.

## Do-Not-Repeat

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->
- [2026-04-29] In @Composable functions: use `LocalConfiguration.current.locales[0]` not `Locale.getDefault()` — latter is non-observable, breaks recomposition
- [2026-04-29] Getting Activity in Compose: `LocalActivity.current` not `LocalContext.current as? Activity`
- [2026-04-29] Getting string resources in Compose: `stringResource(R.string.xxx)` in composable scope, or capture value pre-lambda. Never `context.getString()` in composable context
- [2026-04-29] New strings.xml entries must be added to all 5 locale files (ru/ko/ja/zh/zh-rTW) or lint fails MissingTranslation

## Decision Log

<!-- Significant technical decisions with rationale. Why X was chosen over Y. -->
