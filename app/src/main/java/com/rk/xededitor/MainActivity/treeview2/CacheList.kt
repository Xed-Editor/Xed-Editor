package com.rk.xededitor.MainActivity.treeview2

import androidx.documentfile.provider.DocumentFile

class CacheList : ArrayList<Pair<DocumentFile, List<Node<DocumentFile>>>>() {
    fun containsKey(key: DocumentFile): Boolean {
        return any { it.first == key }
    }
    fun get(key:DocumentFile) : List<Node<DocumentFile>>?{
        var value: List<Node<DocumentFile>>? = null
        any{
            if (it.first.equals(key)){
                value = it.second
                true
            }else{
                false
            }
        }
        return value
    }
    fun put(key:DocumentFile,value: List<Node<DocumentFile>>){
        add(Pair(key,value))
    }
    fun putAll(collection:Collection<Pair<DocumentFile, List<Node<DocumentFile>>>>){
        clear()
        addAll(collection)
    }
}