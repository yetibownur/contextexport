package com.garrettmcbride.contextexport

/**
 * Utilities for formatting numbers with K/M/B suffixes and units.
 *
 * Designed for prompt output where compact, human-readable numbers
 * save tokens and improve readability.
 *
 * Usage:
 * ```kotlin
 * NumberFormat.compact(1_500)       // "1.5K"
 * NumberFormat.compact(2_300_000)   // "2.3M"
 * NumberFormat.compact(750)         // "750"
 *
 * NumberFormat.compact(1_500, "lbs")   // "1.5K lbs"
 * NumberFormat.compact(42, "cal")      // "42 cal"
 * ```
 */
object NumberFormat {

    /**
     * Formats a number with K/M/B suffixes for compact display.
     *
     * - Values under 1,000 are shown as-is (no decimal)
     * - 1,000–999,999 → "1.5K"
     * - 1,000,000–999,999,999 → "2.3M"
     * - 1,000,000,000+ → "1.2B"
     *
     * @param value The number to format.
     * @param unit Optional unit suffix (e.g., "lbs", "cal", "gp"). Added after the number.
     * @param decimals Number of decimal places for K/M/B values (default: 1).
     * @return Formatted string.
     */
    fun compact(value: Number, unit: String = "", decimals: Int = 1): String {
        val d = value.toDouble()
        if (d.isNaN()) return if (unit.isNotEmpty()) "NaN $unit" else "NaN"
        if (d.isInfinite()) {
            val s = if (d < 0) "-∞" else "∞"
            return if (unit.isNotEmpty()) "$s $unit" else s
        }
        val abs = kotlin.math.abs(d)
        val sign = if (d < 0) "-" else ""
        val fmt = "%.${decimals}f"

        val number = when {
            abs >= 1_000_000_000 -> "${sign}${fmt.format(abs / 1_000_000_000)}B"
            abs >= 1_000_000 -> "${sign}${fmt.format(abs / 1_000_000)}M"
            abs >= 1_000 -> "${sign}${fmt.format(abs / 1_000)}K"
            else -> {
                // No decimals for small numbers
                if (d == d.toLong().toDouble()) {
                    "${sign}${abs.toLong()}"
                } else {
                    "${sign}${fmt.format(abs)}"
                }
            }
        }

        // Strip trailing zeros after decimal (1.0K → 1K, but keep 1.5K)
        val cleaned = number.replace(Regex("\\.(0+)([KMB])$"), "$2")
            .replace(Regex("\\.0+$"), "")

        return if (unit.isNotEmpty()) "$cleaned $unit" else cleaned
    }

    /**
     * Formats an integer with comma separators.
     *
     * ```kotlin
     * NumberFormat.withCommas(1234567)  // "1,234,567"
     * NumberFormat.withCommas(42)       // "42"
     * ```
     *
     * @param value The number to format.
     * @param unit Optional unit suffix.
     * @return Formatted string with commas.
     */
    fun withCommas(value: Long, unit: String = ""): String {
        val sign = if (value < 0) "-" else ""
        val abs = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
        val str = abs.toString()
        val result = buildString {
            for ((i, c) in str.withIndex()) {
                if (i > 0 && (str.length - i) % 3 == 0) append(',')
                append(c)
            }
        }
        val formatted = "$sign$result"
        return if (unit.isNotEmpty()) "$formatted $unit" else formatted
    }

    /**
     * Formats an integer with comma separators.
     * Convenience overload for [Int].
     */
    fun withCommas(value: Int, unit: String = ""): String = withCommas(value.toLong(), unit)
}
