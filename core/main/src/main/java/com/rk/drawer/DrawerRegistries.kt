package com.rk.drawer

import android.app.Activity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import com.rk.icons.Icon


object ServiceTabRegistry {
    val providers = mutableStateListOf<(androidx.lifecycle.ViewModelStoreOwner) -> DrawerTab>()
}

data class AddProjectOption(
    val icon: Icon,
    val titleRes: Int,
    val descriptionRes: Int,
    val onClick: (onDismiss: () -> Unit) -> Unit
)

object AddProjectRegistry {
    val options = mutableStateListOf<AddProjectOption>()
}

