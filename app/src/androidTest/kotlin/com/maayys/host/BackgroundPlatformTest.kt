package com.maayys.host

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.hardware.display.DisplayManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maayys.platform.BackgroundConnection
import com.maayys.platform.IBackgroundDisplay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackgroundPlatformTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun assertForegroundIsMaaYys() {
        // Accessibility's active window may refer to the secondary display or
        // be null. Verify Android's actual resumed task on display 0 instead.
        val dump = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities"))
            .bufferedReader().use { it.readText() }
        val primary = Regex("(?s)Display #0 .*?(?=\\nDisplay #|\\z)").find(dump)?.value ?: error("No primary display in activity dump")
        assertTrue("Display 0 must retain MaaYYs as its resumed activity", Regex("topResumedActivity=.*" + Regex.escape(context.packageName) + "/").containsMatchIn(primary))
    }
    private fun connect(): IBackgroundDisplay = BackgroundConnection.connect(context, "ROOT") { command ->
        // The host harness executes this as root on the isolated emulator.
        // Production uses the exact same handshake with the user's su process.
        File(context.cacheDir, "background-test-command.txt").writeText(command)
    }
    private fun frame(remote: IBackgroundDisplay, predicate: (Bitmap) -> Boolean = { true }): Bitmap {
        val end = SystemClock.uptimeMillis() + 12_000
        do {
            val bitmap = runCatching { ParcelFileDescriptor.AutoCloseInputStream(remote.screenshot()).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            if (bitmap != null) { if (predicate(bitmap)) return bitmap; bitmap.recycle() }
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < end)
        error("No expected frame from the virtual display")
    }
    @Test fun virtualDisplayCapturesAndInjectsInputWithoutReplacingForeground() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val remote = connect()
            val id = remote.displayId()
            try {
                assertTrue(id > 0)
                val probe = instrumentation.context.packageName
                android.util.Log.i("BGTest", "launch probe"); assertTrue(remote.launch(probe)); android.util.Log.i("BGTest", "probe launched")
                assertTrue(remote.isOnDisplay(probe))
                frame(remote) { image -> image.width == 1280 && image.height == 720 && Color.green(image.getPixel(200,160)) > 150 && Color.red(image.getPixel(200,160)) < 100 }.recycle()
                android.util.Log.i("BGTest", "green frame received"); assertTrue(remote.click(200,160)); android.util.Log.i("BGTest", "click injected")
                frame(remote) { image -> Color.red(image.getPixel(200,160)) > 220 && Color.green(image.getPixel(200,160)) in 100..190 }.recycle()
                assertForegroundIsMaaYys()
            } finally { android.util.Log.i("BGTest", "closing"); BackgroundConnection.close(); android.util.Log.i("BGTest", "closed") }
            val displays = context.getSystemService(DisplayManager::class.java)
            val end = SystemClock.uptimeMillis() + 3000
            while (displays.getDisplay(id) != null && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
            assertNull("Closing background must remove the virtual display", displays.getDisplay(id))
        }
    }

    @Test fun onmyojiRunsOnSeparateDisplayAndCanReturnToForeground() {
        val pkg = "com.netease.onmyoji.wyzymnqsd_cps"
        ActivityScenario.launch(MainActivity::class.java).use {
            val remote = connect()
            try {
                assertTrue("Onmyoji launch on virtual display", remote.launch(pkg))
                assertTrue("Onmyoji task must really belong to the virtual display", remote.isOnDisplay(pkg))
                frame(remote) { image ->
                    val colors = mutableSetOf<Int>()
                    for (y in 0 until image.height step 36) for (x in 0 until image.width step 64) colors += image.getPixel(x,y)
                    colors.size > 8
                }.useBitmap { bitmap -> File(context.filesDir, "onmyoji-background.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                assertForegroundIsMaaYys()
                assertTrue("Move real game back to display 0", remote.bringToFront(pkg))
                assertFalse(remote.isOnDisplay(pkg))
            } finally { android.util.Log.i("BGTest", "closing"); BackgroundConnection.close(); android.util.Log.i("BGTest", "closed") }
        }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
