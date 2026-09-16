package com.maayys.host

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import com.maayys.app.*
import com.maayys.platform.*
import com.maayys.resource.ProjectCatalog
import com.maayys.resource.MirrorChyanClient
import com.maayys.resource.GitHubUpdateClient
import com.maayys.resource.GitHubAsset
import com.maayys.resource.MirrorUpdate
import com.maayys.resource.CoreUpdateStore
import com.maayys.runtime.*
import kotlinx.coroutines.*
import java.io.File

class AppModel(app: Application) : AndroidViewModel(app) {
    private val context = app as MaaApplication
    private val prefs = context.getSharedPreferences("project", Application.MODE_PRIVATE)
    var catalog by mutableStateOf<ProjectCatalog?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null)
    var running by mutableStateOf(false); private set
    var status by mutableStateOf("正在准备资源"); private set
    var currentTask by mutableStateOf<String?>(null); private set
    var logs by mutableStateOf(RuntimeLog.snapshot()); private set
    var resourceIndex by mutableIntStateOf(prefs.getInt("resource", 0)); private set
    var mode by mutableStateOf(ControllerSettings(context).selectedMode()); private set
    var backgroundMode by mutableStateOf(prefs.getBoolean("background", false)); private set
    var backgroundActive by mutableStateOf(BackgroundConnection.display > 0); private set
    var backgroundBusy by mutableStateOf(false); private set
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system"); private set
    var tab by mutableIntStateOf(0)
    var schedules by mutableStateOf(ScheduleStore.load(context)); private set
    var scheduleDays by mutableStateOf(prefs.getString("schedule_days", "2,3,4,5")!!.split(',').mapNotNull(String::toIntOrNull).toSet()); private set
    var scheduleMinutes by mutableStateOf(prefs.getString("schedule_times", "480,720,1080")!!.split(',').mapNotNull(String::toIntOrNull).toSet()); private set
    var taskToConfigure by mutableStateOf<JsonObject?>(null)
    var selections by mutableStateOf(JsonObject()); private set
    var savedTaskName by mutableStateOf(prefs.getString("saved_task", null)); private set
    var showLogs by mutableStateOf(false)
    private var pendingTaskQueue = mutableListOf<String>()
    var resourceUpdateBusy by mutableStateOf(false); private set
    var coreUpdateBusy by mutableStateOf(false); private set
    var updateMessage by mutableStateOf<String?>(null); private set
    // CDK is session-only; Mirror酱 advises against persisting it in plaintext.
    var mirrorCdk by mutableStateOf("")
    var updateSource by mutableStateOf(prefs.getString("update_source", "mirror") ?: "mirror")
    var coreUpdateMessage by mutableStateOf<String?>(null); private set
    val channel: String get() = catalog?.resources?.getOrNull(resourceIndex)?.let { it.get("label")?.asString ?: it.get("name").asString } ?: "正在加载"
    private val logListener: (String) -> Unit = { viewModelScope.launch { logs = RuntimeLog.snapshot().takeLast(24000) } }
    private val backgroundListener: () -> Unit = { viewModelScope.launch { backgroundActive = BackgroundConnection.display > 0 } }
    private val statusListener: (RuntimeEvent) -> Unit = { event -> viewModelScope.launch {
        when (event) {
            is RuntimeEvent.Started -> { running = true; currentTask = event.task; status = "任务执行中：${event.task}" }
            is RuntimeEvent.Finished -> {
                if (pendingTaskQueue.isNotEmpty()) {
                    val next = pendingTaskQueue.removeAt(0)
                    startSingleTaskByName(next)
                } else {
                    running = false
                    status = if (event.success) "全部任务已完成" else "任务未完成"
                    if (!event.success) error = "${event.task}未完成，请查看运行日志。"
                }
            }
            is RuntimeEvent.Failed -> {
                pendingTaskQueue.clear()
                running = false
                status = "运行遇到问题"
                error = event.error
            }
            RuntimeEvent.Stopped -> {
                pendingTaskQueue.clear()
                running = false
                status = "任务已停止"
            }
        }
    } }
    init {
        RuntimeStatusBus.subscribe(statusListener); RuntimeLog.subscribe(logListener); BackgroundConnection.subscribe(backgroundListener)
        viewModelScope.launch {
            try {
                catalog = withContext(Dispatchers.IO) { BundledProject.prepare(context, context.resourcesProvider) }
                resourceIndex = resourceIndex.coerceIn(catalog!!.resources.indices)
                if (!running) status = "等待开始"
            } catch (e: Exception) { error = e.message; status = "资源加载失败" }
            finally { loading = false }
        }
    }
    fun selectChannel(index: Int) { if (!running && !backgroundActive) { resourceIndex = index; prefs.edit().putInt("resource", index).apply() } }
    fun selectMode(selected: ControllerMode) { if (!running && !backgroundActive) { mode = selected; ControllerSettings(context).select(selected) } }
    fun selectTheme(value: String) { theme = value; prefs.edit().putString("theme", value).apply() }
    fun saveMirrorCdk(value: String) { mirrorCdk = value.trim() }
    fun selectUpdateSource(value: String) { updateSource = value; prefs.edit().putString("update_source", value).apply() }
    fun checkMirrorUpdate() {
        val project = catalog ?: return
        if (resourceUpdateBusy) return
        val source = updateSource
        val cdk = mirrorCdk
        resourceUpdateBusy = true; updateMessage = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (source == "github") {
                        GitHubUpdateClient("TanyaShue/MaaYYs").latestAsset { it.contains("win-x86_64") && it.endsWith(".zip") }
                            ?.let { MirrorUpdate(it.version, it.url, null) } ?: error("GitHub 没有找到 MaaYYs 资源包")
                    } else MirrorChyanClient(cdk).latest(project.version)
                }
                if (result.version == project.version || result.version.isBlank() || compareVersions(project.version, result.version) >= 0) updateMessage = "当前已是最新资源：${project.version}"
                else updateMessage = "发现新资源 ${result.version}，正在下载…"
                // Never install a remote version older than the active MaaYYs resource.
                if (compareVersions(project.version, result.version) < 0 && !result.url.isNullOrBlank()) {
                    val file = File.createTempFile("mirrorchyan-", ".zip", context.cacheDir)
                    try {
                        val manifest = withContext(Dispatchers.IO) {
                            if (source == "github") GitHubUpdateClient("TanyaShue/MaaYYs").download(GitHubAsset("MaaYYs-resource.zip", result.version, requireNotNull(result.url)), file)
                            else MirrorChyanClient(cdk).download(result, file)
                        }
                        withContext(Dispatchers.IO) { context.resourcesProvider.installDownloadedArchive(file, manifest) }
                        updateMessage = "资源 ${result.version} 已安装，重启应用后生效。"
                    } finally { file.delete() }
                }
            } catch (e: Exception) { updateMessage = e.message ?: "Mirror酱更新失败"; RuntimeLog.append("Mirror酱：${e.stackTraceToString()}") }
            finally { resourceUpdateBusy = false }
        }
    }
    fun checkCoreUpdate() {
        if (coreUpdateBusy) return
        val source = updateSource
        val cdk = mirrorCdk
        coreUpdateBusy = true; coreUpdateMessage = null
        viewModelScope.launch {
            try {
                val abi = if (android.os.Build.SUPPORTED_ABIS.firstOrNull() == "x86_64") "x86_64" else "aarch64"
                val asset = withContext(Dispatchers.IO) {
                    if (source == "github") GitHubUpdateClient("MaaXYZ/MaaFramework").latestAsset { it.startsWith("MAA-android-$abi-") && it.endsWith(".zip") }
                    else {
                        val update = MirrorChyanClient(cdk, "MaaYYs-Android-Core", "MaaFramework", "android", if (abi == "aarch64") "arm64-v8a" else "x86_64").latest("v5.13.0")
                        update.url?.let { GitHubAsset("MaaFramework-${update.version}.zip", update.version, it) }
                    }
                } ?: error("没有找到当前 ABI 的 MaaFramework Core 更新")
                if (asset.version == "v5.13.0") coreUpdateMessage = "当前 Core 已是最新：MaaFramework 5.13.0"
                else {
                    val file = File.createTempFile("maa-core-", ".zip", context.cacheDir)
                    try {
                        val manifest = withContext(Dispatchers.IO) { GitHubUpdateClient("MaaXYZ/MaaFramework").download(asset, file) }
                        withContext(Dispatchers.IO) { CoreUpdateStore.stage(File(context.filesDir, "resources"), manifest, file) }
                        coreUpdateMessage = "MaaFramework ${asset.version} 已暂存，停止任务并重启应用后生效。"
                    } finally { file.delete() }
                }
            } catch (e: Exception) {
                coreUpdateMessage = if (source == "mirror") {
                    "Mirror酱暂无当前 ABI 的 Core 包，请切换 GitHub"
                } else {
                    "GitHub 暂无当前 ABI 的 MaaFramework Core 包"
                }
                RuntimeLog.append("Core更新失败：${e.javaClass.simpleName}（详细地址仅记录在调试日志）")
            }
            finally { coreUpdateBusy = false }
        }
    }
    private fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) { val c = (x.getOrNull(i) ?: 0).compareTo(y.getOrNull(i) ?: 0); if (c != 0) return c }
        return 0
    }
    fun setBackground(value: Boolean) { if (!running) { backgroundMode = value; prefs.edit().putBoolean("background", value).apply() } }
    var tasksEnabledState by mutableStateOf<Map<String, Boolean>>(emptyMap()); private set
    fun toggleTaskEnabled(task: String, value: Boolean) {
        prefs.edit().putBoolean("enabled:$task", value).apply()
        tasksEnabledState = tasksEnabledState + (task to value)
        schedules = ScheduleStore.load(context)
    }
    fun taskEnabled(task: String): Boolean = tasksEnabledState[task] ?: prefs.getBoolean("enabled:$task", true)
    fun saveSchedule(entry: ScheduleEntry) { ScheduleStore.save(context, (schedules.filterNot { it.task == entry.task } + entry)); schedules = ScheduleStore.load(context) }
    fun setSchedule(days: Set<Int>, minutes: Set<Int>) {
        scheduleDays = days; scheduleMinutes = minutes
        prefs.edit().putString("schedule_days", days.sorted().joinToString(",")).putString("schedule_times", minutes.sorted().joinToString(",")).apply()
        val entries = catalog?.tasks.orEmpty().filter { taskEnabled(it.get("name").asString) }.map { ScheduleEntry(it.get("name").asString, days, minutes, true) }
        ScheduleStore.save(context, entries); schedules = ScheduleStore.load(context)
    }
    fun configure(task: JsonObject) {
        taskToConfigure = task
        selections = runCatching { JsonParser.parseString(prefs.getString("options:${task.get("name").asString}", "{}")).asJsonObject }.getOrDefault(JsonObject())
    }
    fun savedTask(): JsonObject? {
        savedTaskName?.let { name ->
            catalog?.tasks?.firstOrNull { it.get("name").asString == name }?.let { return it }
        }
        val firstEnabled = catalog?.tasks?.firstOrNull { taskEnabled(it.get("name").asString) }
        if (firstEnabled != null) return firstEnabled
        return catalog?.tasks?.firstOrNull()
    }
    fun choose(name: String, value: JsonElement) {
        selections = selections.deepCopy().apply { add(name, value) }
        taskToConfigure?.get("name")?.asString?.let { prefs.edit().putString("options:$it", selections.toString()).apply() }
    }
    fun startSingleTaskByName(name: String) {
        val project = catalog ?: return
        val task = project.tasks.firstOrNull { it.get("name").asString == name } ?: return
        try {
            require(mode != null) { "请先在设置中选择 Root 或 Shizuku 控制方式。" }
            require(mode != ControllerMode.SHIZUKU || backgroundMode) { "Shizuku 当前用于后台模式，请开启后台运行。" }
            val optionsJson = runCatching { JsonParser.parseString(prefs.getString("options:$name", "{}")).asJsonObject }.getOrDefault(JsonObject())
            project.overrides(task, optionsJson)
            if (backgroundMode) context.startForegroundService(Intent(context, BackgroundKeepAliveService::class.java))
            val intent = Intent(context, MaaAgentForegroundService::class.java).setAction(MaaAgentForegroundService.ACTION_RUN_TASK)
                .putExtra(MaaAgentForegroundService.EXTRA_TASK, name)
                .putExtra(MaaAgentForegroundService.EXTRA_PARAMS, Bundle().apply { putString("options", optionsJson.toString()) })
            currentTask = name; running = true; status = "正在连接运行环境：$name"
            savedTaskName = name
            prefs.edit().putString("saved_task", savedTaskName).apply()
            context.startForegroundService(intent)
        } catch (e: Exception) {
            error = e.message; running = false; pendingTaskQueue.clear()
        }
    }
    fun startTask() {
        val task = taskToConfigure ?: return
        pendingTaskQueue.clear()
        startSingleTaskByName(task.get("name").asString)
        taskToConfigure = null
    }
    fun stopTask() {
        pendingTaskQueue.clear()
        status = "正在停止任务"
        if (!context.stopService(Intent(context, MaaAgentForegroundService::class.java))) { running = false; status = "等待开始" }
    }
    fun startSavedTask() {
        val project = catalog ?: run { error = "资源正在加载中，请稍候"; return }
        val enabledTasks = project.tasks.map { it.get("name").asString }.filter { taskEnabled(it) }
        if (enabledTasks.isEmpty()) {
            error = "请先在任务分页中开启至少一个任务开关。"
            tab = 1
            return
        }
        pendingTaskQueue.clear()
        if (enabledTasks.size > 1) {
            pendingTaskQueue.addAll(enabledTasks.drop(1))
        }
        startSingleTaskByName(enabledTasks.first())
    }
    fun openBackground() {
        val project = catalog ?: return
        val selected = mode ?: run { error = "请先在设置中选择控制方式。"; return }
        if (backgroundBusy) return
        backgroundBusy = true; setBackground(true)
        context.startForegroundService(Intent(context, BackgroundKeepAliveService::class.java))
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val remote = BackgroundConnection.connect(context, selected.name)
                    check(remote.launch(project.gamePackage(resourceIndex))) { "阴阳师未能进入后台显示器，请查看日志。" }
                }
                backgroundActive = true
            } catch (e: Exception) {
                error = e.message; RuntimeLog.append(e.stackTraceToString())
                withContext(Dispatchers.IO) { BackgroundConnection.close() }
                context.stopService(Intent(context, BackgroundKeepAliveService::class.java))
            } finally { backgroundBusy = false }
        }
    }
    fun closeBackground(bringToFront: Boolean = false) {
        stopTask(); backgroundBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (bringToFront) {
                        val pkg = catalog?.gamePackage(resourceIndex) ?: error("资源未就绪")
                        check(BackgroundConnection.remote?.bringToFront(pkg) == true) { "游戏未能返回前台" }
                    }
                    BackgroundConnection.close()
                }
                backgroundActive = false
                context.stopService(Intent(context, BackgroundKeepAliveService::class.java))
            } catch (e: Exception) { error = e.message }
            finally { backgroundBusy = false }
        }
    }
    fun authorizeShizuku() { runCatching { BackgroundConnection.requestShizuku() }.onFailure { error = it.message } }
    override fun onCleared() { RuntimeStatusBus.unsubscribe(statusListener); RuntimeLog.unsubscribe(logListener); BackgroundConnection.unsubscribe(backgroundListener) }
}
