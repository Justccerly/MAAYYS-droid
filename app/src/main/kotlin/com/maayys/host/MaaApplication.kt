package com.maayys.host

import android.app.Application
import android.content.Context
import com.maayys.app.AgentDependencies
import com.maayys.app.AgentService
import com.maayys.resource.ResourceProvider
import java.io.File

class MaaApplication : Application(), AgentDependencies {
    val resourcesProvider by lazy { ResourceProvider(File(filesDir, "resources")) }

    override fun createAgentService(context: Context, listener: com.maayys.runtime.RuntimeEventListener): AgentService {
        val catalog = BundledProject.prepare(this, resourcesProvider)
        val index = getSharedPreferences("project", MODE_PRIVATE).getInt("resource", 0)
        require(index in catalog.resources.indices) { "请重新选择游戏区服" }
        val mode = com.maayys.app.ControllerSettings(this).selectedMode()
        val controller = if (getSharedPreferences("project", MODE_PRIVATE).getBoolean("background", false)) {
            val remote = com.maayys.platform.BackgroundConnection.connect(this, requireNotNull(mode) { "请选择控制方式" }.name)
            check(remote.launch(catalog.gamePackage(index))) { "游戏未能进入独立显示器" }
            com.maayys.agent.MeowController(remote)
        } else com.maayys.app.ControllerSelector().create(mode)
        val runtime = com.maayys.runtime.MaaFrameworkRuntime(this, catalog, index)
        return AgentService(com.maayys.app.AgentHost(resourcesProvider, runtime, listener), controller)
    }
}
