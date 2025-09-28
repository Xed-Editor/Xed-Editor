package com.rk.libcommons.editor

import android.content.Context

abstract class BaseLspServer {
    abstract fun isInstalled(context: Context): Boolean
    abstract fun install(context: Context)
    abstract fun command(): Array<String>
    abstract val id: String
    abstract val languageName: String
    abstract val supportedExtensions: List<String>
}