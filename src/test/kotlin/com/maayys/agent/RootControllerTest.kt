package com.maayys.agent

import org.junit.Assert.*
import org.junit.Test

class RootControllerTest {
    private fun result(code: Int = 0, text: String = "") =
        RootCommandResult(code, text.toByteArray(), byteArrayOf())

    @Test fun authorizationRequiresRootUidAndSuccessfulExit() {
        assertTrue(RootController { result(text = "0\n") }.requestAuthorization())
        assertFalse(RootController { result(text = "2000\n") }.requestAuthorization())
        assertFalse(RootController { result(1, "0") }.requestAuthorization())
    }

    @Test fun missingSuAndDeniedPermissionReturnFalse() {
        assertFalse(RootController { throw java.io.IOException("su missing") }.requestAuthorization())
        assertFalse(RootController { result(1, "Permission denied") }.click(1, 2))
    }

    @Test fun successfulInputDoesNotRequireOutput() {
        val calls = mutableListOf<String>()
        val controller = RootController { calls += it; result() }
        assertTrue(controller.click(10, 20))
        assertTrue(controller.swipe(1, 2, 3, 4, 500))
        assertEquals(listOf("input tap 10 20", "input swipe 1 2 3 4 500"), calls)
    }

    @Test fun invalidArgumentsNeverReachShell() {
        var calls = 0
        val controller = RootController { calls++; result() }
        assertFalse(controller.click(-1, 0))
        assertFalse(controller.swipe(0, 0, 1, 1, 0))
        assertFalse(controller.swipe(0, 0, 1, 1, 60_001))
        assertFalse(controller.launch("com.example;reboot"))
        assertFalse(controller.launch("com.example\nreboot"))
        assertEquals(0, calls)
    }

    @Test fun screenshotChecksExitCodeAndBinarySignature() {
        // Signature fixture tests the transport check, not full PNG decoding.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
        assertArrayEquals(png, RootController { RootCommandResult(0, png, byteArrayOf()) }.screenshot())
        assertNull(RootController { RootCommandResult(1, png, byteArrayOf()) }.screenshot())
        assertNull(RootController { result(text = "not an image") }.screenshot())
    }

    @Test fun launchRequiresConfirmation() {
        assertTrue(RootController { result(text = "Events injected: 1\n") }.launch("com.example.game"))
        assertFalse(RootController { result() }.launch("com.example.game"))
        assertFalse(RootController { result(1, "Events injected: 1") }.launch("com.example.game"))
    }

    @Test fun interruptionIsPreserved() {
        try {
            assertFalse(RootController { throw InterruptedException() }.requestAuthorization())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}
