package com.rk.xededitor.ui.screens.terminal

import com.rk.libcommons.alpineDir
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir

fun setupTerminalFiles(){

    with(localBinDir().child("init-host")){
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/init-host.sh").bufferedReader()
                    .use { it.readText() })
        }
    }

    with(localBinDir().child("init")){
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/init.sh").bufferedReader()
                    .use { it.readText() })
        }
    }

    with(alpineDir().child("bin/logger")){
        if (exists().not()) {
            createFileIfNot()
            setExecutable(true)
            writeText(
                application!!.assets.open("terminal/log.sh").bufferedReader()
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


}