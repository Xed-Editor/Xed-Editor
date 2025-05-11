package com.rk.xededitor.ui.screens.terminal

import android.content.Context
import com.rk.libcommons.alpineDir
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.isMainThread
import com.rk.App.Companion.getTempDir
import java.io.File
import java.lang.Runtime.getRuntime

class MkRootfs(val context: Context, private val onComplete:()->Unit) {
    private val alpine = File(getTempDir(),"alpine.tar.gz")

    init {
        val rootfsFiles = alpineDir().listFiles()?.filter { 
    it.absolutePath != alpineHomeDir().absolutePath && it.absolutePath != alpineDir().child("tmp").absolutePath 
} ?: emptyList()


        if (alpine.exists().not() || rootfsFiles.isEmpty().not()){
            onComplete.invoke()
            println("completed")
        }else{
            initializeInternal()
        }
    }

    private fun initializeInternal(){
        if (isMainThread()){
            throw RuntimeException("IO operation on the main thread")
        }
        getRuntime().exec("tar -xf ${alpine.absolutePath} -C ${alpineDir()}").waitFor()
        alpine.delete()
        with(alpineDir()){
            child("etc/hostname").writeText("Xed-Editor")
            child("etc/resolv.conf").also { it.createFileIfNot();it.writeText(nameserver) }
            child("etc/hosts").writeText(hosts)
        }
        onComplete.invoke()
    }
}