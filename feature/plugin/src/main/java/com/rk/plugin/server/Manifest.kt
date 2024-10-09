package com.rk.plugin.server

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
data class Manifest(
    val name:String,
    val packageName:String,
    val description:String,
    val author:String,
    val version:String,
    val versionCode:Int,
    val script:String,
    val icon:String
) : Serializable