package com.maayys.runtime

import com.maayys.agent.*
import com.sun.jna.*

/** C callback table in the exact order of MaaCustomControllerCallbacks (5.13.0).
 * Callbacks stay strongly referenced until MaaControllerDestroy returns.
 */
internal class FrameworkController(private val api: MaaFrameworkLibrary, private val controller: Controller) {
    private val shell = RootShell(15_000)
    private fun safe(block: () -> Boolean): Byte = try { if (block()) 1 else 0 } catch (e: Exception) {
        RuntimeLog.append("设备操作失败：${e.message}"); 0
    }
    private fun command(cmd: String) = controller is RootController && shell.execute(cmd).success
    private fun packageName(value: String) = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value)
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private val callbacks: List<Callback> = listOf(
        MaaFrameworkLibrary.Simple { safe { true } },
        MaaFrameworkLibrary.Simple { safe { true } },
        MaaFrameworkLibrary.Buffer { _, buffer -> api.MaaStringBufferSet(buffer, "maayys-android-device") },
        MaaFrameworkLibrary.Features { 0L },
        MaaFrameworkLibrary.Text { intent, _ -> safe { (controller as? PackageLauncher)?.launch(intent) == true } },
        MaaFrameworkLibrary.Text { intent, _ -> safe { packageName(intent) && if (controller is MeowController) controller.remote.stopApp(intent) else command("am force-stop ${quote(intent)}") } },
        MaaFrameworkLibrary.Buffer { _, buffer -> safe {
            val bytes = (controller as? ScreenshotProvider)?.screenshot() ?: return@safe false
            api.MaaImageBufferSetEncoded(buffer, bytes, bytes.size.toLong()) != 0.toByte()
        } },
        MaaFrameworkLibrary.Click { x, y, _ -> safe { controller.click(x, y) } },
        MaaFrameworkLibrary.Swipe { x, y, ex, ey, duration, _ -> safe { controller.swipe(x, y, ex, ey, duration.toLong()) } },
        MaaFrameworkLibrary.Touch { contact, x, y, _, _ -> safe { (controller as? MeowController)?.remote?.touch(0, x, y, contact) == true } },
        MaaFrameworkLibrary.Touch { contact, x, y, _, _ -> safe { (controller as? MeowController)?.remote?.touch(2, x, y, contact) == true } },
        MaaFrameworkLibrary.Key { contact, _ -> safe { (controller as? MeowController)?.remote?.touch(1, -1, -1, contact) == true } },
        MaaFrameworkLibrary.Key { key, _ -> safe { key >= 0 && if (controller is MeowController) controller.remote.key(key) else command("input keyevent $key") } },
        MaaFrameworkLibrary.Text { text, _ -> safe { command("input text ${quote(text.replace(" ", "%s"))}") } },
        MaaFrameworkLibrary.Key { _, _ -> 0 },
        MaaFrameworkLibrary.Key { _, _ -> 0 },
        MaaFrameworkLibrary.Click { _, _, _ -> 0 },
        MaaFrameworkLibrary.Click { _, _, _ -> 0 },
        MaaFrameworkLibrary.Shell { cmd, timeout, _, buffer -> safe {
            if (controller !is RootController) return@safe false
            val result = RootShell(timeout.coerceIn(1, 120_000)).execute(cmd)
            api.MaaStringBufferSet(buffer, result.stdout.toString(Charsets.UTF_8))
            result.success
        } },
        MaaFrameworkLibrary.Simple { 1 },
        MaaFrameworkLibrary.Buffer { _, buffer -> api.MaaStringBufferSet(buffer, "{}") }
    )
    val table = Memory(callbacks.size.toLong() * Native.POINTER_SIZE).apply {
        callbacks.forEachIndexed { i, callback -> setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callback)) }
    }
}
