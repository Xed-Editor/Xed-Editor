package com.rk.xededitor.MainActivity.treeview2

import androidx.documentfile.provider.DocumentFile

class CacheList : ArrayList<Pair<DocumentFile, List<Node<DocumentFile>>>>() {
  
  fun containsKey(key: DocumentFile): Boolean {
    return synchronized(this) {
      find { it.first == key } != null
    }
  }
  
  fun get(key: DocumentFile): List<Node<DocumentFile>>? {
    return synchronized(this) {
      find { it.first == key }?.second
    }
  }
  
  fun put(key: DocumentFile, value: List<Node<DocumentFile>>) {
    synchronized(this) {
      removeIf { it.first == key }
      add(Pair(key, value))
    }
    
  }
  
  fun putAll(collection: Collection<Pair<DocumentFile, List<Node<DocumentFile>>>>) {
    synchronized(this) {
      clear()
      addAll(collection)
    }
    
  }
}
