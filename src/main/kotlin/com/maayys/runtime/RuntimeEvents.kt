package com.maayys.runtime

sealed interface RuntimeEvent {
    data class Started(val task: String) : RuntimeEvent
    data class Finished(val task: String, val success: Boolean) : RuntimeEvent
    data class Failed(val task: String, val error: String) : RuntimeEvent
    data object Stopped : RuntimeEvent
}

fun interface RuntimeEventListener { fun onEvent(event: RuntimeEvent) }