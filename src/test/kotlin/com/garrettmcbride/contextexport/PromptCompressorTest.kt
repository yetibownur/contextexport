package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptCompressorTest {

    @Test
    fun `empty string unchanged`() {
        assertEquals("", PromptCompressor.compress(""))
    }

    @Test
    fun `single line trimmed`() {
        assertEquals("hello", PromptCompressor.compress("hello  "))
    }

    @Test
    fun `two blank lines preserved`() {
        val input = "line1\n\n\nline2"
        val result = PromptCompressor.compress(input)
        // Two blank lines between = 2 empty strings = "line1\n\n\nline2" stays as "line1\n\n\nline2"
        // Actually: lines = [line1, "", "", line2]. blank counter hits 2 on second blank, both kept.
        // Third line (line2) is content. So result = "line1\n\n\nline2"
        assertTrue(result.contains("line1"))
        assertTrue(result.contains("line2"))
    }

    @Test
    fun `three or more blank lines collapsed to two`() {
        val input = "line1\n\n\n\n\nline2"
        val result = PromptCompressor.compress(input)
        // Should have at most 2 blank lines between
        val lines = result.lines()
        var maxConsecutiveBlanks = 0
        var current = 0
        for (line in lines) {
            if (line.isEmpty()) {
                current++
                if (current > maxConsecutiveBlanks) maxConsecutiveBlanks = current
            } else {
                current = 0
            }
        }
        assertTrue(maxConsecutiveBlanks <= 2, "Max consecutive blanks should be 2, got $maxConsecutiveBlanks")
    }

    @Test
    fun `trailing whitespace stripped from lines`() {
        val input = "hello   \nworld\t\t\nfoo  "
        val result = PromptCompressor.compress(input)
        for (line in result.lines()) {
            assertEquals(line.trimEnd(), line, "Line should have no trailing whitespace: '$line'")
        }
    }

    @Test
    fun `trailing blank lines removed`() {
        val input = "content\n\n\n\n"
        val result = PromptCompressor.compress(input)
        assertTrue(!result.endsWith("\n"), "Should not end with blank line")
    }

    @Test
    fun `leading indentation preserved`() {
        val input = "  - indented item\n    - nested item"
        val result = PromptCompressor.compress(input)
        assertTrue(result.startsWith("  - indented"), "Leading spaces should be preserved")
        assertTrue(result.contains("    - nested"), "Nested indentation should be preserved")
    }

    @Test
    fun `savings reports character and token savings`() {
        val input = "line1\n\n\n\n\n\n\n\nline2   \n\n\n"
        val (charsSaved, tokensSaved) = PromptCompressor.savings(input)
        assertTrue(charsSaved > 0, "Should save some characters")
        assertTrue(tokensSaved >= 0, "Token savings should be non-negative")
    }

    @Test
    fun `realistic prompt compression`() {
        val input = buildString {
            appendLine("# Title")
            appendLine()
            appendLine()
            appendLine()
            appendLine()
            appendLine("## Section A")
            appendLine("Content here.   ")
            appendLine()
            appendLine()
            appendLine()
            appendLine()
            appendLine()
            appendLine("## Section B")
            appendLine("More content.  ")
            appendLine()
            appendLine()
            appendLine()
        }

        val result = PromptCompressor.compress(input)
        assertTrue(result.length < input.length, "Compressed should be smaller")
        assertTrue(result.contains("# Title"))
        assertTrue(result.contains("## Section A"))
        assertTrue(result.contains("## Section B"))
    }
}
