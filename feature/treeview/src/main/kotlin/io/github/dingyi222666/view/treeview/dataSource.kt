package io.github.dingyi222666.view.treeview

import android.util.SparseArray
import androidx.core.util.size
import androidx.core.util.valueIterator

/**
 * Wrapper interface for data
 *
 * declares some fields, which are generic.
 */
interface DataSource<T : Any> {
    /**
     * The name of the data.
     *
     * This is displayed by default as the title of the [TreeView] item (you need to set it yourself)
     */
    val name: String

    /**
     * The data
     */
    val data: T?

    /**
     *  position relative to parent data, can be used for indexing or deletion
     */
    var index: Int

    /**
     * The parent?
     */
    var parent: DataSource<T>?

    /**
     * same as [TreeNode.requireData]
     */
    fun requireData(): T {
        return checkNotNull(data)
    }

}

/**
 * A support interface for data containing child nodes.
 */
interface MultipleDataSourceSupport<T : Any> {
    /**
     * Add a child data
     */
    fun add(child: DataSource<T>)

    /**
     * Add all child data
     */
    fun addAll(children: Iterable<DataSource<T>>) {
        children.forEach {
            add(it)
        }
    }

    /**
     * Delete a child data
     */
    fun remove(child: DataSource<T>)

    /**
     * Get the index of the child data
     */
    fun indexOf(child: DataSource<T>): Int

    /**
     * Get from index of child data for child data
     */
    fun get(index: Int): DataSource<T>

    /**
     * List all child data
     */
    fun toList(): List<DataSource<T>>

    /**
     * Set all child data
     */
    fun toSet(): Set<DataSource<T>>

    /**
     * Get the size of child data
     */
    fun size(): Int


}

private class MultipleDataSourceSupportHandler<T : Any> : MultipleDataSourceSupport<T> {

    internal val childList = SparseArray<DataSource<T>>()

    private var lastIndex = 0

    override fun add(child: DataSource<T>) {
        child.index = ++lastIndex
        childList.append(child.index, child)
    }

    override fun remove(child: DataSource<T>) {
        childList.remove(child.index)
    }

    override fun indexOf(child: DataSource<T>): Int {
        return childList.indexOfValue(child)
    }

    override fun get(index: Int): DataSource<T> {
        return childList.get(index)
    }

    override fun toList(): List<DataSource<T>> {
        return childList.valueIterator().asSequence()
            .toList()
    }

    override fun toSet(): Set<DataSource<T>> {
        return childList.valueIterator().asSequence()
            .toSet()
    }


    override fun size(): Int {
        return childList.size
    }

}

/**
 * Single data source
 */
open class SingleDataSource<T : Any> internal constructor(
    override val name: String,
    override val data: T?,
    override var parent: DataSource<T>?
) : DataSource<T> {
    override var index = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SingleDataSource<*>

        if (name != other.name) return false
        if (data != other.data) return false
        return index == other.index
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + index
        return result
    }


}

/**
 * Data sources with child data
 */
open class MultipleDataSource<T : Any> internal constructor(
    override val name: String,
    override val data: T?,
    override var parent: DataSource<T>? = null,
) : DataSource<T>, MultipleDataSourceSupport<T> by MultipleDataSourceSupportHandler() {
    override var index = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultipleDataSource<*>

        if (name != other.name) return false
        if (data != other.data) return false
        return index == other.index
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + index
        return result
    }

}

/**
 * The default node generator for DataSource.
 *
 * This will simplify the code for you to display the data.
 */
class DataSourceNodeGenerator<T : Any>(
    /**
     * The root node of the data to be displayed.
     *
     * The node generator start iterating from this node and generate the nodes
     */
    private val rootData: MultipleDataSource<T>
) : TreeNodeGenerator<DataSource<T>> {
    override suspend fun fetchChildData(targetNode: TreeNode<DataSource<T>>): Set<DataSource<T>> {
        return (targetNode.requireData() as MultipleDataSourceSupport<T>).toSet()
    }

    override fun createNode(
        parentNode: TreeNode<DataSource<T>>,
        currentData: DataSource<T>,
        tree: AbstractTree<DataSource<T>>
    ): TreeNode<DataSource<T>> {
        return TreeNode(
            data = currentData,
            depth = parentNode.depth + 1,
            name = currentData.name,
            id = tree.generateId(),
            hasChild = if (currentData is MultipleDataSource<T>) {
                currentData.size() > 0
            } else false,
            isChild = currentData is MultipleDataSource<T>,
            expand = false
        )
    }

    override suspend fun moveNode(
        srcNode: TreeNode<DataSource<T>>,
        targetNode: TreeNode<DataSource<T>>,
        tree: AbstractTree<DataSource<T>>
    ): Boolean {

        if (targetNode.path.startsWith(srcNode.path)) {
            return false
        }

        val targetData = targetNode.requireData()
        val targetDataParent = targetData.parent
        val srcData = srcNode.requireData()
        val srcDataParent = srcData.parent


        val targetDataSource = if (targetData is MultipleDataSourceSupport<*>) {
            targetData as MultipleDataSourceSupport<T>
        } else {
            targetDataParent as MultipleDataSourceSupport<T>
        }

        val srcDataParentDataSource =
            srcDataParent as MultipleDataSourceSupport<T>

        srcDataParentDataSource.remove(srcData)

        targetDataSource.add(srcData)

        srcData.parent = if (targetData is MultipleDataSourceSupport<*>) {
            targetData
        } else {
            targetDataParent
        }


        return true
    }

    override fun createRootNode(): TreeNode<DataSource<T>> {
        return TreeNode(
            data = rootData,
            depth = -1,
            name = rootData.name,
            id = Tree.ROOT_NODE_ID,
            hasChild = true,
            isChild = true,
        )
    }
}

@DslMarker
annotation class DataSourceMarker

/**
 * Used to generate data associated with [DataSource]
 */
typealias CreateDataScope<T> = (String, DataSource<T>) -> T


@DataSourceMarker
class DataSourceScope<T : Any>(
    internal val currentDataSource: DataSource<T>
) {
    internal var createDataScope: CreateDataScope<T> = { _, _ -> error("Not supported") }
}

/**
 * Build a node with child nodes
 *
 * @param name node name
 * @param data The data of the node.
 *
 * If null, try to get data from [CreateDataScope].
 *
 * @param scope Build scope of child nodes
 */
fun <T : Any> DataSourceScope<T>.Branch(
    name: String,
    data: T? = null,
    scope: DataSourceScope<T>.() -> Unit
) {
    val newData = MultipleDataSource(
        name,
        data ?: createDataScope.invoke(name, currentDataSource),
        currentDataSource
    )
    if (currentDataSource is MultipleDataSourceSupport<*>) {
        (currentDataSource as MultipleDataSourceSupport<T>).add(newData)
    }
    val childScope = DataSourceScope(newData)
    childScope.createDataScope = createDataScope
    scope.invoke(childScope)
}

/**
 * Build a leaf node
 *
 * @param name node name
 * @param data The data of the node.
 *
 */
fun <T : Any> DataSourceScope<T>.Leaf(
    name: String,
    data: T? = null
) {
    if (currentDataSource is MultipleDataSourceSupport<*>) {
        val newData = SingleDataSource(
            name,
            data ?: createDataScope.invoke(name, currentDataSource),
            currentDataSource
        )
        (currentDataSource as MultipleDataSourceSupport<T>).add(newData)
    }
}


/**
 * Build a tree quickly based on kotlin dsl.
 */
fun <T : Any> buildTree(
    /**
     * Data Creator, can be null
     */
    dataCreator: CreateDataScope<T>? = null,
    /**
     *  Build scope of child nodes
     */
    scope: DataSourceScope<T>.() -> Unit
): Tree<DataSource<T>> {

    val root = MultipleDataSource<T>("root", null)
    val rootScope = DataSourceScope(root)

    if (dataCreator != null) {
        rootScope.createDataScope = dataCreator
    }

    scope.invoke(rootScope)

    val tree = Tree<DataSource<T>>()

    tree.generator = DataSourceNodeGenerator(root)

    tree.initTree()

    return tree

}