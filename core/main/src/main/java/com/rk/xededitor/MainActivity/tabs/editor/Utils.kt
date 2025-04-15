package com.rk.xededitor.MainActivity.tabs.editor

import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment

fun fragmentsForEach(callback:(CoreFragment)-> Unit){
    MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach { weakRef ->
        weakRef.get()?.fragment?.let {
            callback.invoke(it)
        }
    }
}

fun editorFragmentsForEach(callback: (EditorFragment) -> Unit){
    fragmentsForEach { fragment ->
        if (fragment.isEditorFragment()){
            callback.invoke(fragment as EditorFragment)
        }
    }
}

inline fun CoreFragment.isEditorFragment(): Boolean{
    return this is EditorFragment
}

fun saveAllFiles(){
    editorFragmentsForEach {
        it.save(isAutoSaver = true)
    }
}

fun getCurrentFragment(): CoreFragment?{
    return MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.fragment
}

fun getCurrentEditorFragment(): EditorFragment?{
    return getCurrentFragment() as? EditorFragment
}
