package com.rk.utils

import android.app.Activity
import android.view.KeyEvent

/**
 * Centralized handler for volume key scrolling functionality.
 *
 * This utility provides the shared logic for intercepting volume key events and delegating to the appropriate
 * [VolumeScrollTarget].
 */
object VolumeKeyHandler {

    /** Scroll amount in dp for pixel-based scrolling (Editor) */
    const val SCROLL_AMOUNT_DP = 150

    /** Scroll amount in rows for terminal scrolling */
    const val TERMINAL_SCROLL_ROWS = 5

    /**
     * Checks if a key event should be handled as a volume scroll event.
     *
     * @param event the key event to check
     * @param isEnabled whether volume scrolling is enabled for this context
     * @return true if this is a volume key press that should trigger scrolling
     */
    fun shouldHandle(event: KeyEvent, isEnabled: Boolean): Boolean =
        isEnabled &&
            event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)

    /**
     * Determines the scroll direction from a key code.
     *
     * @param keyCode the key code (VOLUME_UP or VOLUME_DOWN)
     * @return true if scrolling up, false if scrolling down
     */
    fun isUp(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOLUME_UP

    /**
     * Calculates the scroll amount in pixels based on screen density.
     *
     * @param density the display density (from resources.displayMetrics.density)
     * @return the scroll amount in pixels
     */
    fun getScrollAmountPx(density: Float): Int = (SCROLL_AMOUNT_DP * density).toInt()

    /**
     * Extension function for easy integration into any Activity.
     *
     * Call this from dispatchKeyEvent() to handle volume key scrolling.
     *
     * @param event the key event from dispatchKeyEvent
     * @param isEnabled whether volume scrolling is enabled for this context
     * @param getTarget lambda that returns the current scroll target, or null if none
     * @return true if the event was handled, false if not handled, null if not a volume key event
     *
     * Usage:
     * ```kotlin
     * override fun dispatchKeyEvent(event: KeyEvent): Boolean {
     *     handleVolumeKey(event, Settings.enable_volume_scroll_editor) { getCurrentScrollTarget() }
     *         ?.let { return it }
     *     return super.dispatchKeyEvent(event)
     * }
     * ```
     */
    fun Activity.handleVolumeKey(event: KeyEvent, isEnabled: Boolean, getTarget: () -> VolumeScrollTarget?): Boolean? {
        if (!shouldHandle(event, isEnabled)) return null
        val target = getTarget() ?: return false
        return target.scrollByVolume(isUp(event.keyCode))
    }
}
