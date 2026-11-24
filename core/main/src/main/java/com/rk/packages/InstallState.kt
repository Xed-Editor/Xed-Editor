package com.rk.packages

/** Progress of a package install/update triggered from the store. */
enum class InstallState {
    Idle,
    Installing,
    Installed,
    Updatable,
    Updating,
}
