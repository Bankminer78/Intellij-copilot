package com.github.bankminer78.intellijcopilot

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls the OpenAI Chat Completions API (GPT-4o) synchronously.
 *
 * It:
 *  1. Limits prompt length to avoid huge payloads.
 *  2. Caches the last prompt/suggestion to avoid duplicate calls.
 *  3. Uses a singleton OkHttpClient + singleton Gson for minimal overhead.
 *  4. Strips markdown fences via index‐based substring.
 *  5. Reads the API key from the IntelliJ Configurable (PropertiesComponent). If none is set, emits "// NO KEY FOUND".
 */
class SuggestionProvider : Closeable {
    private val LOG = Logger.getInstance(SuggestionProvider::class.java)

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"
        private const val MAX_TOKENS = 32
        private const val TEMPERATURE = 0.7

        // Maximum number of characters from the context to send (prefix+suffix total)
        private const val MAX_PROMPT_CHARS = 2300

        // Single OkHttpClient instance with connection pooling
        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()

        // Single Gson instance
        private val gson = Gson()
    }

    // Cache for last prompt → suggestion
    private var lastPrompt: String? = null
    private var lastSuggestion: String? = null

    fun getSuggestions(context: String): List<String> {
        LOG.info("getSuggestions() called with context length=${context.length}")

        // 1️⃣ Limit context length to MAX_PROMPT_CHARS (take last part if too long)
        val prompt = if (context.length > MAX_PROMPT_CHARS) {
            val truncated = context.substring(context.length - MAX_PROMPT_CHARS)
            LOG.debug("Context truncated to last $MAX_PROMPT_CHARS chars")
            truncated
        } else {
            context
        }

        // 2️⃣ If prompt is identical to last time, return cached suggestion immediately
        if (prompt == lastPrompt && lastSuggestion != null) {
            LOG.info("Prompt matches lastPrompt; returning cached suggestion")
            return listOf(lastSuggestion!!)
        }

        // 3️⃣ Read API key from IntelliJ’s PropertiesComponent
        val apiKey = PropertiesComponent.getInstance()
            .getValue("intellijCopilot.apiKey", "")
            .trim()
        LOG.debug("Read API key from PropertiesComponent; length=${apiKey.length}")

        // 4️⃣ If no key → emit a placeholder suggestion
        if (apiKey.isEmpty()) {
            LOG.warn("No API key found; returning placeholder")
            return listOf("// NO KEY FOUND")
        }

        // 5️⃣ Build request JSON for the Chat Completions endpoint
        LOG.info("Building JSON payload for OpenAI request")
        val requestBodyJson = gson.toJson(
            ChatRequest(
                model = MODEL,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = """You are a code completion assistant.
- The user’s cursor is marked by <CARET>.
- Generate code to be pasted in place of the caret; never repeat or duplicate the character immediately to the left of <CARET>.
- Inspect the surrounding context to see how many braces (“{” and “}”), parentheses, or brackets are already balanced. Do not emit any extra closing braces that correspond to blocks already closed in the context.
- Only close braces, parentheses, or brackets that you open within your suggested continuation. In other words:
   1. Count existing unmatched “{” vs “}” in the text before <CARET>.
   2. If you open a new “{”, you may emit a matching “}” in your suggestion.
   3. Do not add a “}” for any “{” that was already closed before or after <CARET>.
- Do not output any explanation or commentary—return exactly the code continuation after <CARET>."""
                    ),
                    ChatMessage(role = "user", content = prompt)
                ),
                maxTokens = MAX_TOKENS,
                n = 1,
                temperature = TEMPERATURE
            )
        )
        LOG.debug("Payload JSON: $requestBodyJson")

        // 6️⃣ Execute HTTP call synchronously and parse result
        LOG.info("Sending HTTP request to OpenAI API: $API_URL")
        val rawResponse = try {
            runBlockingCall(apiKey, requestBodyJson)
        } catch (e: IOException) {
            LOG.error("IOException during OpenAI API call", e)
            return emptyList()
        }

        // 7️⃣ Extract the assistant’s message text
        val choiceText = rawResponse
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            .orEmpty()
        LOG.debug("Raw choice text: $choiceText")

        // 8️⃣ Strip code fences (``` … ```) if present
        val stripped = stripCodeFences(choiceText)
        LOG.info("Stripped suggestion text: $stripped")

        // 9️⃣ Cache the result and return (unless blank)
        lastPrompt = prompt
        lastSuggestion = stripped
        return if (stripped.isBlank()) {
            LOG.warn("Stripped suggestion is blank; returning empty list")
            emptyList()
        } else {
            listOf(stripped).also { LOG.info("Returning suggestion list of size=${it.size}") }
        }
    }

    /**
     * Performs a synchronous HTTP POST to the OpenAI Chat Completions endpoint.
     * Uses OkHttp under the hood. Throws IOException on failure.
     */
    @Throws(IOException::class)
    private fun runBlockingCall(apiKey: String, jsonBody: String): ChatResponse {
        LOG.debug("Constructing OkHttp request")
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        LOG.info("Executing HTTP call to OpenAI")
        httpClient.newCall(request).execute().use { response ->
            val code = response.code
            LOG.debug("Received HTTP response code=$code")
            if (!response.isSuccessful) {
                val msg = "OpenAI API error: HTTP $code ${response.message}"
                LOG.error(msg)
                throw IOException(msg)
            }
            val bodyStr = response.body?.string().orEmpty()
            LOG.debug("Response body: $bodyStr")
            return gson.fromJson(bodyStr, ChatResponse::class.java)
        }
    }

    /**
     * Strips leading/trailing Markdown fences from the assistant’s returned text.
     */
    private fun stripCodeFences(text: String): String {
        var t = text
        if (t.startsWith("```")) {
            LOG.debug("Stripping opening markdown fence")
            val firstNewlineIdx = t.indexOf('\n')
            if (firstNewlineIdx >= 0 && firstNewlineIdx + 1 < t.length) {
                t = t.substring(firstNewlineIdx + 1)
            }
        }
        val lastFence = t.lastIndexOf("```")
        if (lastFence >= 0) {
            LOG.debug("Stripping closing markdown fence")
            t = t.substring(0, lastFence).trimEnd()
        }
        return t.trim()
    }

    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val n: Int,
        val temperature: Double
    )

    private data class ChatMessage(
        val role: String,
        val content: String
    )

    private data class ChatResponse(
        val choices: List<ChatChoice>
    )

    private data class ChatChoice(
        val message: ChatMessage
    )

    /**
     * Close the OkHttpClient’s connection pool if needed.
     */
    override fun close() {
        LOG.info("Closing HTTP client connection pool")
        httpClient.connectionPool.evictAll()
    }
}