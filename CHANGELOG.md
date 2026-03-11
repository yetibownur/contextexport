# Changelog

All notable changes to ContextExport SDK are documented here.

## [1.3.0] — 2026-03-11

### Added
- **Diff/delta exports** — `snapshot()` + `buildPromptDiff()` detect changed sections via SHA-256 hashing, only re-exporting what changed since the last snapshot
- **Export presets** — `ExportPreset<TSection>` data class with `exportPresets()` override, `getPreset()`, and `buildFromPreset()` for named section configurations with optional target model
- **Prompt cache optimization** — `cacheOptimized` property + `sectionVolatility()` override sorts sections by `Volatility` (STABLE → MODERATE → VOLATILE) to maximize AI API prompt caching
- **Section groups** — `sectionGroups()` override with `sectionsFromGroups()` and `availableGroups()` for batch enable/disable of related sections
- **Structured message output** — `buildStructuredPrompt()` returns `List<PromptMessage>` with `MessageRole.SYSTEM`/`USER` tags, mapping directly to AI API message arrays
- **Render interceptors** — `SectionInterceptor` fun interface with `sectionInterceptors()` override for post-processing section output (compression, redaction, formatting)
- **Pluggable tokenizers** — `Tokenizer` fun interface with `tokenizer` property override to swap in real tokenizers (tiktoken, etc.) instead of the default ~4 chars/token heuristic

## [1.2.0] — 2026-03-11

### Added
- **Conditional section rendering** — `autoSkipEmpty` property auto-skips sections where `sectionItemCount()` returns 0, eliminating manual `if (isEmpty) return` guards
- **Per-section token breakdown** — `PromptResult.sectionBreakdown` provides `SectionStats` (chars, tokens, percentage) for each enabled section
- **Context window presets** — `ContextWindow` object with model constants (GPT-4o, Claude 3/3.5/4, Gemini) and utilities (`effectiveInput`, `fitsInContext`, `usagePercent`, `remainingTokens`)
- **Smart truncation** — `buildPromptWithBudget()` drops lowest-priority sections to fit a token budget, returns `BudgetResult` with drop info
- **Section priority** — `sectionPriority()` override controls which sections are dropped first during budget truncation

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
