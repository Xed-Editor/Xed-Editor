package com.rk.ai.service

import java.io.File

interface FileOpener {
    fun openFileInEditor(file: File, switchToTab: Boolean = true)
}
