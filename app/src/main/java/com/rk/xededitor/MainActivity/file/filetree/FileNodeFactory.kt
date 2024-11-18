package com.rk.xededitor.MainActivity.file.filetree

import io.github.dingyi222666.view.treeview.AbstractTree
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeNode
import io.github.dingyi222666.view.treeview.TreeNodeGenerator
import java.io.File

@Suppress("OVERRIDE_BY_INLINE")
class FileNodeFactory(
     val rootPath: File,
     val fileLoader: FileLoader
): TreeNodeGenerator<File> {
    
    override suspend fun fetchChildData(targetNode: TreeNode<File>): Set<File> {
        val path = targetNode.requireData().absolutePath
        val files = fileLoader.getLoadedFiles(path)
//        if (files.isEmpty()) {
//            files = fileLoader.loadFiles(path)
//        }
        return files.toSet()
    }
    
   
    override inline fun createNode(parentNode: TreeNode<File>, currentData: File, tree: AbstractTree<File>): TreeNode<File> {
        return TreeNode(
            data = currentData,
            depth = parentNode.depth + 1,
            name = currentData.name,
            id = tree.generateId(),
            hasChild = currentData.isDirectory && fileLoader.getLoadedFiles(currentData.absolutePath)
                .isNotEmpty(),
            isChild = currentData.isDirectory,
            expand = false
        )
    }
    
    override inline fun createRootNode(): TreeNode<File> {
        return TreeNode(
            data = rootPath,
            depth = 0,
            name = rootPath.name,
            id = Tree.ROOT_NODE_ID,
            hasChild = true,
            isChild = true,
        )
    }
    
    
}