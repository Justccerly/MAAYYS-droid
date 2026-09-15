package com.maayys.app

import com.maayys.agent.Controller
import com.maayys.agent.RuntimeTaskController
import com.maayys.agent.TaskController
import com.maayys.resource.ResourceProvider
import com.maayys.runtime.MaaRuntime
import com.maayys.runtime.RuntimeConfig
import java.io.File

class AgentHost(
    private val resources: ResourceProvider,
    private val runtime: MaaRuntime,
    private val listener: com.maayys.runtime.RuntimeEventListener = com.maayys.runtime.RuntimeEventListener { }
) {
    private var taskController: TaskController? = null
    @Synchronized fun start(controller: Controller): Boolean {
        if (taskController != null) return runtime.isRunning()
        val dir = resources.activePath() ?: return false
        if (!runtime.start(RuntimeConfig(dir, controller))) return false
        taskController = RuntimeTaskController(runtime, listener)
        return true
    }
    @Synchronized fun run(task: String, params: Map<String, String> = emptyMap()) = taskController?.start(task, params) ?: false
    @Synchronized fun stop() {
        try {
            val tasks = taskController
            if (tasks != null) tasks.stop() else runtime.stop()
        } finally { taskController = null }
    }
    @Synchronized fun isRunning() = taskController?.isRunning() == true
}