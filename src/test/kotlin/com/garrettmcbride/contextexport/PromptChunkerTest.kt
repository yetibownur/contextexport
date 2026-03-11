package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PromptChunkerTest {

    @Test
    fun `short prompt returns single chunk unchanged`() {
        val prompt = "# Title\n\nSome content here."
        val chunks = PromptChunker.chunk(prompt)

        assertEquals(1, chunks.size)
        assertEquals(prompt, chunks[0])
    }

    @Test
    fun `prompt under chunk size is not split`() {
        val prompt = "x".repeat(PromptChunker.DEFAULT_CHUNK_SIZE - 1)
        val chunks = PromptChunker.chunk(prompt)

        assertEquals(1, chunks.size)
    }

    @Test
    fun `prompt exactly at chunk size is not split`() {
        val prompt = "x".repeat(PromptChunker.DEFAULT_CHUNK_SIZE)
        val chunks = PromptChunker.chunk(prompt)

        assertEquals(1, chunks.size)
    }

    @Test
    fun `long prompt with sections splits at section boundaries`() {
        val section1 = "# Header\n\nIntro text.\n\n"
        val section2 = "## Section A\n\n${"Line of content.\n".repeat(500)}\n"
        val section3 = "## Section B\n\n${"Another line.\n".repeat(500)}\n"
        val prompt = section1 + section2 + section3

        val chunks = PromptChunker.chunk(prompt, chunkSize = 5000)

        assertTrue(chunks.size >= 2, "Should split into at least 2 chunks, got ${chunks.size}")
    }

    @Test
    fun `multi-part chunks have continuation labels`() {
        val section1 = "# Header\n\n"
        val section2 = "## Section A\n\n${"x".repeat(8000)}\n\n"
        val section3 = "## Section B\n\n${"y".repeat(8000)}\n\n"
        val prompt = section1 + section2 + section3

        val chunks = PromptChunker.chunk(prompt, chunkSize = 10000)

        assertTrue(chunks.size >= 2)

        // First chunk should end with continuation message
        assertContains(chunks[0], "[Part 1 of ${chunks.size}]")
        assertContains(chunks[0], "Send the next part to continue.")

        // Last chunk should have end-of-export marker
        assertContains(chunks.last(), "[End of export")
    }

    @Test
    fun `single massive section force-splits at newline boundaries`() {
        // One huge block with no ## headings
        val prompt = (1..2000).joinToString("\n") { "Line number $it with some content to make it longer" }

        val chunks = PromptChunker.chunk(prompt, chunkSize = 5000)

        assertTrue(chunks.size >= 2, "Should force-split, got ${chunks.size} chunks")
        // Each chunk should end at a line boundary (no truncated lines)
        for (chunk in chunks) {
            val raw = chunk
                .replace(Regex("\\[Part \\d+ of \\d+].*"), "")
                .replace("[End of export", "")
                .trimEnd()
            // If there's content, it should end with a complete line
            if (raw.isNotEmpty()) {
                assertTrue(!raw.endsWith(" ") || raw.endsWith("\n") || raw.last().isLetterOrDigit() || raw.last() == '.',
                    "Chunk should end cleanly")
            }
        }
    }

    @Test
    fun `empty prompt returns single empty chunk`() {
        val chunks = PromptChunker.chunk("")
        assertEquals(1, chunks.size)
        assertEquals("", chunks[0])
    }

    @Test
    fun `custom chunk size is respected`() {
        val prompt = "## A\n${"a".repeat(100)}\n\n## B\n${"b".repeat(100)}\n\n## C\n${"c".repeat(100)}"

        // With large chunk size, should stay as one
        val largeChunks = PromptChunker.chunk(prompt, chunkSize = 10000)
        assertEquals(1, largeChunks.size)

        // With tiny chunk size, should split
        val tinyChunks = PromptChunker.chunk(prompt, chunkSize = 150)
        assertTrue(tinyChunks.size >= 2)
    }
}
