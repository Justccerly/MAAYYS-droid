package com.maayys.app

import com.maayys.agent.Controller

class AgentService(private val host: AgentHost, private val controller: Controller) {
    fun start(): Boolean = host.start(controller)
    fun run(task: String, params: Map<String, String> = emptyMap()): Boolean = host.run(task, params)
    fun stop() = host.stop()
    fun isRunning() = host.isRunning()
}