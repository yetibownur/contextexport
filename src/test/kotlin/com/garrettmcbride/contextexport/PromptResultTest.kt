package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptResultTest {

    @Test
    fun `single chunk result reports not multi-part`() {
        val result = PromptResult(
            fullText = "hello",
            chunks = listOf("hello"),
            sizeBytes = 5,
            estimatedTokens = 1,
            enabledSectionCount = 1,
            chunkSize = 15000
        )

        assertEquals(1, result.chunkCount)
        assertFalse(result.isMultiPart)
    }

    @Test
    fun `multi chunk result reports multi-part`() {
        val result = PromptResult(
            fullText = "hello world",
            chunks = listOf("hello", "world"),
            sizeBytes = 11,
            estimatedTokens = 2,
            enabledSectionCount = 2,
            chunkSize = 5
        )

        assertEquals(2, result.chunkCount)
        assertTrue(result.isMultiPart)
    }

    @Test
    fun `sizeKb calculates correctly`() {
        val result = PromptResult(
            fullText = "",
            chunks = emptyList(),
            sizeBytes = 2048,
            estimatedTokens = 0,
            enabledSectionCount = 0,
            chunkSize = 15000
        )

        assertEquals(2.0, result.sizeKb)
    }

    @Test
    fun `sizeKb for 1024 bytes is 1 KB`() {
        val result = PromptResult(
            fullText = "",
            chunks = emptyList(),
            sizeBytes = 1024,
            estimatedTokens = 0,
            enabledSectionCount = 0,
            chunkSize = 15000
        )

        assertEquals(1.0, result.sizeKb)
    }
}
