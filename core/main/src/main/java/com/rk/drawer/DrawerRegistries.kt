package com.rk.drawer

import android.app.Activity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import com.rk.icons.Icon

object DrawerOverlayRegistry {
    val overlays = mutableStateListOf<@Composable () -> Unit>()
}

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

object TerminalLauncher {
    // A delegate function to launch the terminal activity dynamically
    var handler: ((
        activity: Activity,
        sandbox: Boolean,
        exe: String,
        args: Array<String>,
        id: String,
        terminatePreviousSession: Boolean,
        workingDir: String?,
        env: Array<String>
    ) -> Unit)? = null

    fun launch(
        activity: Activity,
        sandbox: Boolean = true,
        exe: String,
        args: Array<String> = arrayOf(),
        id: String,
        terminatePreviousSession: Boolean = true,
        workingDir: String? = null,
        env: Array<String> = arrayOf()
    ) {
        handler?.invoke(activity, sandbox, exe, args, id, terminatePreviousSession, workingDir, env)
    }
}

object SandboxedProcessRegistry {
    // A delegate function to spawn a sandboxed PRoot process dynamically
    var provider: (suspend (
        command: List<String>,
        workingDir: String?,
        excludeMounts: List<String>
    ) -> Process)? = null
}
