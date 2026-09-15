package com.maayys.app

import com.maayys.agent.RootController
import com.maayys.resource.ResourceProvider
import com.maayys.runtime.MaaRuntime

/** Call from AgentDependencies.createAgentService only when the user has selected Root mode.
 * The foreground service invokes that factory on its command worker, not the main thread.
 * Runtime remains injected: this factory does not pretend the JNI backend is implemented.
 */
object RootAgentFactory {
    fun create(resources: ResourceProvider, runtime: MaaRuntime): AgentService {
        val controller = RootController()
        check(controller.requestAuthorization()) {
            "Root unavailable, denied, or timed out. Authorize this app in your root manager."
        }
        return AgentService(AgentHost(resources, runtime), controller)
    }
}
