package com.rk.plugin.server

import java.io.Serializable

data class PluginInfo(
    val icon: String?,
    val title: String,
    val packageName: String,
    val description: String,
    val repo: String,
    val author:String,
    val version:String,
    val versionCode:Int,
    val script:String?,
    val isLocal:Boolean
) : Serializable
