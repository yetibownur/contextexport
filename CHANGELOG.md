# Changelog

All notable changes to ContextExport SDK are documented here.

## [1.1.0] — 2026-03-11

### Added
- **MarkdownTable** — Fluent DSL for building markdown tables (`sb.appendTable(...)`)
- **NumberFormat** — Compact number formatting with K/M/B suffixes and comma separators
- **PromptCompressor** — Strips redundant whitespace to save tokens
- **Section item counts** — `countSectionItems()` + `sectionItemCount()` override for UI chip labels
- **Format versioning** — `formatVersion` property prepends `<!-- format_version: N -->` to prompts
- **Configurable chunk labels** — `appName` property brands multi-part continuation messages
- **GitHub Actions CI** — Auto-runs build, tests, and sample on push/PR

## [1.0.1] — 2025-03-10

### Added
- **PromptChunker** — Splits large prompts at `##` section boundaries (~15K chars per chunk)
- **PromptResult** — Data class with `fullText`, `chunks`, `sizeBytes`, `sizeKb`, `estimatedTokens`
- **TokenEstimator** — Fast ~4 chars/token heuristic
- **JSON output** — `buildJsonPrompt()` produces structured JSON with individual sections
- **Footer support** — `appendFooter()` override for closing instructions
- **buildPromptResult()** — Recommended entry point returning `PromptResult` with metadata
- **Unit tests** — 32 tests covering builder, chunker, token estimator, and result metadata
- **Dokka plugin** — API docs generation via `./gradlew dokkaHtml`
- **Sample module** — Runnable recipe app demo (`./gradlew :sample:run`)

### Fixed
- Gradle wrapper added for JitPack compatibility

## [1.0.0] — 2025-03-08

### Added
- Initial release
- `AppPromptBuilder<TData, TSection>` base class
- `appendHeader()` and `sectionRenderers()` overrides
- `buildPrompt(data, enabledSections)` entry point
- JitPack publishing via `maven-publish` plugin
