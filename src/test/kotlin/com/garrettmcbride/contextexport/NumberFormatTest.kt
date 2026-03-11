package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {

    @Test
    fun `compact small numbers unchanged`() {
        assertEquals("0", NumberFormat.compact(0))
        assertEquals("42", NumberFormat.compact(42))
        assertEquals("999", NumberFormat.compact(999))
    }

    @Test
    fun `compact thousands`() {
        assertEquals("1K", NumberFormat.compact(1000))
        assertEquals("1.5K", NumberFormat.compact(1500))
        assertEquals("10K", NumberFormat.compact(10_000))
        assertEquals("999.9K", NumberFormat.compact(999_900))
    }

    @Test
    fun `compact millions`() {
        assertEquals("1M", NumberFormat.compact(1_000_000))
        assertEquals("2.3M", NumberFormat.compact(2_300_000))
        assertEquals("10M", NumberFormat.compact(10_000_000))
    }

    @Test
    fun `compact billions`() {
        assertEquals("1B", NumberFormat.compact(1_000_000_000))
        assertEquals("7.8B", NumberFormat.compact(7_800_000_000L))
    }

    @Test
    fun `compact with unit`() {
        assertEquals("1.5K lbs", NumberFormat.compact(1500, "lbs"))
        assertEquals("42 cal", NumberFormat.compact(42, "cal"))
        assertEquals("2.3M gp", NumberFormat.compact(2_300_000, "gp"))
    }

    @Test
    fun `compact with custom decimals`() {
        assertEquals("1.50K", NumberFormat.compact(1500, decimals = 2))
        assertEquals("2K", NumberFormat.compact(1500, decimals = 0))
    }

    @Test
    fun `compact negative numbers`() {
        assertEquals("-1.5K", NumberFormat.compact(-1500))
        assertEquals("-42", NumberFormat.compact(-42))
    }

    @Test
    fun `compact float values`() {
        assertEquals("1.5K", NumberFormat.compact(1500.0))
        assertEquals("750", NumberFormat.compact(750.0f))
    }

    @Test
    fun `withCommas formats integers`() {
        assertEquals("0", NumberFormat.withCommas(0))
        assertEquals("42", NumberFormat.withCommas(42))
        assertEquals("1,234", NumberFormat.withCommas(1234))
        assertEquals("1,234,567", NumberFormat.withCommas(1_234_567))
        assertEquals("1,000,000,000", NumberFormat.withCommas(1_000_000_000))
    }

    @Test
    fun `withCommas with unit`() {
        assertEquals("1,234 lbs", NumberFormat.withCommas(1234, "lbs"))
    }

    @Test
    fun `withCommas negative`() {
        assertEquals("-1,234", NumberFormat.withCommas(-1234))
    }
}
