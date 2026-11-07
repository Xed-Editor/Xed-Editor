package com.rk.drawer

import androidx.compose.runtime.Composable
import com.rk.icons.Icon
import java.io.Serializable

/**
 * A tab of the drawer.
 *
 * [DrawerPersistence] saves the tabs in [DrawerViewModel.drawerTabs] with Java serialization. A subclass that can be
 * added there has to declare a `serialVersionUID` and keep only serializable state in its fields (mark anything else
 * `@Transient`), or an update cannot read the tabs that the previous version saved.
 */
abstract class DrawerTab : Serializable {
    @Composable abstract fun Content()

    abstract fun getName(): String

    abstract fun getIcon(): Icon

    open fun isSupported(): Boolean = true

    open fun isEnabled(): Boolean = true

    open fun onAdded() {}

    open fun onRemoved() {}

    open fun onSelect() {}

    open fun onUnselect() {}

    private companion object {
        private const val serialVersionUID = 1L
    }
}

abstract class ServiceTab : Serializable {
    @Composable abstract fun Content(drawerTab: DrawerTab?)

    abstract fun getName(): String

    abstract fun getIcon(): Icon

    open fun isSupported(drawerTab: DrawerTab?): Boolean = true

    open fun isEnabled(drawerTab: DrawerTab?): Boolean = true

    open fun onRegister() {}

    open fun onUnregister() {}

    open fun onSelect(drawerTab: DrawerTab?) {}

    open fun onUnselect(drawerTab: DrawerTab?) {}
}
