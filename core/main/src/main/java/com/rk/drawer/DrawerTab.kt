package com.rk.drawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    @Composable abstract fun Content(modifier: Modifier)

    abstract fun getName(): String

    abstract fun getIcon(): Icon

    open fun isSupported(): Boolean = true

    open fun isEnabled(): Boolean = true

    open fun onAdded() {}

    open fun onRemoved() {}

    private companion object {
        private const val serialVersionUID = 1L
    }
}
