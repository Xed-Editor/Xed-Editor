package de.Maxr1998.modernpreferences.helpers

import android.content.SharedPreferences
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.preferences.StatefulPreference
import java.lang.ref.WeakReference
import java.util.LinkedList

internal object DependencyManager {
    private val preferences = HashMap<PreferenceKey, LinkedList<WeakReference<Preference>>>()
    private val stateCache = HashMap<PreferenceKey, Boolean>()

    /**
     * Register a preference with the manager.
     * If the preference's [dependency][Preference.dependency] field is set,
     * it's added to internal data structures and gets updated with the latest state
     * supplied by the dependency.
     */
    fun register(preference: Preference) {
        val screen = preference.parent
        check(screen != null) { "Preference must be attached to a screen first" }
        val dependency = preference.dependency ?: return
        val key = PreferenceKey(screen.prefs, dependency)
        preferences.getOrPut(key) { LinkedList() }.add(WeakReference(preference))
        stateCache[key]?.let { state -> preference.enabled = state }
    }

    /**
     * Update dependencies of this [StatefulPreference] with the latest state.
     */
    fun publishState(preference: StatefulPreference) {
        val screen = preference.parent
        check(screen != null) { "Preference must be attached to a screen first" }
        val key = PreferenceKey(screen.prefs, preference.key)
        val state = preference.state // Cache state so that every dependent gets the same value
        stateCache[key] = state
        preferences[key]?.forEach { it.get()?.enabled = state }
    }

    private data class PreferenceKey(val preferenceStore: SharedPreferences?, val key: String)
}