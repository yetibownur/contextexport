package com.garrettmcbride.contextexport

/**
 * Fluent builder for markdown tables.
 *
 * Eliminates hand-written `|` pipes and `|---|` separator lines.
 * Automatically pads columns and handles escaping.
 *
 * ## Usage
 *
 * ### DSL style (recommended)
 * ```kotlin
 * sb.appendTable("Name", "Level", "HP") {
 *     row("Grimjaw", "3", "22")
 *     row("Pip", "1", "8")
 * }
 * ```
 *
 * ### Builder style
 * ```kotlin
 * val table = MarkdownTable("Name", "Level", "HP")
 * table.row("Grimjaw", "3", "22")
 * table.row("Pip", "1", "8")
 * sb.append(table.build())
 * ```
 *
 * ### From a list
 * ```kotlin
 * sb.appendTable("Name", "Level", "HP") {
 *     rows(characters) { c -> arrayOf(c.name, "${c.level}", "${c.hp}") }
 * }
 * ```
 */
class MarkdownTable(private vararg val headers: String) {

    private val dataRows = mutableListOf<Array<out String>>()

    /**
     * Adds a single row. Values must match the number of headers.
     * Extra values are ignored; missing values become empty strings.
     */
    fun row(vararg values: String) {
        dataRows.add(values)
    }

    /**
     * Adds rows from a list by mapping each item to column values.
     */
    fun <T> rows(items: Iterable<T>, mapper: (T) -> Array<String>) {
        for (item in items) {
            dataRows.add(mapper(item))
        }
    }

    /**
     * Builds the markdown table string.
     *
     * @return The formatted table with header, separator, and data rows.
     */
    fun build(): String {
        if (headers.isEmpty()) return ""

        val sb = StringBuilder()
        val colCount = headers.size

        // Header row
        sb.appendLine(headers.joinToString(" | ", "| ", " |"))

        // Separator row
        sb.appendLine(headers.map { "---" }.joinToString(" | ", "| ", " |"))

        // Data rows
        for (row in dataRows) {
            val cells = Array(colCount) { i ->
                if (i < row.size) row[i] else ""
            }
            sb.appendLine(cells.joinToString(" | ", "| ", " |"))
        }

        return sb.toString()
    }

    /** Whether any data rows have been added. */
    val isEmpty: Boolean get() = dataRows.isEmpty()

    /** Number of data rows (excluding header). */
    val size: Int get() = dataRows.size
}

/**
 * Appends a markdown table to this [StringBuilder] using a DSL block.
 *
 * ```kotlin
 * sb.appendTable("Name", "Score") {
 *     row("Alice", "95")
 *     row("Bob", "87")
 * }
 * ```
 *
 * @param headers Column header names.
 * @param block Configuration block to add rows.
 */
fun StringBuilder.appendTable(vararg headers: String, block: MarkdownTable.() -> Unit) {
    val table = MarkdownTable(*headers)
    table.block()
    append(table.build())
}
