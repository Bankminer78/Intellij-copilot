package com.github.bankminer78.intellijcopilot

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

object ContextExtractor {

    /**
     * Extracts up to 10 lines before the caret and up to 10 lines after the caret,
     * inserting "<CARET>" at the exact cursor position.  All document/caret reads
     * happen inside a read-action to satisfy IntelliJ’s threading rules.
     */
    fun extractContextWithMarker(project: Project, editor: Editor): String {
        return ApplicationManager.getApplication().runReadAction<String> {
            val document = editor.document
            val caretOffset = editor.caretModel.offset

            // 1️⃣ Which line the caret is on
            val caretLine = document.getLineNumber(caretOffset)

            // 2️⃣ Compute startLine = max(0, caretLine - 10)
            val startLine = (caretLine - 10).coerceAtLeast(0)

            // 3️⃣ Compute endLine = min(lastLine, caretLine + 10)
            val lastLineIndex = document.lineCount - 1
            val endLine = (caretLine + 10).coerceAtMost(lastLineIndex)

            // 4️⃣ Compute absolute offsets of those line boundaries
            val startOffset = document.getLineStartOffset(startLine)
            val endOffset = document.getLineEndOffset(endLine)

            // 5️⃣ Grab the full block of text from startOffset to endOffset
            val fullBlock = document.getText(TextRange(startOffset, endOffset))

            // 6️⃣ Now insert "<CARET>" at the right place within that block
            val relativeCaretIndex = (caretOffset - startOffset).coerceIn(0, fullBlock.length)
            val beforeCaret = fullBlock.substring(0, relativeCaretIndex)
            val afterCaret = fullBlock.substring(relativeCaretIndex)

            buildString {
                append(beforeCaret)
                append("<CARET>")
                append(afterCaret)
            }
        }
    }
}