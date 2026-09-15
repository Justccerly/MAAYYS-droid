package com.maayys.app

import com.maayys.agent.Controller
import org.junit.Assert.*
import org.junit.Test

class ControllerSelectorTest {
    private val fake = object : Controller {
        override fun click(x: Int, y: Int) = true
        override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = true
    }

    @Test fun noSelectionDoesNotRequestRoot() {
        var requested = false
        val selector = ControllerSelector(root = { requested = true; fake })
        assertThrows(IllegalStateException::class.java) { selector.create(null) }
        assertFalse(requested)
    }

    @Test fun unavailableShizukuNeverFallsBackToRoot() {
        var requested = false
        val selector = ControllerSelector(root = { requested = true; fake })
        assertThrows(IllegalStateException::class.java) { selector.create(ControllerMode.SHIZUKU) }
        assertFalse(requested)
    }

    @Test fun onlySelectedFactoryIsCalled() {
        val calls = mutableListOf<ControllerMode>()
        val selector = ControllerSelector(
            root = { calls += ControllerMode.ROOT; fake },
            shizuku = { calls += ControllerMode.SHIZUKU; fake },
            adb = { calls += ControllerMode.ADB; fake }
        )
        ControllerMode.entries.forEach { assertSame(fake, selector.create(it)) }
        assertEquals(ControllerMode.entries.toList(), calls)
    }

    @Test fun authorizationFailureIsPropagatedWithoutFallback() {
        var fallback = false
        val selector = ControllerSelector(
            root = { error("Denied") },
            adb = { fallback = true; fake }
        )
        val error = assertThrows(IllegalStateException::class.java) { selector.create(ControllerMode.ROOT) }
        assertEquals("Denied", error.message)
        assertFalse(fallback)
    }
}