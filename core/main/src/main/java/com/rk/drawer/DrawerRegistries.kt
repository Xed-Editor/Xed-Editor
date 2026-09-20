package com.rk.drawer

import com.rk.icons.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ServiceTabRegistry {

    private val _tabs = MutableStateFlow<List<DrawerTab>>(emptyList())
    val tabs = _tabs.asStateFlow()

    fun register(tab: DrawerTab) {
        _tabs.update { it + tab }
        tab.onAdded()
    }

    fun unregister(tab: DrawerTab) {
        _tabs.update { it - tab }
        tab.onRemoved()
    }
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

    private val _options = MutableStateFlow<List<AddProjectOption>>(emptyList())
    val options = _options.asStateFlow()

    fun register(option: AddProjectOption) {
        _options.update { it + option }
    }

    fun unregister(option: AddProjectOption) {
        _options.update { it - option }
    }
}
