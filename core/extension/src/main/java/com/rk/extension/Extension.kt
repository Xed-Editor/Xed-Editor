package com.rk.extension

import java.io.File

data class Extension(
    val name:String,
    val packageName:String,
    val mainClass:String,
    val settingsClass:String?,
    val website:String?,
    val author:String,
    val version:String,
    val versionCode:Int,
    val manifest: File,
    val dexFiles: List<File>
){
    override fun hashCode(): Int {
        return manifest.absolutePath.hashCode()
    }
    override fun toString(): String {
        return "Extension : $packageName"
    }
    override fun equals(other: Any?): Boolean {
        if (other !is Extension){return false}
        return packageName == other.packageName
    }
}
