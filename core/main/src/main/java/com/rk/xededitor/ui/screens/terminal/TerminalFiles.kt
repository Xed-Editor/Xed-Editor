package com.rk.xededitor.ui.screens.terminal

import android.system.Os
import com.rk.file.sandboxDir
import com.rk.libcommons.application
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localBinDir
import com.rk.file.localDir

fun setupTerminalFiles(){

    with(localBinDir().child("init")){
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/init.sh").bufferedReader()
                    .use { it.readText() })
        }
    }

    with(localBinDir().child("sandbox")){
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/sandbox.sh").bufferedReader()
                    .use { it.readText() })
        }
    }


    with(localDir().child("stat")){
        if (exists().not()){
            createFileIfNot()
            writeText(stat)
        }
    }

    with(localDir().child("vmstat")){
        if (exists().not()){
            createFileIfNot()
            writeText(vmstat)
        }
    }



    val groupFile = sandboxDir().child("etc/group")
    val aid = Os.getgid()
    val linesToAdd = listOf(
        "inet:x:3003",
        "everybody:x:9997",
        "android_app:x:20455",
        "android_debug:x:50455",
        "android_cache:x:${10000 + aid}",
        "android_storage:x:${40000 + aid}",
        "android_media:x:${50000 + aid}",
        "android_external_storage:x:1077"
    )

    val existing = if (groupFile.exists()) {
        groupFile.readText()
    } else {
        ""
    }

    for (line in linesToAdd) {
        val gid = line.substringAfterLast(":")
        if (!existing.contains(":$gid")) {
            groupFile.appendText("$line\n")
        }
    }



}