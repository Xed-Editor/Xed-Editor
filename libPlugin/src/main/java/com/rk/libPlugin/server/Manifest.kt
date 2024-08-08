package com.rk.libPlugin.server

data class Manifest(
    val name:String,
    val packageName:String,
    val author:String,
    val version:String,
    val versionCode:Int,
    val script:String,
    val icon:String
)