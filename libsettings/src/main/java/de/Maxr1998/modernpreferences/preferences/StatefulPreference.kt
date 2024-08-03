package de.Maxr1998.modernpreferences.preferences

import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.helpers.DependencyManager

abstract class StatefulPreference(key: String) : Preference(key) {
    internal abstract val state: Boolean

    override fun onAttach() {
        publishState()
    }

    internal fun publishState() = DependencyManager.publishState(this)
}