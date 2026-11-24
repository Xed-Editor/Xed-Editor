package com.rk.store

import androidx.compose.runtime.mutableStateListOf
import com.rk.common.PackageType
import com.rk.extension.api.XedExtensionPoint

/**
 * Registry of the package catalogs the store can display.
 *
 * The store feature registers its built-in sections (themes and icon packs) while other feature modules - e.g.
 * `:features:extensions` - register theirs. Nothing in the store depends on those modules: a section that is never
 * registered simply does not appear, so its packages never disappear from the store when the owning feature is
 * disabled.
 */
object StoreRegistry {
    private val _sections = mutableStateListOf<StoreSection>()

    /** Sections in display order: contributed sections first, then the built-in ones. */
    val sections: List<StoreSection>
        get() = _sections

    @XedExtensionPoint
    fun register(section: StoreSection) {
        if (_sections.none { it.id == section.id }) {
            _sections.add(0, section)
        }
    }

    @XedExtensionPoint
    fun unregister(section: StoreSection) {
        _sections.remove(section)
    }

    fun sectionFor(type: PackageType): StoreSection? = _sections.firstOrNull { it.type == type }

    fun sectionForId(id: String): StoreSection? = _sections.firstOrNull { it.id == id }
}
