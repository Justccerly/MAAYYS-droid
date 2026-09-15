package com.maayys.platform

import android.graphics.Bitmap
import android.os.*
import android.view.Surface
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.maa.InputControlUtils
import com.aliothmoon.maameow.remote.internal.ActivityUtils
import com.aliothmoon.maameow.remote.internal.VirtualDisplayManager
import com.aliothmoon.maameow.third.Workarounds
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import java.io.IOException
import java.util.concurrent.Executors

/** Runs only in the explicitly authorized Root/Shizuku process, never the UI process. */
class BackgroundDisplayService : IBackgroundDisplay.Stub() {
    private val main = Handler(Looper.getMainLooper())
    private val imageWriter = Executors.newSingleThreadExecutor()
    private var ownerBinder: IBinder? = null
    private val ownerDeath = IBinder.DeathRecipient { destroy() }
    private var configured = false
    private val lastTouches = mutableMapOf<Int, Pair<Int, Int>>()

    init { Workarounds.apply() }

    override fun setup(width: Int, height: Int, dpi: Int, nativeDirectory: String, owner: IBinder): Int = synchronized(this) {
        require(width in 640..2560 && height in 360..1440 && dpi in 120..480)
        if (!configured) {
            System.setProperty("maayys.bridge.library", java.io.File(nativeDirectory, "libbridge.so").absolutePath)
            System.load(java.io.File(nativeDirectory, "libc++_shared.so").absolutePath)
            check(NativeBridgeLib.LOADED) { "MAA-Meow screenshot bridge failed to load" }
            owner.linkToDeath(ownerDeath, 0); ownerBinder = owner
            configured = true
        }
        ActivityUtils.forceFullscreenOnVirtualDisplay = true
        VirtualDisplayManager.setResolution(width, height, dpi)
        VirtualDisplayManager.start().also { check(it > 0) { "当前系统未能创建独立游戏显示器" } }
    }
    override fun displayId() = VirtualDisplayManager.getDisplayId()
    private fun requireDisplay(): Int = displayId().also { check(it > 0) { "后台显示器未运行" } }
    private fun validPackage(pkg: String) = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(pkg)
    override fun launch(packageName: String): Boolean {
        require(validPackage(packageName))
        val display = requireDisplay()
        if (ActivityUtils.getAppDisplayId(packageName) == display) return true
        return ActivityUtils.startApp(packageName, display, true, true) && ActivityUtils.ensureAppOnDisplay(packageName, display)
    }
    override fun stopApp(packageName: String): Boolean {
        require(validPackage(packageName)); ServiceManager.getActivityManager().forceStopPackage(packageName); return true
    }
    override fun bringToFront(packageName: String): Boolean {
        require(validPackage(packageName))
        return ActivityUtils.repinAppToDisplay(packageName, 0)
    }
    override fun isOnDisplay(packageName: String) = validPackage(packageName) && displayId() > 0 && ActivityUtils.getAppDisplayId(packageName) == displayId()
    override fun screenshot(): ParcelFileDescriptor {
        requireDisplay()
        val bitmap = NativeBridgeLib.getFrameBufferBitmap() ?: throw IllegalStateException("后台画面尚未就绪")
        val pipe = ParcelFileDescriptor.createReliablePipe()
        imageWriter.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encode failed")
                }
            } catch (error: Exception) { runCatching { pipe[1].closeWithError(error.message ?: "Screenshot failed") } }
            finally { bitmap.recycle() }
        }
        return pipe[0]
    }
    override fun setPreview(surface: Surface?) { if (configured) NativeBridgeLib.setPreviewSurface(surface) }
    @Synchronized override fun click(x: Int, y: Int): Boolean {
        val id = requireDisplay()
        if (!InputControlUtils.down(x, y, 0, id)) return false
        try { SystemClock.sleep(45); return InputControlUtils.up(x, y, 0, id) }
        finally { InputControlUtils.cancel(id) }
    }
    @Synchronized override fun swipe(x: Int, y: Int, ex: Int, ey: Int, durationMs: Int): Boolean {
        require(durationMs in 1..60000)
        val id = requireDisplay()
        if (!InputControlUtils.down(x, y, 0, id)) return false
        try {
            val start = SystemClock.uptimeMillis()
            do {
                val fraction = ((SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceAtMost(1f)
                if (!InputControlUtils.move((x + (ex - x) * fraction).toInt(), (y + (ey - y) * fraction).toInt(), 0, id)) return false
                if (fraction >= 1f) break
                SystemClock.sleep(12)
            } while (true)
            return InputControlUtils.up(ex, ey, 0, id)
        } finally { InputControlUtils.cancel(id) }
    }
    @Synchronized override fun touch(action: Int, x: Int, y: Int, contact: Int): Boolean {
        val id = requireDisplay()
        val previous = lastTouches[contact] ?: (0 to 0)
        val px = if (x < 0) previous.first else x; val py = if (y < 0) previous.second else y
        if (action == 0 || action == 2) lastTouches[contact] = px to py
        return when (action) { 0 -> InputControlUtils.down(px,py,contact,id); 1 -> { lastTouches.remove(contact); InputControlUtils.up(px,py,contact,id) }; 2 -> InputControlUtils.move(px,py,contact,id); else -> { lastTouches.clear(); InputControlUtils.cancel(id); true } }
    }
    override fun key(keyCode: Int): Boolean {
        val id = requireDisplay()
        return InputControlUtils.keyDown(keyCode, id) && InputControlUtils.keyUp(keyCode, id)
    }
    @Synchronized override fun close() {
        if (!configured) return
        InputControlUtils.cancel(displayId())
        NativeBridgeLib.setPreviewSurface(null)
        VirtualDisplayManager.stop()
        ownerBinder?.let { runCatching { it.unlinkToDeath(ownerDeath, 0) } }; ownerBinder = null
        configured = false
    }
    override fun destroy() { runCatching { close() }; imageWriter.shutdownNow(); main.post { kotlin.system.exitProcess(0) } }
}
