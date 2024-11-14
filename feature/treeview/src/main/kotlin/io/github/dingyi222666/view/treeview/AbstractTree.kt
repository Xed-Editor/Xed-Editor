package io.github.dingyi222666.view.treeview

import androidx.annotation.CallSuper


/**
 * Abstract interface for tree structure.
 *
 * You can implement your own tree structure.
 */
interface AbstractTree<T : Any> : TreeVisitable<T>, TreeIdGenerator {

    /**
     * Node generator.
     *
     * The object is required for each tree structure so that the node data can be retrieved.
     *
     * @see [TreeNodeGenerator]
     */
    var generator: TreeNodeGenerator<T>


    /**
     * Root node.
     *
     * All data for the tree structure is obtained based on the root node and the node generator.
     */
    var rootNode: TreeNode<T>

    /**
     * Create the root node.
     *
     * This method is called first when the init tree is created.
     *
     * In this method you need to call the [createRootNodeUseGenerator] method first
     * and then create an empty root node if the [TreeNodeGenerator] does not return a root node.
     *
     * @see [createRootNodeUseGenerator]
     * @see [TreeNodeGenerator]
     */
    fun createRootNode(): TreeNode<T>

    /**
     * Use [TreeNodeGenerator] to create the root node.
     * This method will actually call [TreeNodeGenerator.createRootNode]
     *
     * @see [TreeNodeGenerator.createRootNode]
     */
    fun createRootNodeUseGenerator(): TreeNode<T>? {
        return generator.createRootNode()
    }

    /**
     * Initializing the tree.
     *
     * Subclass overrides can do something when that method is called.
     *
     * Note: Before initializing the tree, make sure that the node generator is set up.
     */
    @CallSuper
    fun initTree() {
        createRootNode()
    }


    /**
     * Get the node pointed to by the path from the cache.
     *
     * The path is a string that represents the path to the node.
     *
     * Such as: "/path/to" represents the node with name "to" under the node with name "path"
     *
     * **Tips: The path must start with "/"**
     *
     * @param path path
     */
    fun resolveNodeFromCache(path: String): TreeNode<T> {
        val realPath = if (path.startsWith("/root")) path.substring(5) else path
        val pathList = realPath.split("/")
        var currentNode = rootNode
        for (i in 1 until pathList.size) {
            val name = pathList[i]
            val childNodes = getChildNodesForCache(currentNode)
            val node = childNodes.find { it.name == name }
            if (node == null) {
                throw IllegalArgumentException(
                    "Can't find node with path $realPath when resolve path to ${
                        pathList.slice(
                            0..i
                        ).joinToString(separator = "/")
                    }"
                )
            } else {
                currentNode = node
            }
        }
        return currentNode
    }

    /**
     * Get the list of children of the current node.
     *
     * Like [resolveNodeFromCache] but always load node form [TreeNodeGenerator]
     */
    suspend fun resolveNode(path: String): TreeNode<T> {
        val realPath = if (path.startsWith("/root")) path.substring(5) else path
        val pathList = realPath.split("/")
        var currentNode = rootNode
        for (i in 1 until pathList.size) {
            val name = pathList[i]
            val childNodes = getChildNodes(currentNode)
            val node = childNodes.find { it.name == name }
            if (node == null) {
                throw IllegalArgumentException(
                    "Can't find node with path $realPath when resolve path to ${
                        pathList.slice(
                            0..i
                        ).joinToString(separator = "/")
                    }"
                )
            } else {
                currentNode = node
            }
        }
        return currentNode
    }

    /**
     * Get the list of children of the current node.
     *
     * This method returns a list of the ids of the child nodes,
     * you may need to do further operations to get the list of child nodes
     *
     * @param currentNode current node
     * @return List of child nodes
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun getChildNodes(currentNode: TreeNode<T>): List<TreeNode<T>>

    /**
     * Get the child list of the current node pointed to by the id.
     *
     * This method returns a list of the ids of the child nodes,
     * you may need to do further operations to get the list of child nodes
     *
     * @param currentNodeId Need to get the id of a node in the child node list
     * @return List of child nodes
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun getChildNodes(currentNodeId: Int): List<TreeNode<T>>

    /**
     * Get the child node data of the node from the cache.
     *
     * Different from [getChildNodes], this method does not call the node generator, it just fetch the child nodes from the cache
     *
     * @param currentNode current node
     * @return List of child nodes
     */
    fun getChildNodesForCache(currentNode: TreeNode<T>): List<TreeNode<T>>

    /**
     * Get the child node data of the node from the cache.
     *
     * Different from [getChildNodes], this method does not call the node generator, it just fetch the child nodes from the cache
     *
     * @param currentNodeId Need to get the id of a node in the child node list
     * @return List of child nodes
     */
    fun getChildNodesForCache(currentNodeId: Int): List<TreeNode<T>>

    /**
     * Get the parent node of the given node.
     *
     * If the given node is the root node, then return `null`
     *
     * @param currentNode Need to get the node of the parent node
     */
    fun getParentNode(currentNode: TreeNode<T>): TreeNode<T>?

    /**
     * Get the parent node of the given node.
     *
     * If the given node is the root node, then return `null`
     *
     * @param currentNodeId Need to get the node id of the parent node
     */
    fun getParentNode(currentNodeId: Int): TreeNode<T>?


    /**
     * Select the node.
     *
     * @param [node] Node that need to select
     * @param [selected] Whether to select the node, if false, then the node will be unselected
     * @param [selectChild] Whether to select all the child nodes, if false, then only the current node will be selected
     */
    suspend fun selectNode(node: TreeNode<T>, selected: Boolean = true, selectChild: Boolean = true)

    /**
     * Select all the nodes.
     *
     * @param [selected] Whether to select all the nodes, if false, then all the nodes will be unselected
     * @see [selectNode]
     */
    suspend fun selectAllNode(selected: Boolean = true) {
        selectNode(rootNode, selected)
    }


    /**
     * Get the list of selected nodes.
     *
     * @return List of selected nodes
     */
    fun getSelectedNodes(): List<TreeNode<T>>

    /**
     * Refresh the current node.
     *
     * Refresh the node, this will update the list of children of the current node.
     *
     * Note: Refreshing a node does not update all the children under the node, the method will only update one level (the children under the node). You can call this method repeatedly if you need to update all the child nodes
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun refresh(node: TreeNode<T>): TreeNode<T>

    /**
     * Refresh the current node and it‘s child.
     *
     * Refreshing the current node and also refreshes its children.
     *
     * @param [withExpandable] Whether to refresh only the expanded child nodes, otherwise all will be refreshed.
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun refreshWithChild(node: TreeNode<T>, withExpandable: Boolean = true): TreeNode<T>

    /**
     * Switch the state of the node.
     *
     * If the node is a leaf node, then the method will do nothing.
     * Otherwise, it will switch the expand and collapse state of the node.
     *
     * @param [node] Node that need to switch node state
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun toggleNode(node: TreeNode<T>, fullRefresh: Boolean = false) {
        if (!node.isChild) {
            return
        }
        if (node.expand) {
            collapseNode(node, fullRefresh)
        } else {
            expandNode(node, fullRefresh)
        }
    }

    /**
     * Expand all nodes.
     *
     * Start from the root node and set all non leaf nodes to expanded state
     *
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun expandAll(fullRefresh: Boolean = false)

    /**
     * Expand the given node and its children start from it
     *
     * Expand the node from the given node, which also includes all its children.
     *
     * @param [node] Node to be expanded
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun expandAll(node: TreeNode<T>, fullRefresh: Boolean = false)

    /**
     * expand node.
     *
     * This will expand the children of the given node with no change in the state of the children.
     * This is especially different from [expandAll].
     *
     * @param [node] Node to be expanded
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun expandNode(node: TreeNode<T>, fullRefresh: Boolean = false)

    /**
     * Collapse all nodes.
     *
     * Start from the root node and set all non leaf nodes to collapsed state
     *
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseAll(fullRefresh: Boolean = false)

    /**
     * Collapse the given node and its children start from it
     *
     * Collapse the node from the given node, which also includes all its children.
     *
     * @param [node] Node to be collapsed
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseAll(node: TreeNode<T>, fullRefresh: Boolean = false)

    /**
     * collapse node.
     *
     * This will collapse the children of the given node with no change in the state of the children.
     * This is especially different from [collapseAll].
     *
     * @param [node] Node to be collapsed
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseNode(node: TreeNode<T>, fullRefresh: Boolean = false)

    /**
     * Expand nodes of the given depth
     *
     * This will expand the nodes that have the given depth and does not include its children
     *
     * @param [depth] Given depth
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseFrom(depth: Int, fullRefresh: Boolean = false)


    /**
     * Collapse the nodes of the given depth
     *
     * This will collapse the nodes that have the given depth and does not include its children
     *
     * @param [depth] Given depth
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
    .
     */
    suspend fun expandUntil(depth: Int, fullRefresh: Boolean = false)

    /**
     * Get the list of node from the given list of id
     */
    fun getNodes(nodeIdList: Set<Int>): List<TreeNode<T>>

    /**
     * Get node pointed to from id
     */
    fun getNode(id: Int): TreeNode<T>

    /**
     * shallow copy the tree. The new tree will have the same root node as the original tree.
     */
    fun copy(): AbstractTree<T>

    /**
     * move the node to the target node.
     *
     * If the target node and the node are have the same parent node, you will return false.
     *
     * You need move the real data by yourself.
     *
     */
    suspend fun moveNode(srcNode: TreeNode<T>, targetNode: TreeNode<T>): Boolean {
        return generator.moveNode(srcNode, targetNode, this)
    }

}

/**
 * Convert the node data in a tree structure into an ordered list.
 *
 * @param withExpandable Whether to add collapsed nodes
 * @param fastVisit Quick visit to the tree structure or not
 *
 * @see AbstractTree
 * @see TreeVisitor
 * @see TreeVisitable
 */
suspend fun <T : Any> AbstractTree<T>.toSortedList(
    withExpandable: Boolean = true, fastVisit: Boolean = true
): List<TreeNode<T>> {
    val result = mutableListOf<TreeNode<T>>()

    val visitor = object : TreeVisitor<T> {
        override fun visitChildNode(node: TreeNode<T>): Boolean {
            if (node.depth >= 0) {
                result.add(node)
            }
            return if (withExpandable) {
                node.expand
            } else {
                true
            }
        }

        override fun visitLeafNode(node: TreeNode<T>) {
            if (node.depth >= 0) {
                result.add(node)
            }
        }

    }

    visit(visitor, fastVisit)

    return result
}