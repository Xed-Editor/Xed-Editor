package com.rk.xededitor.ui.screens.settings.mutators

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localDir
import com.rk.resources.drawables
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import java.io.File

object Mutators {

    data class Mutator(val file: File) {
        val name: String
            get() = file.nameWithoutExtension
        var script: String
            get(){
                return file.readText()
            }
            set(value) {
                file.writeText(value)
            }

        override fun equals(other: Any?): Boolean {
            if (other !is Mutator) return false
            return other.file.absolutePath == file.absolutePath
        }

        override fun hashCode(): Int {
            return file.hashCode()
        }
    }

    val mutators = mutableStateListOf<Mutator>()

    private fun getMutatorDirectory(): File {
        val dir = File(localDir(), "mutators")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }


    fun updateMutators() {
        val dir = getMutatorDirectory()
        mutators.clear()

        dir.listFiles()?.forEach {
            if (it.extension == "mut"){
                mutators.add(Mutator(it))
            }
        }
    }

    fun createMutator(name: String,script: String) {
        val file = getMutatorDirectory().child("$name.mut").createFileIfNot()
        file.writeText(script)
        val mutator = Mutator(file)
        mutators.add(mutator)
    }

    fun deleteMutator(mutator: Mutator) {
        mutators.remove(mutator)
        val file = mutator.file
        if (file.exists()) {
            file.delete()
        }
    }

}
