package com.maayys.agent

import org.junit.Assert.*
import org.junit.Test

class RandomSwipeActionTest {
    private class Recorder(val result: Boolean = true) : Controller {
        val calls = mutableListOf<List<Long>>()
        override fun click(x: Int, y: Int) = false
        override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long): Boolean {
            calls += listOf(sx.toLong(), sy.toLong(), ex.toLong(), ey.toLong(), durationMs)
            return result
        }
    }
    private fun args(delay: String = "0", start: String = "[10,20,1,1]") =
        "{\"start_roi\":$start,\"end_roi\":[30,40,1,1],\"delay\":$delay}"

    @Test fun zeroDelayUsesUpstreamDefault() {
        val c = Recorder()
        assertTrue(RandomSwipeAction().run(args(), null, c))
        assertEquals(listOf(10L,20L,30L,40L,200L), c.calls.single())
    }
    @Test fun randomCoordinatesRemainInsideRoi() {
        val c = Recorder(); val action = RandomSwipeAction()
        repeat(500) { assertTrue(action.run(args("250", "[10,20,5,8]"), null, c)) }
        assertTrue(c.calls.all { it[0] in 10L..14L && it[1] in 20L..27L && it[4] == 250L })
    }
    @Test fun invalidParametersNeverReachController() {
        val c = Recorder(); val action = RandomSwipeAction()
        listOf(args("-1"), args("60001"), args("1.5"), args("\"200\""),
            args(start="[0,0,0,1]"), args(start="[2147483647,0,2,1]"),
            args(start="[-1,0,1,1]"), "{}", args() + " trailing").forEach {
            assertFalse(it, action.run(it, null, c))
        }
        assertTrue(c.calls.isEmpty())
    }
    @Test fun controllerFailureIsPropagated() {
        assertFalse(RandomSwipeAction().run(args(), null, Recorder(false)))
        assertFalse(RandomSwipeAction().run(args(), null, null))
    }
    @Test fun interruptedThreadNeverSwipes() {
        val c = Recorder()
        try {
            Thread.currentThread().interrupt()
            assertFalse(RandomSwipeAction().run(args(), null, c))
            assertTrue(c.calls.isEmpty())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}