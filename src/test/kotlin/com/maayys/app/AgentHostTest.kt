package com.maayys.app

import com.maayys.agent.Controller
import com.maayys.resource.ResourceProvider
import com.maayys.runtime.*
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentHostTest {
    @get:Rule val temp = TemporaryFolder()
    private val controller = object : Controller {
        override fun click(x: Int, y: Int) = true
        override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = true
    }
    private class Runtime : MaaRuntime {
        var starts = 0
        var ready = false
        override fun start(config: RuntimeConfig): Boolean { starts++; ready = true; return true }
        override fun runPipeline(task: String, params: Map<String, String>) = true
        override fun stop() { ready = false }
        override fun isRunning() = ready
    }
    @Test fun hostForwardsTaskEventsAndSupportsRestartAfterStop() {
        val root = temp.newFolder()
        File(root, "resource-versions/v1").mkdirs()
        File(root, "active.json").writeText("{\"version\":\"v1\"}")
        val runtime = Runtime()
        val events = LinkedBlockingQueue<RuntimeEvent>()
        val host = AgentHost(ResourceProvider(root), runtime, RuntimeEventListener { events.add(it) })
        try {
            assertTrue(host.start(controller))
            assertTrue(host.start(controller))
            assertEquals(1, runtime.starts)
            assertTrue(host.run("example"))
            assertEquals(RuntimeEvent.Started("example"), events.poll(3, TimeUnit.SECONDS))
            assertEquals(RuntimeEvent.Finished("example", true), events.poll(3, TimeUnit.SECONDS))
            host.stop()
            assertFalse(host.run("example"))
            assertTrue(host.start(controller))
            assertEquals(2, runtime.starts)
        } finally { host.stop() }
    }
    @Test fun missingResourcesDoNotInitializeRuntime() {
        val runtime = Runtime()
        val host = AgentHost(ResourceProvider(temp.newFolder()), runtime)
        assertFalse(host.start(controller))
        assertEquals(0, runtime.starts)
        assertFalse(host.run("example"))
    }
}