package com.rk.libcommons.editor

import android.content.Context

abstract class BaseLspServer {
    abstract suspend fun isInstalled(context: Context): Boolean
    abstract suspend fun install(context: Context)
    abstract suspend fun command(): Array<String>
    abstract val id: String
    abstract val languageName: String
    abstract val supportedExtensions: List<String>
}