package com.maayys.agent

import java.io.File

class ShellScreenshotProvider(private val shell: (String) -> ByteArray?) : ScreenshotProvider {
    override fun screenshot(): ByteArray? = runCatching { shell("screencap -p") }.getOrNull()
}

class FileScreenshotProvider(private val file: File) : ScreenshotProvider {
    override fun screenshot(): ByteArray? = runCatching { file.takeIf { it.isFile }?.readBytes() }.getOrNull()
}