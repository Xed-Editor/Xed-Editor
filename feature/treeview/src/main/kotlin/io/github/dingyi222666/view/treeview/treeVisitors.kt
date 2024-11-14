package io.github.dingyi222666.view.treeview


/**
 * Tree visitor.
 *
 * The tree structure receive the object and then start accessing the object from
 * the root node on down (always depth first access)
 *
 * @param T The type of data stored in the tree to be visited
 *
 * @see [TreeVisitable]
 */
interface TreeVisitor<T : Any> {
    /**
     * Visit a child node.
     *
     * The tree structure call this method to notify the object that the child node was visited.
     *
     * @return Does it continue to access the child nodes under this node
     */
    fun visitChildNode(node: TreeNode<T>): Boolean {
        return true
    }


    /**
     * Visit a leaf node.
     *
     * The tree structure call this method to notify the object that the leaf node was visited.
     */
    fun visitLeafNode(node: TreeNode<T>) {}
}

/**
 * TreeVisitable.
 *
 * Each tree structure needs to implement this interface to enable access to the tree.
 *
 * @see [TreeVisitor]
 */
interface TreeVisitable<T : Any> {
    /**
     * Visit the tree.
     * The tree structure implement this to enable access to the tree.
     *
     * This method is a suspend function as it needs to fetch node data from the node generator.
     *
     * It can be called to visit a tree.
     *
     * @param [visitor] Tree visitor
     * @param [fastVisit] Whether to have quick access.
     *
     * If the value is true, then the node data will be fetched from the cache instead of
     * calling the node generator to fetch the node data.
     */
    suspend fun visit(visitor: TreeVisitor<T>, fastVisit: Boolean = false)

    /**
     * Visit the tree.
     * The tree structure implement this to enable access to the tree.
     *
     * This method is a suspend function as it needs to fetch node data from the node generator.
     *
     * It can be called to visit a tree.
     *
     * @param [visitor] Tree visitor
     * @param [rootNode] The node that needs to be visited will be visited from that node on down
     * @param [fastVisit] Whether to have quick access.
     *
     * If the value is true, then the node data will be fetched from the cache instead of
     * calling the node generator to fetch the node data.
     */
    suspend fun visit(visitor: TreeVisitor<T>, rootNode: TreeNode<T>, fastVisit: Boolean = false)
}


class CollapseAllTreeVisitor<T : Any> : TreeVisitor<T> {
    override fun visitChildNode(node: TreeNode<T>): Boolean {
        if (node.depth <= -1) {
            return true
        }
        node.expand = false

        return true
    }
}

class CollapseDepthTreeVisitor<T : Any>(
    private val depth: Int
) : TreeVisitor<T> {
    override fun visitChildNode(node: TreeNode<T>): Boolean {
        if (node.depth <= -1) {
            return true
        }
        val oldExpand = node.expand
        node.expand = node.depth < depth
        return if (node.depth <= depth) true else oldExpand
    }
}

class ExpandDepthTreeVisitor<T : Any>(
    private val depth: Int
) : TreeVisitor<T> {
    override fun visitChildNode(node: TreeNode<T>): Boolean {
        node.expand = true
        return node.depth < depth
    }
}


class ExpandAllTreeVisitor<T : Any> : TreeVisitor<T> {
    override fun visitChildNode(node: TreeNode<T>): Boolean {
        node.expand = true
        return true
    }
}