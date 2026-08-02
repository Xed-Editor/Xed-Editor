package com.rk.extension.api

import com.rk.file.FileObject

/** Interface for handling intents in Xed-Editor. */
@XedExtensionPoint
fun interface IntentHandler {
    /**
     * Handles the given file if it matches the handler's criteria.
     *
     * @return true if the file was handled, false otherwise.
     */
    suspend fun handle(file: FileObject): Boolean
}

object IntentHandleRegistry {
    private val handlers = mutableListOf<IntentHandler>()

    @XedExtensionPoint
    fun register(handler: IntentHandler) {
        handlers.add(handler)
    }

    @XedExtensionPoint
    fun unregister(handler: IntentHandler) {
        handlers.remove(handler)
    }

    suspend fun handleIntent(file: FileObject): Boolean {
        for (handler in handlers) {
            if (handler.handle(file)) {
                return true
            }
        }
        return false
    }
}
