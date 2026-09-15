package com.maayys.agent

import org.junit.Assert.*
import org.junit.Test

class RandomWaitActionTest {
    @Test fun emptyAndZeroDoNotSleep() {
        val action = RandomWaitAction(sleep = { fail("Unexpected sleep") })
        listOf("", "{}", "{\"min\":0,\"max\":0}").forEach { assertTrue(action.run(it, null, null)) }
    }
    @Test fun equalBoundsUseExactDuration() {
        var duration = -1L
        val action = RandomWaitAction(sleep = { duration = it }, random = { error("No randomness needed") })
        assertTrue(action.run("{\"min\":1.25,\"max\":1.25}", null, null))
        assertEquals(1250L, duration)
    }
    @Test fun reversedStringBoundsAreSwapped() {
        var duration = -1L
        val action = RandomWaitAction(sleep = { duration = it }, random = { 0.5 })
        assertTrue(action.run("{\"min\":\"3\",\"max\":\"1\"}", null, null))
        assertEquals(2000L, duration)
    }
    @Test fun malformedAndUnsafeValuesAreRejected() {
        val action = RandomWaitAction(sleep = { fail("Unexpected sleep") })
        listOf("not json", "[]", "{min:1}", "{} trailing", "{\"min\":-1}",
            "{\"max\":\"NaN\"}", "{\"max\":1e100}").forEach {
            assertFalse(it, action.run(it, null, null))
        }
    }
    @Test fun interruptionIsPreserved() {
        try {
            val action = RandomWaitAction(sleep = { throw InterruptedException() })
            assertFalse(action.run("{\"min\":1,\"max\":1}", null, null))
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}