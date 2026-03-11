# ContextExport SDK

A lightweight Kotlin library for building structured AI prompts from your app's data. Define your data model, pick which sections to include, and get a ready-to-paste prompt for ChatGPT, Claude, or any LLM.

Zero dependencies. Works with any Kotlin or Java project.

[![](https://jitpack.io/v/yetibownur/contextexport.svg)](https://jitpack.io/#yetibownur/contextexport)
[![CI](https://github.com/yetibownur/contextexport/actions/workflows/ci.yml/badge.svg)](https://github.com/yetibownur/contextexport/actions/workflows/ci.yml)

## What it does

ContextExport gives you a generic `AppPromptBuilder<TData, TSection>` base class. You subclass it with your app's specific data and sections, and it handles the build loop — iterating enabled sections, calling your renderers, and assembling the final prompt string.

```
AppPromptBuilder<TData, TSection>        <- SDK (this library)
  ├── YourAppPromptBuilder               <- your app
  └── AnotherAppPromptBuilder            <- another app
```

**Proven in production** across two Android apps in completely different domains — a fitness tracker (7 sections) and an RPG campaign manager (11 sections) — sharing the same base class with zero domain-specific code in the SDK.

## Features

- **Section-based prompt building** — Define sections as an enum, toggle them on/off at runtime
- **MarkdownTable DSL** — Fluent `sb.appendTable("Col1", "Col2") { rows(...) }` syntax for clean tables
- **NumberFormat** — Compact formatting with K/M/B suffixes (`1500` → `1.5K lbs`) and comma separators
- **Automatic chunking** — Splits large prompts at `##` boundaries with branded continuation labels
- **PromptResult metadata** — Size in bytes/KB, estimated token count, chunk count, section count
- **JSON output** — Structured JSON with individual sections as keys, for AI API system messages
- **PromptCompressor** — Strips redundant whitespace to save tokens
- **Section item counts** — `countSectionItems()` for UI chip labels like "Recipes (3)"
- **Format versioning** — `formatVersion` property embeds schema version in output
- **Configurable chunk labels** — `appName` property brands multi-part continuation messages
- **Auto-skip empty sections** — `autoSkipEmpty` property skips sections with 0 items, eliminating manual guards
- **Per-section token breakdown** — `SectionStats` with chars, tokens, and percentage for each section
- **Context window presets** — `ContextWindow` with model constants and utilities (`fitsInContext`, `usagePercent`)
- **Smart truncation** — `buildPromptWithBudget()` drops low-priority sections to fit token limits
- **Section priority** — `sectionPriority()` override controls truncation order
- **Footer support** — Closing instructions block after all sections
- **Token estimation** — Fast ~4 chars/token heuristic for UI display
- **Zero dependencies** — Pure Kotlin, no Android/Compose/framework deps
- **95 unit tests** — Full coverage of all components
- **GitHub Actions CI** — Auto-runs build, tests, and sample on push/PR

## Install via JitPack

### Step 1 — Add the JitPack repository

In your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2 — Add the dependency

In your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.yetibownur:contextexport:1.2.0")
}
```

### Step 3 — Subclass and build

```kotlin
import com.garrettmcbride.contextexport.AppPromptBuilder
import com.garrettmcbride.contextexport.NumberFormat
import com.garrettmcbride.contextexport.appendTable

// 1. Define your sections
enum class MySection(val label: String) {
    PROFILE("Profile"),
    HISTORY("History"),
    STATS("Statistics")
}

// 2. Define your data bag
data class MyExportData(
    val userName: String,
    val entries: List<Entry>,
    val totalScore: Int
)
data class Entry(val name: String, val value: Int, val date: String)

// 3. Subclass the builder
class MyPromptBuilder : AppPromptBuilder<MyExportData, MySection>() {

    override val appName = "MyApp"          // Brands chunk labels
    override val formatVersion = 1          // Embeds schema version

    override fun appendHeader(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("# ${data.userName} — Data Export")
        sb.appendLine()
    }

    override fun sectionRenderers() = listOf(
        MySection.PROFILE to ::appendProfile,
        MySection.HISTORY to ::appendHistory,
        MySection.STATS to ::appendStats
    )

    override fun appendFooter(sb: StringBuilder, data: MyExportData) {
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("End of export. You now have the user's complete data.")
    }

    // Section item counts for UI chip labels: "History (5)"
    override fun sectionItemCount(section: MySection, data: MyExportData): Int = when (section) {
        MySection.PROFILE -> 1
        MySection.HISTORY -> data.entries.size
        MySection.STATS -> data.totalScore
    }

    private fun appendProfile(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("## Profile")
        sb.appendLine("- Name: ${data.userName}")
        sb.appendLine()
    }

    private fun appendHistory(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("## History")
        // MarkdownTable DSL — no hand-written pipes
        sb.appendTable("Entry", "Value", "Date") {
            rows(data.entries) { e ->
                arrayOf(e.name, NumberFormat.withCommas(e.value), e.date)
            }
        }
        sb.appendLine()
    }

    private fun appendStats(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("## Statistics")
        sb.appendLine("- Total Score: ${NumberFormat.compact(data.totalScore)}")
        sb.appendLine()
    }
}
```

## Usage

### Basic — get the prompt text

```kotlin
val builder = MyPromptBuilder()
val prompt = builder.buildPrompt(
    data = myData,
    enabledSections = setOf(MySection.PROFILE, MySection.STATS)
)
// prompt is a String ready to paste into any AI chat
```

### Recommended — get PromptResult with metadata

```kotlin
val result = builder.buildPromptResult(
    data = myData,
    enabledSections = MySection.entries.toSet()
)

result.fullText          // Complete prompt string
result.chunks            // Pre-split parts for multi-message chats
result.chunkCount        // Number of chunks (1 if it fits in one message)
result.isMultiPart       // true if split into multiple chunks
result.sizeBytes         // UTF-8 byte size
result.sizeKb            // Size in kilobytes
result.estimatedTokens   // ~4 chars/token estimate
result.enabledSectionCount // How many sections were included
```

### MarkdownTable DSL

Eliminates hand-written pipe syntax. The DSL handles column alignment and separator rows automatically:

```kotlin
sb.appendTable("Name", "Score", "Date") {
    row("Alice", "1,500", "Mar 10")
    row("Bob", "2,300", "Mar 11")
}

// Or map from a list:
sb.appendTable("Name", "Score", "Date") {
    rows(players) { p ->
        arrayOf(p.name, NumberFormat.withCommas(p.score), p.date)
    }
}
```

Produces:
```
| Name | Score | Date |
|------|-------|------|
| Alice | 1,500 | Mar 10 |
| Bob | 2,300 | Mar 11 |
```

### NumberFormat

Compact formatting for large numbers with optional units:

```kotlin
NumberFormat.compact(750)              // "750"
NumberFormat.compact(1500, "lbs")      // "1.5K lbs"
NumberFormat.compact(2_300_000)        // "2.3M"
NumberFormat.withCommas(1_234_567)     // "1,234,567"
```

### Auto-skip empty sections

Set `autoSkipEmpty = true` to automatically skip sections where `sectionItemCount()` returns 0. No more manual `if (data.items.isEmpty()) return` guards in every renderer:

```kotlin
class MyPromptBuilder : AppPromptBuilder<MyData, MySection>() {
    override val autoSkipEmpty = true  // SDK skips empty sections automatically

    override fun sectionItemCount(section: MySection, data: MyData): Int = when (section) {
        MySection.PROFILE -> 1
        MySection.HISTORY -> data.entries.size  // 0 entries → section skipped
        MySection.STATS -> data.totalScore
    }

    // No need for "if (data.entries.isEmpty()) return" in appendHistory!
}
```

### Per-section token breakdown

`PromptResult.sectionBreakdown` gives you per-section stats:

```kotlin
val result = builder.buildPromptResult(data, MySection.entries.toSet())

for ((name, stats) in result.sectionBreakdown) {
    println("$name: ${stats.chars} chars, ${stats.tokens} tokens, ${stats.percentage}%")
}
// HISTORY: 1,200 chars, 300 tokens, 45.2%
// PROFILE: 400 chars, 100 tokens, 15.1%
```

### Context window presets

Check if your prompt fits a model's context window:

```kotlin
import com.garrettmcbride.contextexport.ContextWindow

val fits = ContextWindow.fitsInContext(result.estimatedTokens, ContextWindow.CLAUDE_3_5)
val usage = ContextWindow.usagePercent(result.estimatedTokens, ContextWindow.CLAUDE_3_5)
// "Uses 12.3% of Claude 3.5's context"

val budget = ContextWindow.effectiveInput(ContextWindow.GPT_4O)  // 108,800 tokens
val remaining = ContextWindow.remainingTokens(result.estimatedTokens, ContextWindow.GPT_4O)
```

### Smart truncation with token budgets

Auto-fit prompts to a model's context by dropping low-priority sections:

```kotlin
val budget = ContextWindow.effectiveInput(ContextWindow.CLAUDE_3_5)
val result = builder.buildPromptWithBudget(data, allSections, maxTokens = budget)

result.withinBudget       // true — it fits
result.droppedSections    // {LOOT_TABLES, SESSIONS} — what was cut
result.includedSections   // remaining sections
result.promptResult       // the trimmed PromptResult
```

Override `sectionPriority()` to control which sections are dropped first:

```kotlin
override fun sectionPriority(section: MySection): Int = when (section) {
    MySection.PROFILE -> 100    // highest priority, kept last
    MySection.HISTORY -> 50
    MySection.STATS -> 10       // lowest priority, dropped first
}
```

### Section item counts

Override `sectionItemCount()` once in your builder, then call `countSectionItems()` from your ViewModel to drive UI chip labels:

```kotlin
// In your ViewModel:
val counts = promptBuilder.countSectionItems(exportData)
// counts = {PROFILE=1, HISTORY=5, STATS=42}

// In Compose:
FilterChip(
    label = { Text("${section.label} (${counts[section] ?: 0})") },
    ...
)
```

### Format versioning

Set `formatVersion` in your builder to embed a schema version comment at the top of markdown output and in JSON metadata:

```kotlin
override val formatVersion = 2
// Markdown output starts with: <!-- format_version: 2 -->
// JSON includes: "formatVersion": 2
```

### Branded chunk labels

Set `appName` to brand multi-part continuation messages:

```
[Part 2 of 3] — Continuation of MyApp data export.
```

### PromptCompressor

Strips redundant whitespace to save tokens:

```kotlin
import com.garrettmcbride.contextexport.PromptCompressor

val compressed = PromptCompressor.compress(rawPrompt)
val (charsSaved, tokensSaved) = PromptCompressor.savings(rawPrompt)
```

### JSON output — for AI APIs

```kotlin
val json = builder.buildJsonPrompt(
    data = myData,
    enabledSections = MySection.entries.toSet(),
    totalSectionCount = MySection.entries.size
)
```

Produces:
```json
{
  "header": "# Alice — Data Export\n\n...",
  "sections": {
    "PROFILE": "## Profile\n- Name: Alice",
    "HISTORY": "## History\n| Entry | Value | Date |...",
    "STATS": "## Statistics\n- Total Score: 42"
  },
  "footer": "---\nEnd of export...",
  "metadata": {
    "formatVersion": 1,
    "enabledSections": 3,
    "totalSections": 3,
    "estimatedTokens": 150,
    "sizeBytes": 412
  }
}
```

### Toggling sections at runtime

The `enabledSections` parameter is a `Set<TSection>`. Pass all entries for a full export, or let users toggle them in your UI:

```kotlin
// All sections
val full = builder.buildPromptResult(data, MySection.entries.toSet())

// User-selected subset
val partial = builder.buildPromptResult(data, setOf(MySection.PROFILE, MySection.STATS))
```

This pairs well with Compose `FilterChip` rows or Settings toggle lists — the user picks what to include and the prompt rebuilds instantly.

### Chunking large prompts

When prompts exceed what fits in a single AI message, `buildPromptResult` automatically splits them at `##` section boundaries (~15K chars per chunk, ~4K tokens). Each chunk gets branded continuation labels:

```
[Part 1 of 3] — Send the next part to continue.
[Part 2 of 3] — Continuation of MyApp data export.
[Part 3 of 3] — Final part. [End of export]
```

You can also use `PromptChunker` directly:

```kotlin
import com.garrettmcbride.contextexport.PromptChunker

val chunks = PromptChunker.chunk(longPrompt)                    // default 15K chars
val chunks = PromptChunker.chunk(longPrompt, chunkSize = 8000)  // custom size
val chunks = PromptChunker.chunk(longPrompt, appName = "MyApp") // branded labels
```

### Token estimation

```kotlin
import com.garrettmcbride.contextexport.TokenEstimator

val tokens = TokenEstimator.estimate(promptText)  // ~4 chars/token
val tokens = TokenEstimator.estimate(promptText, charsPerToken = 3.5)  // custom ratio
```

## Running the sample

The repo includes a runnable `sample/` module:

```bash
./gradlew :sample:run
```

It builds a recipe app prompt showing markdown output, table DSL, number formatting, compression stats, partial export, and JSON format.

## Local development (includeBuild)

If you're developing the SDK alongside your app, use Gradle composite builds instead of JitPack:

In your `settings.gradle.kts`:

```kotlin
includeBuild("../contextexport")
```

In your `app/build.gradle.kts`:

```kotlin
implementation("com.garrettmcbride.contextexport:contextexport:1.1.0")
```

When you publish to JitPack later, just remove the `includeBuild` line and switch to the JitPack coordinates. No code changes needed.

## API Reference

### `AppPromptBuilder<TData, TSection>`

| Method / Property | Description |
|-------------------|-------------|
| `buildPrompt(data, enabledSections)` | Returns the prompt as a `String`. |
| `buildPromptResult(data, enabledSections, chunkSize?)` | Returns a `PromptResult` with text, chunks, and metadata. |
| `buildJsonPrompt(data, enabledSections, totalSectionCount?)` | Returns structured JSON with individual sections and metadata. |
| `countSectionItems(data)` | Returns `Map<TSection, Int>` of item counts for all sections. |
| `buildPromptWithBudget(data, enabledSections, maxTokens, chunkSize?)` | Returns `BudgetResult` — drops low-priority sections to fit token budget. |
| `appendHeader(sb, data)` | Override — write the prompt header (title, AI instructions). |
| `sectionRenderers()` | Override — return `Section to renderer` pairs. |
| `appendFooter(sb, data)` | Override — write closing instructions after all sections. |
| `sectionItemCount(section, data)` | Override — return item count for a section (default 0). |
| `sectionPriority(section)` | Override — priority for budget truncation (higher = kept longer). |
| `appName` | Override — brand name for chunk continuation labels. |
| `formatVersion` | Override — schema version embedded in output (0 = disabled). |
| `autoSkipEmpty` | Override — skip sections where `sectionItemCount()` returns 0 (default false). |

### `PromptResult`

| Property | Type | Description |
|----------|------|-------------|
| `fullText` | `String` | Complete prompt string. |
| `chunks` | `List<String>` | Prompt split into sendable parts. |
| `chunkCount` | `Int` | Number of chunks. |
| `isMultiPart` | `Boolean` | Whether splitting was needed. |
| `sizeBytes` | `Int` | UTF-8 byte size. |
| `sizeKb` | `Double` | Size in kilobytes. |
| `estimatedTokens` | `Int` | Approximate token count. |
| `enabledSectionCount` | `Int` | Sections that were included. |
| `sectionBreakdown` | `Map<String, SectionStats>` | Per-section chars, tokens, and percentage. |

### `MarkdownTable`

| Method | Description |
|--------|-------------|
| `sb.appendTable(vararg headers, block)` | Extension function — builds and appends a table. |
| `row(vararg values)` | Add a single row. |
| `rows(items, mapper)` | Map a list to rows. |

### `NumberFormat`

| Method | Description |
|--------|-------------|
| `compact(value, unit?, decimals?)` | Compact display: `1500` → `1.5K`, with optional unit suffix. |
| `withCommas(value, unit?)` | Comma-separated: `1234567` → `1,234,567`. |

### `PromptCompressor`

| Method | Description |
|--------|-------------|
| `compress(prompt)` | Collapses 3+ blank lines, trims trailing whitespace. |
| `savings(prompt)` | Returns `Pair<Int, Int>` of (chars saved, tokens saved). |

### `PromptChunker`

| Method | Description |
|--------|-------------|
| `chunk(prompt, chunkSize?, appName?)` | Splits at `##` boundaries with branded continuation labels. Default 15K chars. |

### `TokenEstimator`

| Method | Description |
|--------|-------------|
| `estimate(text, charsPerToken?)` | Estimates token count. Default 4.0 chars/token. |

### `SectionStats`

| Property | Type | Description |
|----------|------|-------------|
| `chars` | `Int` | Character count for this section. |
| `tokens` | `Int` | Estimated token count. |
| `percentage` | `Double` | Share of total prompt (0.0–100.0). |

### `BudgetResult<TSection>`

| Property | Type | Description |
|----------|------|-------------|
| `promptResult` | `PromptResult` | The trimmed prompt result. |
| `droppedSections` | `Set<TSection>` | Sections removed to fit budget. |
| `includedSections` | `Set<TSection>` | Sections kept in the prompt. |
| `budgetTokens` | `Int` | The target token budget. |
| `withinBudget` | `Boolean` | Whether the final prompt fits. |

### `ContextWindow`

| Member | Description |
|--------|-------------|
| `GPT_4_TURBO`, `GPT_4O`, `GPT_4O_MINI` | 128K context presets. |
| `CLAUDE_3`, `CLAUDE_3_5`, `CLAUDE_4` | 200K context presets. |
| `GEMINI_1_5_PRO`, `GEMINI_1_5_FLASH`, `GEMINI_2_FLASH` | 1M context presets. |
| `effectiveInput(contextSize, reserveRatio?)` | Usable input tokens after reserving 15% for output. |
| `fitsInContext(tokens, contextSize, reserveRatio?)` | Whether tokens fit within the effective budget. |
| `usagePercent(tokens, contextSize, reserveRatio?)` | Usage as 0.0–100.0+ percentage. |
| `remainingTokens(tokens, contextSize, reserveRatio?)` | Remaining budget (negative if over). |

### Type Parameters

| Parameter | Constraint | Description |
|-----------|------------|-------------|
| `TData` | None | Your app's export data bag — data class, POJO, anything. |
| `TSection` | `Enum<TSection>` | Your section enum. Each entry maps to a renderer and a UI toggle. |

## Requirements

- Kotlin 1.9+ or Java 21+
- No Android, Compose, or framework dependencies

## Running tests

```bash
./gradlew test
```

95 tests covering: builder (19), chunker (8), token estimator (7), PromptResult (4), MarkdownTable (7), NumberFormat (11), PromptCompressor (9), AutoSkipEmpty (5), SectionStats (7), ContextWindow (12), BudgetResult (7).

## Generating docs

```bash
./gradlew dokkaHtml
# Output: build/dokka/html/index.html
```

## License

MIT
