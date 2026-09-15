package com.maayys.app

import com.maayys.runtime.RuntimeEvent
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class RuntimeStatusBusTest {
    @Test fun publishUpdatesLatestAndNotifiesSubscribers() {
        val received = mutableListOf<RuntimeEvent>()
        val listener: (RuntimeEvent) -> Unit = { received += it }
        RuntimeStatusBus.unsubscribe(listener)
        RuntimeStatusBus.publish(RuntimeEvent.Stopped)
        RuntimeStatusBus.subscribe(listener)
        try {
            assertEquals(RuntimeEvent.Stopped, received.single())
            val event = RuntimeEvent.Started("demo")
            RuntimeStatusBus.publish(event)
            assertEquals(event, RuntimeStatusBus.latest)
            assertEquals(listOf(RuntimeEvent.Stopped, event), received)
        } finally {
            RuntimeStatusBus.unsubscribe(listener)
        }
    }

    @Test fun unsubscribeStopsNotificationsAndListenerFailureIsolated() {
        val good = AtomicInteger(0)
        val bad: (RuntimeEvent) -> Unit = { error("listener failure") }
        val listener: (RuntimeEvent) -> Unit = { good.incrementAndGet() }
        RuntimeStatusBus.unsubscribe(bad)
        RuntimeStatusBus.unsubscribe(listener)
        RuntimeStatusBus.subscribe(bad)
        RuntimeStatusBus.subscribe(listener)
        try {
            RuntimeStatusBus.publish(RuntimeEvent.Finished("demo", true))
            assertEquals(1, good.get())
            RuntimeStatusBus.unsubscribe(listener)
            RuntimeStatusBus.publish(RuntimeEvent.Stopped)
            assertEquals(1, good.get())
        } finally {
            RuntimeStatusBus.unsubscribe(bad)
            RuntimeStatusBus.unsubscribe(listener)
        }
    }
}