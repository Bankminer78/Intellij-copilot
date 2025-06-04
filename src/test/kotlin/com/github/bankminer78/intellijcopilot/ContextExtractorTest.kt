// File: src/test/kotlin/com/github/bankminer78/intellijcopilot/ContextExtractorTest.kt
package com.github.bankminer78.intellijcopilot

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class ContextExtractorTest : BasePlatformTestCase() {

    companion object {
        // If you change the number of lines in ContextExtractor, update this constant accordingly
        private const val LINES_AROUND = 10
    }

    /**
     * Verifies that when the file has fewer than LINES_AROUND above/below,
     * all lines appear in the extracted context and <CARET> is inserted correctly.
     */
    @Test
    fun testSmallFileContext() {
        val content = """
            Line1
            Line2
            <caret>This is line3
            Line4
            Line5
        """.trimIndent()

        myFixture.configureByText(PlainTextFileType.INSTANCE, content)
        val editor: Editor = myFixture.editor

        val ctx = ContextExtractor.extractContextWithMarker(project, editor)

        assertTrue("Should include Line1", ctx.contains("Line1"))
        assertTrue("Should include Line2", ctx.contains("Line2"))
        assertTrue("Should include <CARET>This is line3", ctx.contains("<CARET>This is line3"))
        assertTrue("Should include Line4", ctx.contains("Line4"))
        assertTrue("Should include Line5", ctx.contains("Line5"))
    }

    /**
     * Verifies that when the file has more than 2 * LINES_AROUND + 1 lines,
     * the extractor only returns lines [caretLine - LINES_AROUND .. caretLine + LINES_AROUND],
     * excluding lines outside that window.
     */
    @Test
    fun testTruncateToLinesAround() {
        // Build 25 lines: "Line1", "Line2", …, "Line25"
        val allLines = (1..25).map { "Line$it" }.toMutableList()
        // Place caret at line index 12 (zero‐based), i.e. "Line13"
        allLines[12] = "<caret>" + allLines[12]
        println(allLines)
        val content = allLines.joinToString("\n")

        myFixture.configureByText(PlainTextFileType.INSTANCE, content)
        val editor: Editor = myFixture.editor

        val ctx = ContextExtractor.extractContextWithMarker(project, editor)

        // With caret on index12 ("Line13"), startLine = 12 - LINES_AROUND = 2 → "Line3"
        // endLine = 12 + LINES_AROUND = 22 → "Line23"
        assertTrue("Should start at Line3", ctx.contains("Line3"))
        assertTrue("Should include <CARET>Line13", ctx.contains("<CARET>Line13"))
        assertTrue("Should end at Line23", ctx.contains("Line23"))
        assertFalse(
            "Should not include exactly 'Line1'",
            ctx.lines().any { it.trim() == "Line1" }
        )
        assertFalse("Should not include Line24", ctx.contains("Line24"))
        assertFalse("Should not include Line25", ctx.contains("Line25"))
    }

    /**
     * Verifies that when the caret is on the very first line,
     * extractor still returns everything above (none) and up to LINES_AROUND lines below.
     */
    @Test
    fun testCaretAtTopOfFile() {
        val lines = listOf(
            "<caret>First",
            "Second",
            "Third",
            "Fourth",
            "Fifth",
            "Sixth"
        )
        val content = lines.joinToString("\n")

        myFixture.configureByText(PlainTextFileType.INSTANCE, content)
        val editor: Editor = myFixture.editor

        val ctx = ContextExtractor.extractContextWithMarker(project, editor)

        // Since caret is on first line, startLine = max(0, 0 - LINES_AROUND) = 0
        // endLine = min(lastLine, 0 + LINES_AROUND) = 6th line
        assertTrue("Should start with <CARET>First", ctx.startsWith("<CARET>First"))
        assertTrue("Should include Second", ctx.contains("Second"))
        assertTrue("Should include Third", ctx.contains("Third"))
        assertTrue("Should include Fourth", ctx.contains("Fourth"))
    }

    /**
     * Verifies that when the caret is on the last line,
     * extractor still returns up to LINES_AROUND lines above and everything below (none).
     */
    @Test
    fun testCaretAtBottomOfFile() {
        val content = """
            Line1
            Line2
            Line3
            Line4
            Line5
            <caret>Line6
        """.trimIndent()

        myFixture.configureByText(PlainTextFileType.INSTANCE, content)
        val editor: Editor = myFixture.editor

        val ctx = ContextExtractor.extractContextWithMarker(project, editor)

        // Caret on "Line6" (index 5), startLine = 5 - LINES_AROUND = 0 (because LINES_AROUND = 10)
        // endLine = min(5 + 10, lastLine=5) = 5
        assertTrue("Should include Line1", ctx.contains("Line1"))
        assertTrue("Should include Line4", ctx.contains("Line4"))
        assertTrue("Should include <CARET>Line6", ctx.contains("<CARET>Line6"))
        assertFalse("Should not include any nonexistent line", ctx.contains("Line7"))
    }

    /**
     * Override getTestDataPath() only if you need to load external files under src/test/testData.
     * Here, we configure by text, so this path is unused.
     */
    override fun getTestDataPath(): String = "src/test/testData"
}