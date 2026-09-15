package com.maayys.runtime

import java.util.concurrent.CopyOnWriteArrayList

object RuntimeLog {
    private val lines = ArrayDeque<String>()
    private val observers = CopyOnWriteArrayList<(String) -> Unit>()
    @Synchronized fun snapshot(): String = lines.joinToString("\n")
    fun append(message: String) {
        android.util.Log.i("MaaYYs", message.take(2000))
        synchronized(this) {
            lines.addLast(message.take(2000))
            while (lines.size > 160) lines.removeFirst()
        }
        observers.forEach { runCatching { it(message) } }
    }
    fun subscribe(observer: (String) -> Unit) { observers += observer }
    fun unsubscribe(observer: (String) -> Unit) { observers -= observer }
}
