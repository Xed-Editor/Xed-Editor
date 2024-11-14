package io.github.dingyi222666.view.treeview


/**
 * Node data class.
 *
 * Represents a node that hold relevant data.
 *
 * @param T The type of data stored in the node
 */
open class TreeNode<T : Any>(
    /**
     * The data in the node.
     *
     * This data can be provided to the [TreeViewBinder] to bind data to the view.
     *
     * Note: The data needs to override the equals method and the tree structure need to determine the node to create based on whether the data is equal or not
     */
    var data: T?,

    /**
     * The depth of the node.
     *
     * This value represents the distance of the node relative to the root node.
     *
     * Note: this value can be negative.
     *
     * if it is negative we recommend that you discard the node when visiting the tree,
     * as it is likely to be a non-normal node.
     *
     * For example, a root node of -1 and its children nodes of 0 could be used to implement a visual multiple root node
     */
    var depth: Int,


    /**
     * The path of the node.
     *
     *  The path of the node is a string that represents the path of the node relative to the root node.
     */
    var path: String = "/root",

    /**
     * The name of the node.
     *
     * The  [TreeViewBinder] use this to display the name of the node on the view.
     */
    var name: String?,

    /**
     * The ID of the node.
     *
     * The node's unique and most trusted identifier.
     */
    var id: Int,

    /**
     * Whether the node contains child nodes.
     */
    var hasChild: Boolean = false,

    /**
     * Whether the node is a child node
     */
    var isChild: Boolean = false,

    /**
     * Whether the node is expanded or not.
     *
     * The TreeView checks this value when displaying the list of nodes and decides whether or not to display the child nodes under the node
     */
    var expand: Boolean = true,
) {


    /**
     * Whether the node is selected or not.
     * The [TreeView] use this to display the selected state of the node on the view.
     */
    var selected: Boolean = false
        internal set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TreeNode<*>

        if (depth != other.depth) return false
        if (name != other.name) return false
        if (id != other.id) return false
        if (hasChild != other.hasChild) return false
        if (isChild != other.isChild) return false
        if (expand != other.expand) return false
        if (selected != other.selected) return false
        return data == other.data
    }

    override fun hashCode(): Int {
        var result = 31 + depth
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + id
        result = 31 * result + hasChild.hashCode()
        result = 31 * result + expand.hashCode()
        result = 31 * result + hasChild.hashCode()
        result = 31 * result + selected.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        return result
    }


    /**
     * Return [data] that is not null.
     *
     * At some time, you may have made it clear that [data] is not non-null,
     * then you can call this method. If data is still null, then it will throw an exception.
     *
     * @return non-null [data]
     */
    fun requireData(): T {
        return checkNotNull(data)
    }

    /**
     * shallow copy this node.
     */
    fun copy(): TreeNode<T> {
        return TreeNode(
            data = data,
            depth = depth,
            name = name,
            id = id,
            hasChild = hasChild,
            isChild = isChild,
            expand = expand,
        ).apply {
            selected = this@TreeNode.selected
        }
    }

    override fun toString(): String {
        return "TreeNode(data=$data, depth=$depth, name=$name, id=$id, hasChild=$hasChild, isChild=$isChild, expand=$expand, selected=$selected)"
    }
}


