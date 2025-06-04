# IntelliJ Copilot Plugin

A simple IntelliJ IDEA plugin that demonstrates proof-of-concept AI-assisted code completion using the OpenAI Chat Completions API (gpt-4o). It injects inline “ghost text” suggestions after the user pauses typing.

---

## Features

1. **Inline AI-Powered Suggestions**
  - Debounces user typing for a short interval (e.g., 300 ms).
  - After the editor is idle, sends the surrounding context (up to 10 lines before and after the caret, with `<CARET>` inserted) to OpenAI.
  - Displays a single gray‐text “ghost suggestion” on the next keystroke, which the user can accept or ignore.

2. **Context Extraction**
  - Captures up to 10 lines before and after the caret position in the active editor.
  - Marks the caret position explicitly with `<CARET>` so the AI knows where to continue.
  - All document and caret reads run inside a read-action to satisfy IntelliJ threading rules.

3. **OpenAI Suggestion Provider**
  - If an API key is not set, immediately returns a placeholder suggestion (`// NO KEY FOUND`).
  - Limits the prompt to a configurable maximum number of characters (e.g., 2300) to avoid excessively large payloads.
  - Caches the last prompt and its suggestion to avoid redundant network calls for identical inputs.
  - Sends a system message instructing the model to generate code continuations without extra commentary, and to maintain proper bracket/parenthesis balance.
  - Strips any Markdown fences (```…```) from OpenAI’s response.
  - Logs each major step (payload building, HTTP response codes, any errors) to IDEA’s log for easier debugging.

---

## Limitations & Known Issues

- **Proof of Concept (OpenAI Only)**
  - This plugin is intended as a proof of concept. It currently only supports OpenAI’s Chat Completions endpoint (gpt-4o).
  - Many in the industry prefer alternatives like Anthropic’s Claude for code‐oriented completions; this implementation does not integrate with Claude or other LLMs out of the box.

- **Bracket/Parenthesis Balancing**
  - While the system prompt attempts to count unmatched `{` vs. `}`, `(` vs. `)`, and `[` vs. `]` before `<CARET>`, the AI sometimes emits extra or missing closing braces—especially in nested or truncated code snippets.
  - Accepting a suggestion can occasionally produce mismatched delimiters, which may require manual correction.

- **Latency & Race Conditions**
  - Real‐world HTTP round-trip times vary: on a fast network, suggestions can appear within 200–500 ms after the idle period.
  - Under slow connectivity or heavy CPU load, you may notice a brief delay between “idle” and the ghost text appearing.
  - The plugin emits the ghost suggestion on the *next keystroke* after the AI response arrives, which can feel slightly delayed if the user immediately resumes typing.

- **Single‐Suggestion Workflow**
  - Only one suggestion is returned per request. There is no multi‐option “choose from n completions” UI.
  - No support for “expand this line” or “explain code” modes—only next‐token or next‐line code continuation.

---

## Installation & Setup

1. **Clone the Repository**
   ```bash
   git clone https://github.com/your-username/intellij-copilot-plugin.git
   cd intellij-copilot-plugin
   ```
2. **Open in IntelliJ IDEA**
  - Use **File → Open**, select the project root.
  - IntelliJ will import the Gradle build automatically.

3. **Configure Your OpenAI API Key**
  - Open **File → Settings** (or **Preferences** on macOS), then navigate to **Tools → Copilot API Key**.
  - Paste your OpenAI API key (e.g., `sk-_________`) into the field.
  - Click **Apply** and **OK**.
  - The key is stored securely via `PropertiesComponent` and is not committed to version control.

4. **Run the Plugin**
  - In the top‐right run configuration dropdown, select **Run Plugin**.
  - Press **Run** (or press Shift+F10). This will launch a new sandbox instance of IntelliJ IDEA with your plugin installed.
  - In the sandbox editor, open or create a code file (e.g., Java, Kotlin).
  - Start typing; after a brief pause (≈300 ms), the plugin will request a suggestion from OpenAI. On your next keystroke, you’ll see a gray‐text suggestion appear.

5. **Accepting a Suggestion**
  - When you see gray‐text inline, press **Tab** (or your configured accept key) to accept the ghost text.
  - You can also ignore it and continue typing to replace it.

---

## Developer Notes

- **Project Structure**
  - `ContextExtractor.kt`
    - Extracts a window of up to 10 lines around the caret, inserting `<CARET>`.
    - Ensures read‐thread safety by using `ApplicationManager.getApplication().runReadAction { … }`.
  - `SuggestionProvider.kt`
    - Builds an OpenAI chat payload, sends a synchronous HTTP POST via OkHttp, and parses results using Gson.
    - Applies caching, prompt truncation, system instructions, and Markdown‐fence stripping.
    - Logs each major step for debugging.
  - `ChatInlineCompletionProvider.kt`
    - Uses `DebounceTrigger` (or similar) to wait for 300 ms of idle time after each keystroke.
    - When the idle flag is consumed, calls `getSuggestions(...)` synchronously on a background coroutine.
    - Wraps the AI’s single‐line or multi‐line suggestion as an `InlineCompletionGrayTextElement`, returned via a Kotlin Flow.

- **Logging**
  - Uses IntelliJ’s built‐in `Logger` (from `com.intellij.openapi.diagnostic.Logger`) for both `info` and `debug` statements.
  - You can see these logs under **Help → Show Log in Finder/Explorer** in the sandbox instance.

- **Testing the Core Components**
  - The debounce logic is extracted into a standalone `DebounceTrigger` class, which is covered by unit tests under `src/test/kotlin/com/github/bankminer78/intellijcopilot/DebounceTriggerTest.kt`.
  - `ContextExtractor` has unit tests that verify correct line selection and caret insertion.
  - `SuggestionProvider` is tested via E2E-style tests (skipped if `OPENAI_API_KEY` is missing) that confirm bracket‐balance and placeholder behavior.

- **InlineCompletionSuggestion**
  - Currently ghost text flow is controlled using InlineCompletionSuggestion, which will be removed in future versions of IntelliJ. It is used here for ease and proof-of-concept.
---

## Limitations & Future Work

1. **Support for Multiple AI Backends**
  - Currently only OpenAI is supported. A complete version would definitely include support for Claude and Gemini, and a plugin configuration to switch between providers.

2. **Improved Bracket Matching**
  - The current prompt asks the AI to inspect unmatched braces/parentheses in context, but real‐world code often has nested or truncated fragments that confuse the model.
  - A local parser/formatter and more fine-tuned prompting would be a next step.

3. **Undo/Redo and Edge Cases**
  - Inserting a ghost suggestion directly into the editor may interfere with the user’s undo stack. Further work would ensure that accepting or rejecting a suggestion integrates cleanly with IntelliJ’s `CommandProcessor`.

---

