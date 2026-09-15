package com.maayys.agent

import org.junit.Assert.*
import org.junit.Test

class RandomTouchActionTest {
    private class RecordingController : Controller {
        val clicks = mutableListOf<Pair<Int, Int>>()
        override fun click(x: Int, y: Int): Boolean { clicks += x to y; return true }
        override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = false
    }

    @Test fun singlePixelAlwaysUsesExactCoordinate() {
        val controller = RecordingController()
        val action = RandomTouchAction()
        repeat(100) { assertTrue(action.run("", Rect(7, 9, 1, 1), controller)) }
        assertTrue(controller.clicks.all { it == (7 to 9) })
    }

    @Test fun sampledCoordinatesStayInsideRoi() {
        val controller = RecordingController()
        val action = RandomTouchAction()
        repeat(1000) { assertTrue(action.run("", Rect(10, 20, 30, 40), controller)) }
        assertTrue(controller.clicks.all { (x, y) -> x in 10..39 && y in 20..59 })
    }

    @Test fun invalidAndOverflowingRoisDoNotClick() {
        val controller = RecordingController()
        val action = RandomTouchAction()
        assertFalse(action.run("", null, controller))
        assertFalse(action.run("", Rect(0, 0, 0, 1), controller))
        assertFalse(action.run("", Rect(-1, 0, 2, 2), controller))
        assertFalse(action.run("", Rect(Int.MAX_VALUE, 0, 2, 1), controller))
        assertFalse(action.run("", Rect(0, Int.MAX_VALUE, 1, 2), controller))
        assertTrue(controller.clicks.isEmpty())
    }
}