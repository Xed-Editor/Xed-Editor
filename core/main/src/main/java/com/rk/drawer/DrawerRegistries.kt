package com.rk.drawer

import androidx.lifecycle.ViewModelStoreOwner
import com.rk.icons.Icon

object ServiceTabRegistry {

    private val providers = mutableListOf<ServiceTabProvider>()

    fun register(provider: ServiceTabProvider) {
        providers += provider
    }

    fun unregister(provider: ServiceTabProvider) {
        providers -= provider
    }

    internal fun createAll(owner: ViewModelStoreOwner): List<DrawerTab> {
        return providers.map { it.create(owner) }
    }
}

fun interface ServiceTabProvider {
    fun create(owner: ViewModelStoreOwner): DrawerTab
}

enum class AddProjectCategory {
    STORAGE,
    CREATE,
    OTHER,
}

data class AddProjectOption(
    val icon: Icon,
    val title: String,
    val description: String,
    val category: AddProjectCategory = AddProjectCategory.OTHER,
    val onClick: (onDismiss: () -> Unit) -> Unit,
)

object AddProjectRegistry {

    private val _options = mutableListOf<AddProjectOption>()

    val options
        get() = _options.toList()

    fun register(option: AddProjectOption) {
        _options += option
    }

    fun unregister(option: AddProjectOption) {
        _options -= option
    }
}
