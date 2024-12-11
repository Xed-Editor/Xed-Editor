package com.rk.xededitor.ui.screens.settings.mutators

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.libcommons.DefaultScope
import com.rk.resources.drawables
import com.rk.scriptingengine.Engine
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.launch

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

    private val gson = Gson()

    private val mutators = mutableStateListOf<Mutator>()


    fun loadMutators() {
        val json = PreferencesData.getString(PreferencesKeys.MUTATORS, "")
        if (json.isNotEmpty()) {
            val type = object : TypeToken<List<Mutator>>() {}.type
            val savedMutators: List<Mutator> = gson.fromJson(json, type)
            mutators.addAll(savedMutators)
        }
    }

    fun saveMutators() {
        val json = gson.toJson(mutators)
        PreferencesData.setStringAsync(PreferencesKeys.MUTATORS,json)
    }

    fun getMutators(): SnapshotStateList<Mutator> = mutators

    fun createMutator(mutator: Mutator) {
        mutators.add(mutator)
        saveMutators()

        MainActivity.activityRef.get()?.apply {
            val tool = ContextCompat.getDrawable(this,drawables.build_24px)
            menu?.findItem(R.id.tools)?.subMenu?.add(0,mutator.hashCode(),toolItems.size,mutator.name)?.icon = tool
        }

    }

    fun deleteMutator(mutator: Mutator) {
        mutators.remove(mutator)
        saveMutators()
        MainActivity.activityRef.get()?.apply {
            menu?.findItem(R.id.tools)?.subMenu?.removeItem(mutator.hashCode())
        }
    }

    fun run(id:Int){
        DefaultScope.launch {
            mutators.forEach { mut ->
                if (mut.hashCode() == id){
                    Engine(mut.script, DefaultScope).start(onResult = { engine, result ->
                        println(result)
                    }, onError = {t ->
                        t.printStackTrace()
                        rkUtils.toast(t.message)
                    }, api = ImplAPI::class.java)
                    return@launch
                }
            }
        }
    }
}
