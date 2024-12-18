package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Context
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.ui.activities.settings.alpineDir
import java.io.File
import java.lang.Runtime.getRuntime

class MkRootfs(val context: Context, private val onComplete:()->Unit) {
    private val alpine = File(context.getTempDir(),"alpine.tar.gz")

    init {
        if (alpine.exists().not() || context.alpineDir().listFiles().isNullOrEmpty().not()){
            onComplete.invoke()
        }else{
            initializeInternal()
        }
    }

    private fun initializeInternal(){
        getRuntime().exec("tar -xf ${alpine.absolutePath} -C ${context.alpineDir()}").waitFor()
        alpine.delete()
        with(context.alpineDir()){
            child("etc/hostname").writeText(strings.app_name.getString())
            child("etc/resolv.conf").also { it.createFileIfNot();it.writeText(nameserver) }
            child("etc/hosts").writeText(hosts)
        }
        onComplete.invoke()
    }
}

fun File.child(fileName:String):File {
    return File(this,fileName)
}

fun File.createFileIfNot():File{
    if (exists().not()){
        createNewFile()
    }
    return this
}