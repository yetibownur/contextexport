package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MarkdownTableTest {

    @Test
    fun `basic table with headers and rows`() {
        val table = MarkdownTable("Name", "Level")
        table.row("Alice", "5")
        table.row("Bob", "3")
        val result = table.build()

        assertContains(result, "| Name | Level |")
        assertContains(result, "| --- | --- |")
        assertContains(result, "| Alice | 5 |")
        assertContains(result, "| Bob | 3 |")
    }

    @Test
    fun `table with no rows has header and separator only`() {
        val table = MarkdownTable("A", "B", "C")
        val result = table.build()
        val lines = result.trim().lines()

        assertEquals(2, lines.size, "Header + separator only")
        assertTrue(table.isEmpty)
        assertEquals(0, table.size)
    }

    @Test
    fun `empty headers produces empty string`() {
        val table = MarkdownTable()
        assertEquals("", table.build())
    }

    @Test
    fun `rows function maps items`() {
        data class Item(val name: String, val score: Int)
        val items = listOf(Item("A", 10), Item("B", 20))

        val table = MarkdownTable("Name", "Score")
        table.rows(items) { arrayOf(it.name, "${it.score}") }

        assertEquals(2, table.size)
        val result = table.build()
        assertContains(result, "| A | 10 |")
        assertContains(result, "| B | 20 |")
    }

    @Test
    fun `missing values become empty strings`() {
        val table = MarkdownTable("A", "B", "C")
        table.row("only-one")
        val result = table.build()

        assertContains(result, "| only-one |  |  |")
    }

    @Test
    fun `DSL extension appends to StringBuilder`() {
        val sb = StringBuilder()
        sb.appendLine("Before table")
        sb.appendTable("X", "Y") {
            row("1", "2")
        }
        sb.appendLine("After table")

        val result = sb.toString()
        assertContains(result, "Before table")
        assertContains(result, "| X | Y |")
        assertContains(result, "| 1 | 2 |")
        assertContains(result, "After table")
    }

    @Test
    fun `size and isEmpty reflect data rows`() {
        val table = MarkdownTable("H")
        assertTrue(table.isEmpty)
        assertEquals(0, table.size)

        table.row("data")
        assertTrue(!table.isEmpty)
        assertEquals(1, table.size)
    }
}
