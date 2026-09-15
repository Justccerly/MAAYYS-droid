package com.maayys.runtime

import android.content.Context
import android.system.Os
import com.maayys.resource.ProjectCatalog
import com.maayys.resource.CoreUpdateStore
import com.google.gson.JsonParser
import com.sun.jna.*
import java.io.File
import java.util.concurrent.TimeUnit

/** Uses official native MaaFramework and the original MaaYYs Go Agent process.
 * JNA loading follows the approach used by MAA-Meow's MaaCoreManager.
 */
class MaaFrameworkRuntime(context: Context, private val catalog: ProjectCatalog, private val resourceIndex: Int) : MaaRuntime {
    private val context = context.applicationContext
    private val packagedNativeDir = File(this.context.applicationInfo.nativeLibraryDir)
    private val nativeDir: File get() = CoreUpdateStore.activeDirectory(File(context.filesDir, "resources")) ?: packagedNativeDir
    private val taskLock = Any()
    private val stateLock = Any()
    private lateinit var api: MaaFrameworkLibrary
    private lateinit var agentApi: MaaAgentClientLibrary
    private var resource: Pointer? = null
    private var control: Pointer? = null
    private var client: Pointer? = null
    private var tasker: Pointer? = null
    private var stopId: Long? = null
    private var callbacks: FrameworkController? = null
    private var process: Process? = null
    @Volatile private var ready = false
    @Volatile private var stopping = false
    var registeredActions: Set<String> = emptySet()
        private set
    var registeredRecognitions: Set<String> = emptySet()
        private set
    private val event = MaaFrameworkLibrary.Event { _, message, details, _ ->
        RuntimeLog.append("$message $details")
    }

    override fun start(config: RuntimeConfig): Boolean = synchronized(taskLock) {
        check(!ready && !stopping) { "运行时已启动或正在停止" }
        try {
            Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)
            System.setProperty("jna.tmpdir", context.cacheDir.absolutePath)
            NativeLibrary.addSearchPath("MaaFramework", nativeDir.absolutePath)
            NativeLibrary.addSearchPath("MaaAgentClient", nativeDir.absolutePath)
            val opts = mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
            api = Native.load("MaaFramework", MaaFrameworkLibrary::class.java, opts)
            agentApi = Native.load("MaaAgentClient", MaaAgentClientLibrary::class.java, opts)
            RuntimeLog.append("MaaFramework ${api.MaaVersion()}；MaaYYs ${catalog.version}")
            val work = File(context.filesDir, "runtime").apply { mkdirs() }
            val logDir = File(work, "debug").apply { mkdirs() }.absolutePath.toByteArray(Charsets.UTF_8)
            Memory(logDir.size.toLong() + 1).use { memory ->
                memory.write(0, logDir, 0, logDir.size); memory.setByte(logDir.size.toLong(), 0)
                check(api.MaaGlobalSetOption(1, memory, logDir.size.toLong()) != 0.toByte())
            }
            resource = requireNotNull(api.MaaResourceCreate())
            callbacks = FrameworkController(api, config.controller)
            control = requireNotNull(api.MaaCustomControllerCreate(callbacks!!.table, null))
            // MaaYYs is authored for a 720-pixel short side. Framework scales both
            // screenshots and controller input; callbacks use raw device pixels.
            Memory(4).use { value -> value.setInt(0, 720); check(api.MaaControllerSetOption(control, 2, value, 4) != 0.toByte()) }
            check(api.MaaControllerWait(control, api.MaaControllerPostConnection(control)) == 3000) { "控制器连接失败" }
            for (path in catalog.resourcePaths(resourceIndex)) {
                RuntimeLog.append("加载资源：${path.name}")
                check(api.MaaResourceWait(resource, api.MaaResourcePostBundle(resource, path.absolutePath)) == 3000) { "资源加载失败：$path" }
            }
            client = requireNotNull(agentApi.MaaAgentClientCreateV2(null))
            check(agentApi.MaaAgentClientSetTimeout(client, 15_000) != 0.toByte())
            check(agentApi.MaaAgentClientBindResource(client, resource) != 0.toByte())
            val identifierBuffer = requireNotNull(api.MaaStringBufferCreate())
            val identifier = try {
                check(agentApi.MaaAgentClientIdentifier(client, identifierBuffer) != 0.toByte())
                api.MaaStringBufferGet(identifierBuffer)
            } finally { api.MaaStringBufferDestroy(identifierBuffer) }
            val executable = File(nativeDir, "libmaayys_agent.so")
            check(executable.canExecute()) { "缺少当前设备架构的 MaaYYs Agent：$executable" }
            val logFile = File(work, "agent.log")
            process = ProcessBuilder(executable.absolutePath, identifier).directory(work).apply {
                environment().putAll(mapOf(
                    "MAAYYS_NATIVE_LIB_DIR" to nativeDir.absolutePath,
                    "LD_LIBRARY_PATH" to nativeDir.absolutePath,
                    "TMPDIR" to context.cacheDir.absolutePath,
                    "PI_INTERFACE_VERSION" to "2.5.0",
                    "PI_CLIENT_NAME" to "MaaYYs Android",
                    "PI_CLIENT_VERSION" to "0.2.0-dev",
                    "PI_CLIENT_LANGUAGE" to "zh-CN",
                    "PI_CLIENT_MAAFW_VERSION" to api.MaaVersion(),
                    "PI_VERSION" to catalog.version,
                    "PI_CONTROLLER" to "{\"name\":\"Android\",\"type\":\"Custom\"}",
                    "PI_RESOURCE" to catalog.resources[resourceIndex].toString()
                ))
                redirectErrorStream(true); redirectOutput(logFile)
            }.start()
            check(agentApi.MaaAgentClientConnect(client) != 0.toByte()) {
                "MaaYYs Agent 连接失败：${if (logFile.isFile) logFile.readText().takeLast(3000) else "无日志"}"
            }
            fun names(read: (Pointer, Pointer) -> Byte): Set<String> {
                val list = requireNotNull(api.MaaStringListBufferCreate())
                return try {
                    check(read(requireNotNull(resource), list) != 0.toByte())
                    (0L until api.MaaStringListBufferSize(list)).map { api.MaaStringBufferGet(api.MaaStringListBufferAt(list, it)) }.toSet()
                } finally { api.MaaStringListBufferDestroy(list) }
            }
            registeredActions = names(api::MaaResourceGetCustomActionList)
            registeredRecognitions = names(api::MaaResourceGetCustomRecognitionList)
            RuntimeLog.append("已注册 MaaYYs 自定义动作 ${registeredActions.size} 项、识别器 ${registeredRecognitions.size} 项")
            tasker = requireNotNull(api.MaaTaskerCreate())
            check(api.MaaTaskerBindResource(tasker, resource) != 0.toByte())
            check(api.MaaTaskerBindController(tasker, control) != 0.toByte())
            check(api.MaaTaskerInited(tasker) != 0.toByte())
            api.MaaTaskerAddSink(tasker, event, null)
            api.MaaTaskerAddContextSink(tasker, event, null)
            ready = true
            RuntimeLog.append("真实任务运行时已就绪，已连接 MaaYYs Agent")
            true
        } catch (error: Throwable) {
            if (nativeDir != packagedNativeDir) CoreUpdateStore.rollback(File(context.filesDir, "resources"))
            cleanup()
            throw error
        }
    }

    override fun runPipeline(task: String, params: Map<String, String>): Boolean = synchronized(taskLock) {
        check(ready && !stopping) { "运行时未启动" }
        check(agentApi.MaaAgentClientAlive(client) != 0.toByte()) { "MaaYYs Agent 已退出" }
        val definition = catalog.task(task)
        val selections = JsonParser.parseString(params["options"] ?: "{}").asJsonObject
        val overrides = catalog.overrides(definition, selections)
        val id = synchronized(stateLock) {
            check(!stopping) { "任务已取消" }
            api.MaaTaskerPostTask(tasker, definition.get("entry").asString, overrides.toString())
        }
        check(id != 0L) { "任务提交失败：$task" }
        api.MaaTaskerWait(tasker, id) == 3000 && !stopping
    }

    override fun stop() {
        synchronized(stateLock) {
            stopping = true
            if (ready) tasker?.let { stopId = api.MaaTaskerPostStop(it) }
        }
        synchronized(taskLock) { cleanup() }
    }
    private fun cleanup() {
        ready = false
        tasker?.let {
            // PostStop is asynchronous too. Destroying before its completion
            // frees RuntimeCache while the native stop task is still using it.
            val id = stopId ?: api.MaaTaskerPostStop(it)
            if (id != 0L) api.MaaTaskerWait(it, id)
            api.MaaTaskerDestroy(it)
        }; tasker = null; stopId = null
        client?.let { agentApi.MaaAgentClientDisconnect(it); agentApi.MaaAgentClientDestroy(it) }; client = null
        process?.let {
            it.destroy()
            try { if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly() }
            catch (_: InterruptedException) { it.destroyForcibly(); Thread.currentThread().interrupt() }
        }; process = null
        control?.let { api.MaaControllerDestroy(it) }; control = null
        callbacks = null
        resource?.let { api.MaaResourceDestroy(it) }; resource = null
    }
    override fun isRunning() = ready && !stopping
}
