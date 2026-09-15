package com.maayys.runtime

import com.maayys.agent.Controller
import java.io.File

data class RuntimeConfig(val resourceDir: File, val controller: Controller)
interface MaaRuntime {
    fun start(config: RuntimeConfig): Boolean
    fun runPipeline(task: String, params: Map<String, String> = emptyMap()): Boolean
    fun stop()
    fun isRunning(): Boolean
}

class NativeMaaRuntime(private val native: NativeBridge) : MaaRuntime {
    private var running = false
    override fun start(config: RuntimeConfig): Boolean {
        if (running || !config.resourceDir.isDirectory) return false
        running = native.initialize(config.resourceDir.absolutePath, config.controller)
        return running
    }
    override fun runPipeline(task: String, params: Map<String, String>): Boolean {
        if (!running || task.isBlank()) return false
        return native.run(task, params)
    }
    override fun stop() { if (running) native.shutdown(); running = false }
    override fun isRunning() = running
}

interface NativeBridge {
    fun initialize(resourcePath: String, controller: Controller): Boolean
    fun run(task: String, params: Map<String, String>): Boolean
    fun shutdown()
}
