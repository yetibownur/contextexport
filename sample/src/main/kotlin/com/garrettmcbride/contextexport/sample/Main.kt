package com.garrettmcbride.contextexport.sample

import com.garrettmcbride.contextexport.AppPromptBuilder
import com.garrettmcbride.contextexport.NumberFormat
import com.garrettmcbride.contextexport.PromptCompressor
import com.garrettmcbride.contextexport.appendTable

// ── 1. Define your sections ────────────────────────────────────────

enum class RecipeSection(val label: String) {
    PROFILE("Chef Profile"),
    RECIPES("Recipes"),
    FAVORITES("Favorites"),
    STATS("Cooking Stats")
}

// ── 2. Define your data bag ────────────────────────────────────────

data class RecipeExportData(
    val chefName: String,
    val cuisine: String,
    val recipes: List<Recipe>,
    val favorites: List<String>,
    val totalCookCount: Int
)

data class Recipe(
    val name: String,
    val cuisine: String,
    val ingredients: List<String>,
    val cookTimeMinutes: Int
)

// ── 3. Subclass the builder ────────────────────────────────────────

class RecipePromptBuilder : AppPromptBuilder<RecipeExportData, RecipeSection>() {

    // Branded chunk labels: "[Part 2 of 3] — Continuation of RecipeBook data export."
    override val appName = "RecipeBook"

    // Format versioning for schema tracking
    override val formatVersion = 1

    override fun appendHeader(sb: StringBuilder, data: RecipeExportData) {
        sb.appendLine("# ${data.chefName}'s Recipe Collection")
        sb.appendLine()
        sb.appendLine("Use this data to suggest new recipes, meal plans, or cooking tips based on the chef's preferences and history.")
        sb.appendLine()
    }

    override fun sectionRenderers() = listOf(
        RecipeSection.PROFILE to ::appendProfile,
        RecipeSection.RECIPES to ::appendRecipes,
        RecipeSection.FAVORITES to ::appendFavorites,
        RecipeSection.STATS to ::appendStats
    )

    override fun appendFooter(sb: StringBuilder, data: RecipeExportData) {
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("End of recipe export. You now have the chef's complete cooking profile.")
    }

    // Section item counts for UI chip labels: "Recipes (3)"
    override fun sectionItemCount(section: RecipeSection, data: RecipeExportData): Int = when (section) {
        RecipeSection.PROFILE -> 1
        RecipeSection.RECIPES -> data.recipes.size
        RecipeSection.FAVORITES -> data.favorites.size
        RecipeSection.STATS -> data.totalCookCount
    }

    private fun appendProfile(sb: StringBuilder, data: RecipeExportData) {
        sb.appendLine("## Chef Profile")
        sb.appendLine("- Name: ${data.chefName}")
        sb.appendLine("- Preferred Cuisine: ${data.cuisine}")
        sb.appendLine()
    }

    private fun appendRecipes(sb: StringBuilder, data: RecipeExportData) {
        if (data.recipes.isEmpty()) return
        sb.appendLine("## Recipes")
        sb.appendLine()

        // Using the MarkdownTable DSL instead of hand-written pipes
        sb.appendTable("Recipe", "Cuisine", "Cook Time", "Ingredients") {
            rows(data.recipes) { r ->
                arrayOf(r.name, r.cuisine, "${r.cookTimeMinutes} min", r.ingredients.joinToString(", "))
            }
        }
        sb.appendLine()
    }

    private fun appendFavorites(sb: StringBuilder, data: RecipeExportData) {
        if (data.favorites.isEmpty()) return
        sb.appendLine("## Favorite Ingredients")
        for (fav in data.favorites) {
            sb.appendLine("- $fav")
        }
        sb.appendLine()
    }

    private fun appendStats(sb: StringBuilder, data: RecipeExportData) {
        sb.appendLine("## Cooking Stats")
        // Using NumberFormat for compact display
        sb.appendLine("- Total dishes cooked: ${NumberFormat.withCommas(data.totalCookCount)}")
        sb.appendLine("- Recipes saved: ${data.recipes.size}")
        sb.appendLine("- Avg cook time: ${if (data.recipes.isNotEmpty()) data.recipes.map { it.cookTimeMinutes }.average().toInt() else 0} min")
        sb.appendLine()
    }
}

// ── 4. Run it ──────────────────────────────────────────────────────

fun main() {
    val data = RecipeExportData(
        chefName = "Alice",
        cuisine = "Italian",
        recipes = listOf(
            Recipe("Carbonara", "Italian", listOf("pasta", "eggs", "guanciale", "pecorino"), 25),
            Recipe("Margherita Pizza", "Italian", listOf("dough", "tomato sauce", "mozzarella", "basil"), 15),
            Recipe("Pad Thai", "Thai", listOf("rice noodles", "shrimp", "peanuts", "lime"), 20)
        ),
        favorites = listOf("garlic", "olive oil", "basil", "parmesan"),
        totalCookCount = 1_542
    )

    val builder = RecipePromptBuilder()

    // ── Markdown output ──
    println("=== MARKDOWN PROMPT ===")
    println()
    val result = builder.buildPromptResult(data, RecipeSection.entries.toSet())
    println(result.fullText)
    println()
    println("--- Metadata ---")
    println("Size: ${"%.1f".format(result.sizeKb)} KB")
    println("Estimated tokens: ${result.estimatedTokens}")
    println("Chunks: ${result.chunkCount}")
    println("Sections enabled: ${result.enabledSectionCount}")
    println()

    // ── Section counts ──
    println("--- Section Counts ---")
    val counts = builder.countSectionItems(data)
    for ((section, count) in counts) {
        println("  ${section.label}: $count")
    }
    println()

    // ── Compression stats ──
    val (charsSaved, tokensSaved) = PromptCompressor.savings(result.fullText)
    println("--- Compression ---")
    println("Characters saved: $charsSaved")
    println("Tokens saved: ~$tokensSaved")
    println()

    // ── Partial export (only Profile + Stats) ──
    println("=== PARTIAL EXPORT (Profile + Stats only) ===")
    println()
    val partial = builder.buildPrompt(data, setOf(RecipeSection.PROFILE, RecipeSection.STATS))
    println(partial)
    println()

    // ── Number formatting showcase ──
    println("=== NUMBER FORMAT EXAMPLES ===")
    println("  compact(750)          = ${NumberFormat.compact(750)}")
    println("  compact(1500, 'lbs')  = ${NumberFormat.compact(1500, "lbs")}")
    println("  compact(2300000)      = ${NumberFormat.compact(2_300_000)}")
    println("  withCommas(1234567)   = ${NumberFormat.withCommas(1_234_567)}")
    println()

    // ── JSON output ──
    println("=== JSON PROMPT ===")
    println()
    val json = builder.buildJsonPrompt(
        data = data,
        enabledSections = RecipeSection.entries.toSet(),
        totalSectionCount = RecipeSection.entries.size
    )
    println(json)
}
