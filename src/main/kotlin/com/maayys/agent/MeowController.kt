package com.maayys.agent

import android.os.ParcelFileDescriptor
import com.maayys.platform.IBackgroundDisplay

/** MaaFramework's callbacks route to the MAA-Meow display, never to the phone's foreground. */
class MeowController(val remote: IBackgroundDisplay) : Controller, ScreenshotProvider, PackageLauncher {
    override fun click(x: Int, y: Int) = remote.click(x, y)
    override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = remote.swipe(sx, sy, ex, ey, durationMs.toInt())
    override fun launch(packageName: String) = remote.launch(packageName)
    override fun screenshot(): ByteArray? = remote.screenshot()?.let { descriptor ->
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer); if (size < 0) break
                check(out.size().toLong() + size <= 32 * 1024 * 1024) { "Screenshot exceeds limit" }
                out.write(buffer, 0, size)
            }
            out.toByteArray()
        }
    }
}
