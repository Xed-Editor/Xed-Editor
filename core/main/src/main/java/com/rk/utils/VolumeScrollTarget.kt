package com.rk.utils

/**
 * Interface for components that can be scrolled using volume keys.
 *
 * Implementations should handle the actual scrolling mechanism, which may differ between components (e.g., pixel-based
 * for editors, row-based for terminals, state-based for Compose UIs).
 */
interface VolumeScrollTarget {
    /**
     * Scrolls the component by a predefined amount.
     *
     * @param up true to scroll up (towards the beginning), false to scroll down
     * @return true if scrolling was performed, false if not possible (e.g., no content)
     */
    fun scrollByVolume(up: Boolean): Boolean
}
