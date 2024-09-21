# FileTree
Lightweight and fast file tree for Android

## Overview

`FileTree` is a custom Android view that extends `RecyclerView` to display a hierarchical file structure. This library provides a flexible and visually appealing way to present file trees in your Android applications.

## Getting Started

### Integration

1. **Add Dependency**

   First, include the FileTree library in your project. Add the following to your `build.gradle` file:

   currently not available you have to add the library manually 
   ```groovy
   implementation 'com.rk:filetree:1.0.0'

2. **Initialization**

   You can initialize the `FileTree` view programmatically or define it directly in your XML layout.

   **XML Layout Example:**

   ```xml
   <!-- XML layout file example -->
   <com.rk.filetree.widget.FileTree
       android:layout_width="match_parent"
       android:layout_height="match_parent" />
   ```

   **Programmatic Initialization:**

   ```kotlin
   // Initialize FileTree programmatically
   val fileTree = FileTree(this)
   ```

   It is recommended to wrap the `FileTree` view within a `HorizontalScrollView` or the custom `DiagonalScrollView` provided by this library for smoother navigation through large file structures.

### Loading Files

Use the `loadFiles` method to populate the `FileTree` with file data. Ensure you create a wrapper for the `FileObject` interface.

**Example:**

```kotlin
// Using private app data files as a demo
val targetFile = filesDir.parentFile!!

// Creating a FileObject wrapper
val fileObject = file(targetFile)

// Load the file tree
fileTree.loadFiles(fileObject)
```

- The `loadFiles` method accepts an optional boolean parameter `showRootNodeX`. If set to `false`, the root node will not be displayed. If `true` or `null`, the root node will be shown. To change the root file, call the `loadFiles` method again with a different `FileObject`.

### Customizing Icons

By default, `FileTree` uses a default icon provider. To use custom icons, implement and set your own `FileIconProvider`.

**Example:**

```kotlin
fileTree.setIconProvider(object : FileIconProvider {
    override fun getIcon(node: Node<FileObject>): Drawable? {
        val fileObject = node.value
        return if (fileObject.isDirectory()) {
            directoryDrawable  // Custom drawable for directories
        } else {
            fileDrawable  // Custom drawable for files
        }
    }

    override fun getChevronRight(): Drawable? {
        return chevronDrawable  // Custom drawable for the chevron icon
    }

    override fun getExpandMore(): Drawable? {
        return expandChevronDrawable  // Custom drawable for the expand more icon
    }
})
```

### Setting Listeners

You can set click and long-click listeners to handle user interactions with the file items.

**Example:**

```kotlin
// Setting a file click listener
fileTree.setOnFileClickListener(object : FileClickListener {
    override fun onClick(node: Node<FileObject>) {
        val fileObject = node.value
        Toast.makeText(this@MainActivity, "Clicked: ${fileObject.getName()}", Toast.LENGTH_SHORT).show()
    }
})

// Setting a file long-click listener
fileTree.setOnFileLongClickListener(object : FileLongClickListener {
    override fun onLongClick(node: Node<FileObject>) {
        val fileObject = node.value
        Toast.makeText(this@MainActivity, "Long clicked: ${fileObject.getName()}", Toast.LENGTH_SHORT).show()
    }
})
```

### Refreshing the File Tree

To refresh the file tree, use the `reloadFileTree` method.

**Example:**

```kotlin
// Refresh the file tree
fileTree.reloadFileTree()
```

## Contributing

If you'd like to contribute to the development of this library, please follow the standard GitHub workflow for contributing:

1. Fork the repository.
2. Create a feature branch.
3. Make your changes and test them.
4. Submit a pull request with a detailed description of your changes.

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Contact

For any questions or support, please reach out to [kushavhar173@gmail.com](mailto:kushavhar173@gmail.com).
