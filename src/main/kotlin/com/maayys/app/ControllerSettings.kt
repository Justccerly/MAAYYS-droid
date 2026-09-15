package com.maayys.app

import android.content.Context

/** Stores user choice only, never stores or implies authorization. */
class ControllerSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("agent_controller", Context.MODE_PRIVATE)

    fun selectedMode(): ControllerMode? {
        val value = preferences.getString("mode", null) ?: return null
        return ControllerMode.entries.firstOrNull { it.name == value }
    }

    /** Does not change a running agent. Stop and start the service to apply a new selection. */
    fun select(mode: ControllerMode) { preferences.edit().putString("mode", mode.name).apply() }
    fun clear() { preferences.edit().remove("mode").apply() }
}