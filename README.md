# ContextExport SDK

A lightweight Kotlin library for building structured AI prompts from your app's data. Define your data model, pick which sections to include, and get a ready-to-paste prompt for ChatGPT, Claude, or any LLM.

Zero Android dependencies. Works with any Kotlin or Java project.

## What it does

ContextExport gives you a generic `AppPromptBuilder<TData, TSection>` base class. You subclass it with your app's specific data and sections, and it handles the build loop — iterating enabled sections, calling your renderers, and assembling the final prompt string.

```
AppPromptBuilder<TData, TSection>        <- SDK (this library)
  ├── YourAppPromptBuilder               <- your app
  └── AnotherAppPromptBuilder            <- another app
```

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
    implementation("com.github.yetibownur:contextexport:1.0.1")
}
```

### Step 3 — Subclass and build

```kotlin
import com.garrettmcbride.contextexport.AppPromptBuilder

// 1. Define your sections
enum class MySection(val label: String) {
    PROFILE("Profile"),
    HISTORY("History"),
    STATS("Statistics")
}

// 2. Define your data bag
data class MyExportData(
    val userName: String,
    val entries: List<String>,
    val totalScore: Int
)

// 3. Subclass the builder
class MyPromptBuilder : AppPromptBuilder<MyExportData, MySection>() {

    override fun appendHeader(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("# ${data.userName} — Data Export")
        sb.appendLine()
    }

    override fun sectionRenderers() = listOf(
        MySection.PROFILE to ::appendProfile,
        MySection.HISTORY to ::appendHistory,
        MySection.STATS to ::appendStats
    )

    private fun appendProfile(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("## Profile")
        sb.appendLine("- Name: ${data.userName}")
        sb.appendLine()
    }

    private fun appendHistory(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("## History")
        for (entry in data.entries) {
            sb.appendLine("- $entry")
        }
        sb.appendLine()
    }

    private fun appendStats(sb: StringBuilder, data: MyExportData) {
        sb.appendLine("## Statistics")
        sb.appendLine("- Total Score: ${data.totalScore}")
        sb.appendLine()
    }
}

// 4. Build the prompt
val builder = MyPromptBuilder()
val prompt = builder.buildPrompt(
    data = myData,
    enabledSections = setOf(MySection.PROFILE, MySection.STATS)
)
```

The user copies the resulting prompt into any AI chat. Sections they toggled off are excluded automatically.

## Local development (includeBuild)

If you're developing the SDK alongside your app, use Gradle composite builds instead of JitPack:

In your `settings.gradle.kts`:

```kotlin
includeBuild("../contextexport")
```

In your `app/build.gradle.kts`:

```kotlin
implementation("com.garrettmcbride.contextexport:contextexport:1.0.0")
```

When you publish to JitPack later, just remove the `includeBuild` line and switch to the JitPack coordinates. No code changes needed.

## API Reference

### `AppPromptBuilder<TData, TSection>`

| Method | Description |
|--------|-------------|
| `buildPrompt(data, enabledSections)` | Builds the full prompt string. Calls `appendHeader`, then iterates `sectionRenderers()` and calls each enabled renderer. |
| `appendHeader(sb, data)` | Override to write the prompt header (title, description, instructions for the AI). |
| `sectionRenderers()` | Override to return a list of `Section to renderer` pairs. Each renderer is a function `(StringBuilder, TData) -> Unit`. |

## License

MIT
