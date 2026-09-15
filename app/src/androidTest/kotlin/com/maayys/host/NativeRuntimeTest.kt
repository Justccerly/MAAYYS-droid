package com.maayys.host

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maayys.agent.Controller
import com.maayys.agent.ScreenshotProvider
import com.maayys.resource.ProjectCatalog
import com.maayys.runtime.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Device test of native loading, controller callbacks, IPC and the actual Go
 * RandomWait runner. The image fixture does not prove game recognition or input.
 */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeTest {
    @Test fun loadsBundledOnmyojiResourcesAndRegistersEveryUpstreamRunner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as MaaApplication
        val catalog = BundledProject.prepare(context, app.resourcesProvider)
        val controller = object : Controller {
            override fun click(x: Int, y: Int) = false
            override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) = false
        }
        val runtime = MaaFrameworkRuntime(context, catalog, 0)
        try {
            assertTrue(runtime.start(RuntimeConfig(catalog.root, controller)))
            assertEquals(25, runtime.registeredActions.size)
            assertEquals(6, runtime.registeredRecognitions.size)
            assertTrue(runtime.registeredActions.containsAll(listOf("SwitchSoul", "BattleTeam", "AutoBattle", "RandomTouch")))
            assertTrue(runtime.registeredRecognitions.contains("TaskCounterRecognition"))
        } finally { runtime.stop() }
    }

    @Test fun officialFrameworkRunsOriginalGoAgentAndReleasesResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "native-smoke-${System.nanoTime()}").apply { mkdirs() }
        val pipeline = File(directory, "resource/pipeline").apply { mkdirs() }
        File(directory, "interface.json").writeText("""{
          "version":"device-test", "resource":[{"name":"fixture","path":["resource"]}],
          "task":[{"name":"upstream-go-wait","entry":"UpstreamGoWait"}]
        }""")
        File(pipeline, "smoke.json").writeText("""{
          "UpstreamGoWait":{"recognition":"DirectHit","action":"Custom",
            "custom_action":"RandomWait","custom_action_param":{"min":0.001,"max":0.001}}
        }""")
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().use { bytes -> image.compress(Bitmap.CompressFormat.PNG, 100, bytes); bytes.toByteArray() }
        image.recycle()
        var screenshots = 0
        val controller = object : Controller, ScreenshotProvider {
            override fun click(x: Int, y: Int): Boolean = error("Smoke test must not inject input")
            override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long): Boolean = error("Smoke test must not inject input")
            override fun screenshot(): ByteArray { screenshots++; return png }
        }
        val runtime = MaaFrameworkRuntime(context, ProjectCatalog(directory), 0)
        try {
            assertTrue(runtime.start(RuntimeConfig(directory, controller)))
            assertTrue(runtime.runPipeline("upstream-go-wait"))
            assertTrue("Native pipeline should request an image through the Android callback", screenshots > 0)
        } finally {
            runtime.stop()
            directory.deleteRecursively()
        }
        assertFalse(runtime.isRunning())
    }
}
