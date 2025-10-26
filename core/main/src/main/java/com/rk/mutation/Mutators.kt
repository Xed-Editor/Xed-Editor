package com.rk.mutation

import androidx.compose.runtime.mutableStateListOf
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localDir
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