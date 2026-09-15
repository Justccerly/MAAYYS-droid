package com.maayys.app

import com.maayys.runtime.RuntimeEvent
import java.util.concurrent.CopyOnWriteArrayList

/** Lightweight in-process status bridge; it is intentionally not persisted. */
object RuntimeStatusBus {
    private val listeners = CopyOnWriteArrayList<(RuntimeEvent) -> Unit>()
    @Volatile var latest: RuntimeEvent? = null
        private set
    fun publish(event: RuntimeEvent) { latest = event; listeners.forEach { runCatching { it(event) } } }
    fun subscribe(listener: (RuntimeEvent) -> Unit) { listeners += listener; latest?.let { listener(it) } }
    fun unsubscribe(listener: (RuntimeEvent) -> Unit) { listeners -= listener }
}