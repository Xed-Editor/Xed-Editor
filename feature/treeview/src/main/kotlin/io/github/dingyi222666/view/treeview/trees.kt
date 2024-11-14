package io.github.dingyi222666.view.treeview

import android.util.SparseArray
import androidx.core.util.putAll


/**
 * Default tree structure implementation
 */
class Tree<T : Any> internal constructor() : AbstractTree<T> {

    private val allNode = SparseArray<TreeNode<T>>()

    private val selectedNodes = mutableListOf<TreeNode<T>>()

    private val allNodeAndChild = HashMultimap<Int, Int, LinkedHashSet<Int>> {
        LinkedHashSet()
    }

    private lateinit var _rootNode: TreeNode<T>

    override var rootNode: TreeNode<T>
        set(value) {
            if (this::_rootNode.isInitialized) {
                removeNode(_rootNode.id)
            }
            _rootNode = value
            putNode(value.id, value)
        }
        get() = _rootNode

    override lateinit var generator: TreeNodeGenerator<T>

    @Synchronized
    override fun generateId(): Int {
        idx++
        return idx
    }

    private fun removeAllChildNode(currentNodeId: Int) {
        allNodeAndChild.remove(currentNodeId)
    }

    private fun removeNode(currentNodeId: Int) {
        allNode.remove(currentNodeId)
        removeAllChildNode(currentNodeId)
    }

    private fun putNode(id: Int, node: TreeNode<T>) {
        allNode.put(id, node)
    }

    private fun putChildNode(nodeId: Int, childNodeId: Int) {
        allNodeAndChild.put(nodeId, childNodeId)
    }

    private fun putNodeAndBindParentNode(node: TreeNode<T>, parentNodeId: Int) {
        putNode(node.id, node)
        putChildNode(parentNodeId, node.id)
    }

    private fun removeAndAddAllChild(node: TreeNode<T>, list: LinkedHashSet<TreeNode<T>>) {
        removeAllChild(node)
        addAllChild(node, list)
    }

    private fun getChildNodesForCacheInternal(nodeId: Int): Set<Int> {
        return allNodeAndChild[nodeId] ?: emptySet()
    }

    private fun addAllChild(parentNode: TreeNode<T>, currentNodes: LinkedHashSet<TreeNode<T>>) {
        parentNode.isChild = true
        parentNode.hasChild = true

        putNode(parentNode.id, parentNode)

        currentNodes.forEach {
            putNodeAndBindParentNode(it, parentNode.id)
        }

        if (getChildNodesForCacheInternal(parentNode.id).isEmpty()) {
            parentNode.hasChild = false
        }
    }

    private fun removeAllChild(currentNode: TreeNode<*>) {
        currentNode.hasChild = false

        removeAllChildNode(currentNode.id)
    }

    override fun createRootNode(): TreeNode<T> {
        val rootNode = createRootNodeUseGenerator() ?: TreeNode<T>(
            data = null, depth = 0, name = "Root", id = 0
        )
        rootNode.isChild = true
        rootNode.expand = true
        this.rootNode = rootNode
        return rootNode
    }

    private suspend fun getChildNodesInternal(currentNodeId: Int): Set<Int> {
        refresh(getNode(currentNodeId))
        return getChildNodesForCacheInternal(currentNodeId)
    }


    override suspend fun getChildNodes(currentNode: TreeNode<T>): List<TreeNode<T>> {
        return getChildNodes(currentNode.id)
    }

    override suspend fun getChildNodes(currentNodeId: Int): List<TreeNode<T>> {
        refresh(getNode(currentNodeId))
        return getNodesInternal(getChildNodesForCacheInternal(currentNodeId))
    }

    override fun getNode(id: Int): TreeNode<T> {
        return allNode.get(id) as TreeNode<T>
    }

    private fun getNodesInternal(nodeIdList: Set<Int>): MutableList<TreeNode<T>> =
        mutableListOf<TreeNode<T>>().apply {
            nodeIdList.forEach {
                add(getNode(it))
            }
        }

    override fun getChildNodesForCache(currentNode: TreeNode<T>): List<TreeNode<T>> {
        return getChildNodesForCache(currentNode.id)
    }

    override fun getChildNodesForCache(currentNodeId: Int): List<TreeNode<T>> {
        val childNodes = getChildNodesForCacheInternal(currentNodeId)
        return getNodes(childNodes)
    }

    private fun getParentNodeInternal(currentNodeId: Int): Int? {
        return allNodeAndChild.entries.find { it.value == currentNodeId }?.key
    }

    override fun getParentNode(currentNode: TreeNode<T>): TreeNode<T>? {
        return getParentNode(currentNode.id)
    }

    override fun getParentNode(currentNodeId: Int): TreeNode<T>? {
        if (currentNodeId == rootNode.id) {
            return null
        }
        return getParentNodeInternal(currentNodeId)?.let(::getNode)
    }


    private fun addOrRemoveSelectedNode(node: TreeNode<T>) {
        val selected = node.selected
        if (selected) {
            if (
                selectedNodes.all { it.path != node.path }
            ) {
                selectedNodes.add(node)
            }
        } else {
            // Why HashSet with this method is not working?
            // ???
            // So I use MutableList
            selectedNodes.removeAll { next ->
                next.path == node.path
            }
        }
    }

    override suspend fun selectNode(node: TreeNode<T>, selected: Boolean, selectChild: Boolean) {
        node.selected = selected

        addOrRemoveSelectedNode(node)

        if (!selectChild || !node.isChild) {
            return
        }
        refreshWithChild(node, false)
        val willRefreshNodes = ArrayDeque<TreeNode<T>>()

        willRefreshNodes.add(node)
        while (willRefreshNodes.isNotEmpty()) {
            val currentNode = willRefreshNodes.removeFirst()
            currentNode.selected = selected
            addOrRemoveSelectedNode(currentNode)
            if (!currentNode.isChild) {
                continue
            }
            getChildNodesForCache(currentNode).forEach {
                willRefreshNodes.add(it)
            }
        }

    }

    override fun getSelectedNodes(): List<TreeNode<T>> = selectedNodes

    override fun getNodes(nodeIdList: Set<Int>): List<TreeNode<T>> =
        getNodesInternal(nodeIdList)

    private suspend fun refreshInternal(parentNode: TreeNode<T>): Set<TreeNode<T>> {
        val childNodeCache = getChildNodesForCacheInternal(parentNode.id)

        val targetChildNodeList = LinkedHashSet<TreeNode<T>>()
        val childNodeData = generator.fetchChildData(parentNode)

        if (childNodeData.isEmpty()) {
            removeAndAddAllChild(parentNode, targetChildNodeList)
            return targetChildNodeList
        }

        val oldNodes = getNodesInternal(childNodeCache)

        for (data in childNodeData) {
            val targetNode =
                oldNodes.find { it.data == data } ?: generator.createNode(parentNode, data, this)
            if (targetNode.path == "/root" && targetNode != rootNode) {
                targetNode.path = parentNode.path + "/" + targetNode.name
            }
            oldNodes.remove(targetNode)
            targetChildNodeList.add(targetNode)
        }

        removeAndAddAllChild(parentNode, targetChildNodeList)

        return targetChildNodeList
    }

    override suspend fun refresh(node: TreeNode<T>): TreeNode<T> {
        refreshInternal(node)
        return node
    }

    override suspend fun refreshWithChild(node: TreeNode<T>, withExpandable: Boolean): TreeNode<T> {
        val willRefreshNodes = ArrayDeque<TreeNode<T>>()

        willRefreshNodes.add(node)

        while (willRefreshNodes.isNotEmpty()) {
            val currentRefreshNode = willRefreshNodes.removeLast()
            if (!currentRefreshNode.isChild) {
                continue
            }
            val childNodes = refreshInternal(currentRefreshNode)
            for (childNode in childNodes) {
                if (withExpandable && !childNode.expand) {
                    continue
                }
                willRefreshNodes.addLast(childNode)
            }
        }

        return node
    }


    override suspend fun collapseAll(fullRefresh: Boolean) {
        visitInternal(CollapseAllTreeVisitor(), rootNode, !fullRefresh)
    }

    override suspend fun collapseAll(node: TreeNode<T>, fullRefresh: Boolean) {
        visitInternal(CollapseAllTreeVisitor(), node, !fullRefresh)
    }

    override suspend fun collapseFrom(depth: Int, fullRefresh: Boolean) {
        visitInternal(CollapseDepthTreeVisitor(depth), rootNode, !fullRefresh)
    }

    override suspend fun collapseNode(node: TreeNode<T>, fullRefresh: Boolean) {
        node.expand = false
        if (fullRefresh) {
            refreshWithChild(node, true)
        }
    }

    override suspend fun expandAll(fullRefresh: Boolean) {
        visitInternal(ExpandAllTreeVisitor(), rootNode, !fullRefresh)
    }

    override suspend fun expandAll(node: TreeNode<T>, fullRefresh: Boolean) {
        visitInternal(ExpandAllTreeVisitor(), node, !fullRefresh)
    }

    override suspend fun expandNode(node: TreeNode<T>, fullRefresh: Boolean) {
        node.expand = true
        if (fullRefresh) {
            refreshWithChild(node, true)
        }
    }

    override suspend fun expandUntil(depth: Int, fullRefresh: Boolean) {
        visitInternal(ExpandDepthTreeVisitor(depth), rootNode, !fullRefresh)
    }

    override suspend fun visit(visitor: TreeVisitor<T>, fastVisit: Boolean) {
        visitInternal(visitor, rootNode, fastVisit)
    }

    override suspend fun visit(visitor: TreeVisitor<T>, rootNode: TreeNode<T>, fastVisit: Boolean) {
        visitInternal(visitor, rootNode, fastVisit)
    }

    override fun copy(): Tree<T> {
        val tree = createTree<T>()
        tree.generator = generator
        tree.rootNode = rootNode
        tree.selectedNodes.addAll(selectedNodes)
        tree.allNode.putAll(allNode)
        tree.allNodeAndChild.putAll(allNodeAndChild)
        return tree
    }

    private suspend fun visitInternal(
        visitor: TreeVisitor<T>,
        targetNode: TreeNode<T>,
        fastVisit: Boolean
    ) {

        val nodeQueue = ArrayDeque<TreeNode<T>>()

        nodeQueue.add(targetNode)

        while (nodeQueue.isNotEmpty()) {
            val currentNode = nodeQueue.removeFirst()

            if (!currentNode.isChild) {
                visitor.visitLeafNode(currentNode)
                continue
            }

            if (!visitor.visitChildNode(currentNode)) {
                continue
            }

            val children = if (fastVisit)
                getChildNodesForCacheInternal(currentNode.id)
            else getChildNodesInternal(currentNode.id)

            if (children.isEmpty()) {
                continue
            }

            children.reversed().forEach {
                val childNode = getNode(it)
                nodeQueue.addFirst(childNode)
            }
        }
    }


    companion object {
        @get:Synchronized
        @set:Synchronized
        private var idx = 0

        /**
         * Create a new tree structure to store data of type [T]
         */
        fun <T : Any> createTree(): Tree<T> {
            return Tree()
        }

        /**
         * The root node ID, we recommend using this ID to mark the root node
         */
        const val ROOT_NODE_ID = 0
    }
}

/**
 * The node generator is the correlate for the generation of child nodes (the current node).
 *
 * The tree structure allows access to node data through this.
 *
 *
 *
 * @param T data type stored in node
 */
interface TreeNodeGenerator<T : Any> {

    /**
     * Fetch data from child nodes based on the current node.
     *
     * You will need to fetch the data yourself (which can be asynchronous)
     *
     * @return Data list of the children of the current node.
     */
    suspend fun fetchChildData(targetNode: TreeNode<T>): Set<T>

    /**
     * Given the data and the parent node, create a new node.
     *
     * This method is only called to create new nodes when the tree data structure require it
     *
     * @param [currentData] Need to create node data
     * @param [parentNode] Need to create the parent node of the node
     * @param [tree] Target tree
     */
    fun createNode(parentNode: TreeNode<T>, currentData: T, tree: AbstractTree<T>): TreeNode<T>

    /**
     * Create a root node.
     *
     * In anyway, each tree will have a root node,
     * and then the node generator generates its children based on this root node.
     *
     * If you want to have (visual) multiple root node support,
     * you can set the root node level to less than 0.
     */
    fun createRootNode(): TreeNode<T>? = null

    /**
     * like [AbstractTree.moveNode]
     *
     * @return Whether the node is moved successfully
     */
    suspend fun moveNode(srcNode: TreeNode<T>, targetNode: TreeNode<T>, tree: AbstractTree<T>): Boolean {
        return false
    }
}

/**
 * The node id generator is used to generate an id as a unique token for each node.
 */
interface TreeIdGenerator {

    /**
     * Generate id
     */
    fun generateId(): Int
}
