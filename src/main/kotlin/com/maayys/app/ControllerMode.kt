package com.maayys.app

import com.maayys.agent.Controller
import com.maayys.agent.RootController

enum class ControllerMode { ROOT, SHIZUKU, ADB }

/** Factories must validate authorization/connection before returning a controller.
 * Blocking: resolve on the service command worker. Never silently try another mode.
 */
class ControllerSelector(
    private val root: () -> Controller = {
        RootController().also { check(it.requestAuthorization()) { "Root authorization unavailable" } }
    },
    private val shizuku: (() -> Controller)? = null,
    private val adb: (() -> Controller)? = null
) {
    fun create(mode: ControllerMode?): Controller = when (mode) {
        null -> error("Select a controller mode before starting the agent")
        ControllerMode.ROOT -> root()
        ControllerMode.SHIZUKU -> (shizuku ?: error("Shizuku integration is not configured"))()
        ControllerMode.ADB -> (adb ?: error("ADB integration is not configured"))()
    }
}
