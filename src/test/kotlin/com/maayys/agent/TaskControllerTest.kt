package com.maayys.agent

import com.maayys.runtime.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TaskControllerTest {
    private class FakeRuntime(val body: () -> Boolean) : MaaRuntime {
        override fun start(config: RuntimeConfig) = true
        override fun runPipeline(task: String, params: Map<String, String>) = body()
        override fun stop() {}
        override fun isRunning() = true
    }
    @Test fun publishesFalseResultRatherThanSubmissionSuccess() {
        val events = LinkedBlockingQueue<RuntimeEvent>()
        val controller = RuntimeTaskController(FakeRuntime { false }, RuntimeEventListener { events.add(it) })
        try {
            assertTrue(controller.start("task"))
            assertEquals(RuntimeEvent.Started("task"), events.poll(3, TimeUnit.SECONDS))
            assertEquals(RuntimeEvent.Finished("task", false), events.poll(3, TimeUnit.SECONDS))
            assertFalse(controller.isRunning())
        } finally { controller.stop() }
    }
    @Test fun publishesNativeLinkageFailureAndClearsRunning() {
        val events = LinkedBlockingQueue<RuntimeEvent>()
        val controller = RuntimeTaskController(FakeRuntime { throw UnsatisfiedLinkError("missing JNI") }, RuntimeEventListener { events.add(it) })
        try {
            assertTrue(controller.start("task"))
            assertEquals(RuntimeEvent.Started("task"), events.poll(3, TimeUnit.SECONDS))
            assertEquals(RuntimeEvent.Failed("task", "missing JNI"), events.poll(3, TimeUnit.SECONDS))
            assertFalse(controller.isRunning())
        } finally { controller.stop() }
        assertFalse(controller.start("task"))
    }
}