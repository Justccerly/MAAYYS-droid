package com.maayys.app

import android.content.Context

/** Implement on the host Application; never fall back to a fake native runtime. */
interface AgentDependencies {
    fun createAgentService(context: Context, listener: com.maayys.runtime.RuntimeEventListener = com.maayys.runtime.RuntimeEventListener { }): AgentService
}
