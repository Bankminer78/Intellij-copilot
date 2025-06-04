// File: src/main/kotlin/com/github/bankminer78/intellijcopilot/DebounceTrigger.kt
package com.github.bankminer78.intellijcopilot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fires an “idle” flag once no new events have been signaled for [delayMillis].
 * - Call [event] on each user keystroke.
 * - Call [consume] to check (and reset) whether the idle period elapsed.
 */
class DebounceTrigger(private val delayMillis: Long) {
    private val sharedFlow = MutableSharedFlow<Unit>(replay = 1)
    private val ready = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        scope.launch {
            sharedFlow
                .debounce(delayMillis)
                .onEach { ready.set(true) }
                .collect()
        }
    }

    /** Should be invoked on every user keystroke. */
    fun event() {
        scope.launch { sharedFlow.emit(Unit) }
    }

    /**
     * Returns true if [delayMillis] of inactivity has elapsed since the last [event].
     * Calling [consume] resets the internal flag.
     */
    fun consume(): Boolean = ready.getAndSet(false)
}