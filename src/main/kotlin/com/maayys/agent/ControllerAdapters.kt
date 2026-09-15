package com.maayys.agent

import java.io.File

interface ScreenshotProvider { fun screenshot(): ByteArray? }
interface PackageLauncher { fun launch(packageName: String): Boolean }

class AdbController(private val shell: (String) -> String) : Controller, ScreenshotProvider, PackageLauncher {
    override fun click(x: Int, y: Int) = runCatching { shell("input tap $x $y"); true }.getOrDefault(false)
    override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = runCatching { shell("input swipe $sx $sy $ex $ey $durationMs"); true }.getOrDefault(false)
    override fun screenshot(): ByteArray? = null
    override fun launch(packageName: String): Boolean = shell("monkey -p $packageName 1").isNotBlank()
}

class ShizukuController(private val command: (String) -> String) : Controller, ScreenshotProvider, PackageLauncher {
    override fun click(x: Int, y: Int) = command("input tap $x $y").isNotEmpty()
    override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = command("input swipe $sx $sy $ex $ey $durationMs").isNotEmpty()
    override fun screenshot(): ByteArray? = null
    override fun launch(packageName: String): Boolean = command("monkey -p $packageName 1").isNotEmpty()
}