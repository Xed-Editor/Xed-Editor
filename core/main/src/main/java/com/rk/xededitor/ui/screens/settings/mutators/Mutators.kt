package com.rk.xededitor.ui.screens.settings.mutators

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.rk.libcommons.application
import com.rk.libcommons.localDir
import com.rk.resources.drawables
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import java.io.File

object Mutators {
    data class Mutator(val name: String, var script: String) {
        override fun equals(other: Any?): Boolean {
            if (other !is Mutator) return false
            return other.name + other.script == name + script
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    private const val DIRECTORY_NAME = "mutators"
    private val gson = Gson()
    private val mutators = mutableStateListOf<Mutator>()

    private fun getMutatorDirectory(): File {
        val dir = File(localDir(), DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMutatorFile( mutator: Mutator): File {
        val dir = getMutatorDirectory()
        return File(dir, "${mutator.name}.json")
    }

    fun loadMutators() {
        val dir = getMutatorDirectory()
        mutators.clear()

        dir.listFiles()?.forEach { file ->
            if (file.extension == "json") {
                val json = file.readText()
                val mutator = gson.fromJson(json, Mutator::class.java)
                mutators.add(mutator)
            }
        }

        val assetManager = application!!.assets
        try {
            val assetFiles = assetManager.list("mutators") ?: emptyArray()
            assetFiles.forEach { assetFile ->
                if (assetFile.endsWith(".json")) {
                    val json = assetManager.open("mutators/$assetFile").bufferedReader().use { it.readText() }
                    val mutator = gson.fromJson(json, Mutator::class.java)
                    mutators.add(mutator)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun saveMutator(mutator: Mutator) {
        val file = getMutatorFile(mutator)
        val json = gson.toJson(mutator)
        file.writeText(json)
    }

    fun deleteMutatorFile( mutator: Mutator) {
        val file = getMutatorFile(mutator)
        if (file.exists()) {
            file.delete()
        }
    }

    fun getMutators(): SnapshotStateList<Mutator> = mutators

    fun createMutator(mutator: Mutator) {
        mutators.add(mutator)
        saveMutator(mutator)

        MainActivity.activityRef.get()?.apply {
            val tool = ContextCompat.getDrawable(this, drawables.build)
            menu?.findItem(R.id.tools)?.subMenu?.add(0, mutator.hashCode(), toolItems.size, mutator.name)?.icon = tool
        }
    }

    fun deleteMutator(mutator: Mutator) {
        mutators.remove(mutator)
        deleteMutatorFile(mutator)

        MainActivity.activityRef.get()?.apply {
            menu?.findItem(R.id.tools)?.subMenu?.removeItem(mutator.hashCode())
        }
    }

}
