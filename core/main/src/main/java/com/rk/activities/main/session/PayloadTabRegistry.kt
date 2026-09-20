package com.rk.activities.main.session

import com.rk.tabs.base.Tab
import com.rk.utils.logError

/**
 * Restores a tab from the opaque bytes a [PayloadTabState] carried through the session file.
 *
 * A factory is keyed by the same [PayloadTabState.type] string that was saved, so a feature can
 * decide for itself how its tab is written and read, as long as the key stays stable.
 */
fun interface PayloadTabFactory {
    fun restore(payload: ByteArray): Tab?
}

/**
 * The restore side of tab sessions for tabs whose state does not fit the core's own states.
 *
 * [TabState] is a sealed hierarchy, so a feature that lives outside `core/main` cannot add a member
 * to it. [PayloadTabState] instead carries the tab's state as opaque bytes tagged with a type, and
 * the type is looked up here at restore time. That keeps the session format open for extension
 * without the core having to know every tab type, and without `Serializable` having to cope with
 * types it was never compiled against.
 *
 * Registration is idempotent, so a feature may call it from `init` every time the feature is enabled.
 */
object PayloadTabRegistry {
    private val factories = mutableMapOf<String, PayloadTabFactory>()

    /** Registers [factory] under [type], replacing any previous factory for that type. */
    fun register(type: String, factory: PayloadTabFactory) {
        synchronized(factories) { factories[type] = factory }
    }

    fun unregister(type: String) {
        synchronized(factories) { factories.remove(type) }
    }

    /**
     * Rebuilds the tab saved as [PayloadTabState], or null when its type is unknown or its factory
     * refuses the payload. A tab that cannot be restored is dropped rather than failing the session.
     */
    fun restore(type: String, payload: ByteArray): Tab? {
        val factory = synchronized(factories) { factories[type] } ?: return null
        return runCatching { factory.restore(payload) }
            .onFailure { logError(it) }
            .getOrNull()
    }
}
