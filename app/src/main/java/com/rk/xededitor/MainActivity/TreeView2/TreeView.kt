/*
 * Copyright © 2022 Github Lzhiyong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.widget.treeview

data class Node<T>(
    var value: T,
    var parent: Node<T>? = null,
    var child: List<Node<T>>? = null,
    var isExpand: Boolean = false,
    var level: Int = 0
)

public object TreeView {
    
    // add child node
    fun <T> add(
        parent: Node<T>, 
        child: List<Node<T>>? = null
    ) {
        // check 
        child?.let {
            if(it.size > 0) {
                parent.isExpand = true
            }
        }
        
        parent.parent?.let {
            val nodes = it.child
            if(nodes != null && nodes.size == 1
              && (child != null && child.size == 0 
              || child == null)) {
                parent.isExpand = true
            }
        }
        
        // parent associate with child
        parent.child = child
        
        child?.forEach {
            it.parent = parent
            it.level = parent.level + 1
        }
    }
    
    // remove child node
    fun <T> remove(
        parent: Node<T>, 
        child: List<Node<T>>? = null
    ) {
        parent.child?.let {
            if(it.size > 0) {
                parent.isExpand = false
            }
        }
        parent.child = null
        
        child?.forEach { childNode ->
            childNode.parent = null
            childNode.level = 0
            if(childNode.isExpand) {
                childNode.isExpand = false
                childNode.child?.let { listNodes ->
                    remove(childNode, listNodes)
                }
            }
        }
    }
    
    // Get all child nodes of the parent node
    private fun <T> getChilds(
        parent: Node<T>,
        result: MutableList<Node<T>>
    ): List<Node<T>> {
        parent.child?.let { result.addAll(it) }
        
        parent.child?.forEach {
            if(it.isExpand) {
                getChilds(it, result)
            }
        }
        return result
    }
    
    fun <T> getChilds(parent: Node<T>) = getChilds(parent, mutableListOf<Node<T>>()) 
}
