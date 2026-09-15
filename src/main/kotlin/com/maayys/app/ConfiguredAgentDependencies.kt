package com.maayys.app

import android.content.Context
import com.maayys.resource.ResourceProvider
import com.maayys.runtime.MaaRuntime

/** Host Application can delegate AgentDependencies to this instance.
 * Runtime factory must create an uninitialized, independently owned runtime.
 */
class ConfiguredAgentDependencies(
    private val resources: ResourceProvider,
    private val runtimeFactory: () -> MaaRuntime,
    private val controllers: ControllerSelector = ControllerSelector(),
    private val listener: com.maayys.runtime.RuntimeEventListener = com.maayys.runtime.RuntimeEventListener { }
) : AgentDependencies {
    override fun createAgentService(context: Context, eventListener: com.maayys.runtime.RuntimeEventListener): AgentService {
        val mode = ControllerSettings(context).selectedMode()
        val controller = controllers.create(mode)
        return AgentService(AgentHost(resources, runtimeFactory(), eventListener), controller)
    }
}