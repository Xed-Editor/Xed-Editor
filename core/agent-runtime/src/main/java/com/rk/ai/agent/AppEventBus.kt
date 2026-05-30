package com.rk.ai.agent

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
}

class AppEventBus {
    private val listeners = mutableListOf<(AppEvent) -> Unit>()

    fun onEvent(listener: (AppEvent) -> Unit) {
        listeners.add(listener)
    }

    fun emit(event: AppEvent) {
        listeners.forEach { it(event) }
    }

    fun removeListener(listener: (AppEvent) -> Unit) {
        listeners.remove(listener)
    }
}
