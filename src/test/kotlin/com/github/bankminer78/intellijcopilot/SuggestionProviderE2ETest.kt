// File: src/test/kotlin/com/github/bankminer78/intellijcopilot/SuggestionProviderE2ETest.kt
package com.github.bankminer78.intellijcopilot

import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.System.getenv
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class SuggestionProviderE2ETest : BasePlatformTestCase() {

    companion object {
        private const val PREF_KEY = "intellijCopilot.apiKey"
    }

    /** Count occurrences of a single character in a string. */
    private fun String.countChar(ch: Char): Int = this.count { it == ch }

    /** Verify that braces, parentheses, and brackets are balanced. */
    private fun assertBalancedDelimiters(s: String) {
        val opensBrace = s.countChar('{')
        val closesBrace = s.countChar('}')
        assertEquals("Mismatched '{' vs '}' in suggestion: $s", opensBrace, closesBrace)

        val opensPar = s.countChar('(')
        val closesPar = s.countChar(')')
        assertEquals("Mismatched '(' vs ')' in suggestion: $s", opensPar, closesPar)

        val opensSq = s.countChar('[')
        val closesSq = s.countChar(']')
        assertEquals("Mismatched '[' vs ']' in suggestion: $s", opensSq, closesSq)
    }

    /** Skip these tests unless OPENAI_API_KEY is set in the environment. */
    private fun assumeApiKeyPresent(): String {
        val key = getenv("OPENAI_API_KEY") ?: ""
        assumeTrue("OPENAI_API_KEY not set; skipping E2E tests.", key.isNotBlank())
        return key
    }

    @Test
    fun testUnclosedForLoopCompletesProperly() {
        val apiKey = assumeApiKeyPresent()
        // Store API key in PropertiesComponent so SuggestionProvider.reads it
        PropertiesComponent.getInstance().setValue(PREF_KEY, apiKey)

        val context = """
            public class LoopTest {
                public void iterate() {

                    for (int i = 0; i < 5; i++)<CARET>
                    
                }
            }
        """.trimIndent()

        val suggestions = runBlocking { SuggestionProvider().getSuggestions(context) }
        assertTrue("Expected at least one suggestion", suggestions.isNotEmpty())
        val suggestion = suggestions.first()

        // Should include both '{' and '}', and be balanced
        assertTrue("Suggestion should open and close the for-loop body: $suggestion",
            suggestion.contains("{") && suggestion.contains("}"))
        assertBalancedDelimiters(suggestion)
    }

    @Test
    fun testSingleLetterTriggerDoesNotDuplicate() {
        val apiKey = assumeApiKeyPresent()
        PropertiesComponent.getInstance().setValue(PREF_KEY, apiKey)

        val context = """
            public class SimpleTest {
                public static void main(String[] args) {
                    S<CARET>
                }
            }
        """.trimIndent()

        val suggestions = runBlocking { SuggestionProvider().getSuggestions(context) }
        assertTrue("Expected at least one suggestion", suggestions.isNotEmpty())
        val suggestion = suggestions.first()

        // The first character should not be 'S'
        assertFalse("AI duplicated 'S' at suggestion start: $suggestion",
            suggestion.startsWith("S"))
        assertBalancedDelimiters(suggestion)
    }

    @Test
    fun testStringLiteralJsonCompletion() {
        val apiKey = assumeApiKeyPresent()
        PropertiesComponent.getInstance().setValue(PREF_KEY, apiKey)

        val context = """
            public class JsonTest {
                public void build() {
                    String json = "{ 
                        \"name\": \"Alice\",
                        \"age\": 30,
                        \"address\": {<CARET>
                        }
                    }";
                }
            }
        """.trimIndent()

        val suggestions = runBlocking { SuggestionProvider().getSuggestions(context) }
        assertTrue("Expected at least one suggestion", suggestions.isNotEmpty())
        val suggestion = suggestions.first()

        // Should start with a quote for a JSON property
        assertTrue("Expected suggestion to start with '\"', got: $suggestion",
            suggestion.trimStart().startsWith("\""))
        assertBalancedDelimiters(suggestion)

        // Quotes should be balanced (even count)
        val quoteCount = suggestion.countChar('"')
        assertTrue("Quotation marks should be balanced; got $quoteCount quotes: $suggestion",
            quoteCount % 2 == 0)
    }

    @Test
    fun testNoKeyYieldsPlaceholder() {
        // Clear any stored key
        PropertiesComponent.getInstance().setValue(PREF_KEY, "")

        val context = "some random text<CARET>"

        val suggestions = runBlocking { SuggestionProvider().getSuggestions(context) }
        assertEquals("Expected '// NO KEY FOUND' when API key is missing",
            listOf("// NO KEY FOUND"),
            suggestions)
    }
}