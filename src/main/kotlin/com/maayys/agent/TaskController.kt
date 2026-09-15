package com.maayys.agent

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

interface TaskController {
    fun start(task: String, params: Map<String, String> = emptyMap()): Boolean
    fun stop()
    fun isRunning(): Boolean
}

class RuntimeTaskController(
    private val runtime: com.maayys.runtime.MaaRuntime,
    private val listener: com.maayys.runtime.RuntimeEventListener = com.maayys.runtime.RuntimeEventListener { }
) : TaskController {
    // Listener executes on the publishing thread; UI consumers must dispatch to main.
    private fun emit(event: com.maayys.runtime.RuntimeEvent) {
        try { listener.onEvent(event) } catch (_: Exception) { /* observers must not break task cleanup */ }
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var future: Future<*>? = null

    @Synchronized override fun start(task: String, params: Map<String, String>): Boolean {
        if (executor.isShutdown || running.get() || !runtime.isRunning() || task.isBlank()) return false
        val snapshot = params.toMap()
        running.set(true)
        try {
            future = executor.submit {
                var outcome: com.maayys.runtime.RuntimeEvent? = null
                try {
                    emit(com.maayys.runtime.RuntimeEvent.Started(task))
                    if (!Thread.currentThread().isInterrupted) {
                        outcome = com.maayys.runtime.RuntimeEvent.Finished(task, runtime.runPipeline(task, snapshot))
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    outcome = com.maayys.runtime.RuntimeEvent.Failed(task, error.message ?: error.javaClass.simpleName)
                } catch (error: LinkageError) {
                    outcome = com.maayys.runtime.RuntimeEvent.Failed(task, error.message ?: "Native linkage failure")
                } finally {
                    running.set(false)
                    if (!Thread.currentThread().isInterrupted && !executor.isShutdown) outcome?.let(::emit)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            running.set(false)
            return false
        }
        return true
    }

    @Synchronized override fun stop() {
        future?.cancel(true)
        try { runtime.stop() } finally {
            executor.shutdownNow()
            future = null
            running.set(false)
        }
    }

    override fun isRunning() = running.get()
}
