package com.rk.extension.api

typealias Registerable = Any

fun interface Disposer<T : Any> {
    fun dispose(target: T)
}

class DisposableManager {

    private val registry = mutableMapOf<Registerable, MutableList<Disposer<*>>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Registerable> register(registerable: T, disposer: Disposer<T>) {
        registry.getOrPut(registerable) { mutableListOf() }.add(disposer as Disposer<*>)
    }

    fun unregister(registerable: Registerable) {
        registry.remove(registerable)
    }

    fun unregister(registerable: Registerable, disposer: Disposer<*>) {
        registry[registerable]?.let { disposers ->
            disposers.remove(disposer)

            if (disposers.isEmpty()) {
                registry.remove(registerable)
            }
        }
    }

    fun onDispose(beforeDispose: (Registerable) -> Unit = {}) {
        registry.forEach { (registerable, disposers) ->
            beforeDispose(registerable)
            disposers.forEach { (it as Disposer<Registerable>).dispose(registerable) }
        }
        registry.clear()
    }
}
