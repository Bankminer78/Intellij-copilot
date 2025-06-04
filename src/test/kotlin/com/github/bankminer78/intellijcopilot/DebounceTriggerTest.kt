// File: src/test/kotlin/com/github/bankminer78/intellijcopilot/DebounceTriggerTest.kt
package com.github.bankminer78.intellijcopilot

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebounceTriggerTest {

    @Test
    fun `flag should not fire immediately after creation`() {
        val trigger = DebounceTrigger(delayMillis = 200)
        // Immediately, no event and no ready-to-fetch
        assertFalse(trigger.consume(), "Flag should be false initially")
    }

    @Test
    fun `flag fires only after delay without new events`() = runBlocking {
        val trigger = DebounceTrigger(delayMillis = 200)

        trigger.event()
        // Before 200ms, still false
        delay(100)
        assertFalse(trigger.consume(), "Flag should not fire before delay")

        // After additional time beyond 200ms, it should fire
        delay(150)
        assertTrue(trigger.consume(), "Flag should fire after delay")

        // After consuming, it resets
        assertFalse(trigger.consume(), "Flag should reset after consume")
    }

    @Test
    fun `frequent events prevent firing until idle`() = runBlocking {
        val trigger = DebounceTrigger(delayMillis = 200)

        // Emit events every 100ms, which is less than 200ms debounce
        repeat(3) {
            trigger.event()
            delay(100)
            assertFalse(trigger.consume(), "Flag must not fire while receiving frequent events")
        }

        // Now wait without new events to let debounce complete
        delay(250)
        assertTrue(trigger.consume(), "Flag should finally fire after idle period")
    }
}