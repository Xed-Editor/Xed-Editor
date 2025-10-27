package com.rk.filetree

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.activities.main.MainActivity
import com.rk.components.getDrawerWidth
import com.rk.components.isPermanentDrawer
import com.rk.file.FileObject
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.folderSurface
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.get

private val ic_file = drawables.file
private val folder = drawables.folder
private val unknown = drawables.unknown_document
private val fileSymlink = drawables.file_symlink
private val java = drawables.java
private val html = drawables.ic_language_html
private val kotlin = drawables.ic_language_kotlin
private val python = drawables.ic_language_python
private val xml = drawables.ic_language_xml
private val js = drawables.ic_language_js
private val ts = drawables.typescript
private val lua = drawables.lua
private val plugin = drawables.extension
private val prop = drawables.settings
private val c = drawables.ic_language_c
private val cpp = drawables.ic_language_cpp
private val json = drawables.ic_language_json
private val css = drawables.ic_language_css
private val csharp = drawables.ic_language_csharp
private val bash = drawables.bash
private val apk = drawables.apk_document
private val archive = drawables.archive
private val text = drawables.text
private val video = drawables.video
private val audio = drawables.music
private val image = drawables.image
private val react = drawables.react
private val rust = drawables.rust
private val markdown = drawables.markdown
private val php = drawables.php
private val go = drawables.golang
private val lisp = drawables.lisp
private val sql = drawables.sql

@Composable
private fun FileIcon(file: FileObject) {
    val icon = if (file.isFile()) {
        when (file.getName()) {
            "contract.sol",
            "LICENSE",
            "NOTICE",
                -> text

            "gradlew" -> bash

            else ->
                when (file.getName().substringAfterLast('.', "")) {
                    "java",
                    "bsh", "gradle" -> java

                    "html", "htm", "htmx" -> html
                    "kt",
                    "kts" -> kotlin

                    "py" -> python
                    "xml" -> xml
                    "js" -> js
                    "ts" -> ts
                    "lua" -> lua
                    "c",
                    "h" -> c

                    "cpp",
                    "hpp" -> cpp

                    "json" -> json
                    "css",
                    "sass",
                    "scss" -> css

                    "cs" -> csharp

                    "sh",
                    "bash",
                    "zsh",
                    "bat",
                    "fish",
                    "ksh" -> bash

                    "apk",
                    "xapk",
                    "apks" -> apk

                    "zip",
                    "rar",
                    "7z",
                    "gz",
                    "bz2",
                    "tar",
                    "xz" -> archive

                    "md" -> markdown
                    "txt" -> text

                    "mp3",
                    "wav",
                    "ogg", "m4a", "aac", "wma", "opus",
                    "flac" -> audio

                    "mp4",
                    "mov",
                    "avi",
                    "mkv" -> video

                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "bmp","svg" -> image

                    "rs" -> rust
                    "lisp","clisp" -> lisp
                    "sql" -> sql
                    "jsx", "tsx" -> react
                    "php" -> php
                    "plugin" -> plugin
                    "properties", "pro", "package.json" -> prop
                    "go" -> go
                    else -> ic_file
                }
        }
    } else if (file.isDirectory()) {
        folder
    } else if (file.isSymlink()) {
        fileSymlink
    } else {
        unknown
    }

    val tint = if (icon == folder || icon == archive) {
        MaterialTheme.colorScheme.folderSurface
    } else MaterialTheme.colorScheme.secondary

    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )

}

// ViewModel to handle file operations and caching
class FileTreeViewModel : ViewModel() {
    var selectedFile = mutableStateMapOf<FileObject, FileObject>()
    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val expandedNodes = mutableStateMapOf<FileObject, Boolean>()

    // Track loading states to avoid showing spinners incorrectly
    private val _loadingStates = mutableStateMapOf<FileObject, Boolean>()


    fun isNodeExpanded(fileObject: FileObject): Boolean = expandedNodes[fileObject] == true

    fun isNodeLoading(fileObject: FileObject): Boolean = _loadingStates[fileObject] == true

    fun toggleNodeExpansion(fileObject: FileObject) {
        val wasExpanded = expandedNodes[fileObject] == true
        expandedNodes[fileObject] = !wasExpanded

        // If we're expanding and haven't loaded yet, trigger a load
        if (!wasExpanded && !fileListCache.containsKey(fileObject)) {
            _loadingStates[fileObject] = true
        }
    }

    fun updateCache(file: FileObject) {
        if (file.isFile()) {
            throw IllegalStateException("file ${file.getAbsolutePath()} is a file but a directory was expected")
        }
        viewModelScope.launch(Dispatchers.IO) {
            _loadingStates[file] = true  // Mark as loading

            try {
                // Safely access file listing
                val fileList = try {
                    file.listFiles()
                } catch (e: Exception) {
                    _loadingStates[file] = false
                    return@launch
                }

                // Process files
                val files = fileList
                    .sortedWith(compareBy({ !it.isDirectory() }, { it.getName().lowercase() }))
                    .map {
                        it.toFileTreeNode()
                    }

                fileListCache[file] = files

                //maybe important
                if (!isNodeExpanded(file)) {
                    expandedNodes[file] = true
                }

                viewModelScope.launch {
                    delay(300)
                    _loadingStates[file] = false
                }
            } catch (e: Exception) {
                _loadingStates[file] = false
            }
        }
    }

    suspend fun refreshEverything() = withContext(Dispatchers.IO){
        fileListCache.keys.toList().forEach {
            updateCache(it)
        }
    }

    fun getNodeChildren(node: FileTreeNode): List<FileTreeNode> {
        return fileListCache[node.file] ?: emptyList()
    }

    fun loadChildrenForNode(node: FileTreeNode) {

        // If already in cache, don't reload
        if (fileListCache.containsKey(node.file)) {
            _loadingStates[node.file] = false
            return
        }

        // Set loading state
        _loadingStates[node.file] = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Safely access file listing
                val fileList = try {
                    node.file.listFiles()
                } catch (e: Exception) {
                    _loadingStates[node.file] = false
                    return@launch
                }

                // Process files
                val files = fileList
                    .sortedWith(compareBy({ !it.isDirectory() }, { it.getName().lowercase() }))
                    .map {
                        it.toFileTreeNode()
                    }

                fileListCache[node.file] = files
                viewModelScope.launch {
                    delay(300)
                    _loadingStates[node.file] = false
                }
            } catch (e: Exception) {
                _loadingStates[node.file] = false
            }
        }
    }
}

data class FileTreeNode(
    val file: FileObject,
    val isFile: Boolean,
    val isDirectory: Boolean,
    val name: String
)

var fileTreeViewModel: FileTreeViewModel? = null

@Composable
fun FileTree(
    rootNode: FileTreeNode,
    modifier: Modifier = Modifier,
    onFileClick: FileTreeNode.(FileTreeNode) -> Unit,
    onFileLongClick: FileTreeNode.(FileTreeNode) -> Unit = {},
    viewModel: FileTreeViewModel
) {

    fileTreeViewModel = viewModel
    // Auto-expand root node on first composition
    LaunchedEffect(rootNode.file) {
        if (!viewModel.isNodeExpanded(rootNode.file)) {
            viewModel.toggleNodeExpansion(rootNode.file)
            viewModel.loadChildrenForNode(rootNode)
        }
    }

    LaunchedEffect(MainActivity.instance) {
        MainActivity.instance?.foregroundListener["fileTreeRefresh"] = { resumed ->
            if (resumed){
                viewModel.refreshEverything()
            }
        }
    }

    Surface(
        modifier = modifier,
        color = if (isPermanentDrawer){
            MaterialTheme.colorScheme.surface
        }else{
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = rootNode.file.hashCode()) {
                    FileTreeNodeItem(
                        modifier = Modifier.fillMaxWidth(),
                        node = rootNode,
                        depth = 0,
                        onFileClick = {
                            rootNode.onFileClick(it)
                        },
                        onFileLongClick = {
                            rootNode.onFileLongClick(it)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeNodeItem(
    modifier: Modifier,
    node: FileTreeNode,
    depth: Int,
    onFileClick: (FileTreeNode) -> Unit,
    onFileLongClick: (FileTreeNode) -> Unit,
    viewModel: FileTreeViewModel
) {
    val isExpanded = viewModel.isNodeExpanded(node.file)
    val horizontalPadding = (depth * 16).dp

    val isLoading = viewModel.isNodeLoading(node.file)

    // Load children when expanded
    LaunchedEffect(node.file, isExpanded) {
        if (isExpanded && node.isDirectory) {
            viewModel.loadChildrenForNode(node)
        }
    }

    val children by remember(node.file, isExpanded) {
        derivedStateOf {
            if (node.isDirectory && isExpanded) {
                viewModel.getNodeChildren(node)
            } else {
                emptyList()
            }
        }
    }


    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        if (node.isDirectory) {
                            viewModel.toggleNodeExpansion(node.file)
                        } else {
                            scope.launch {
                                delay(100)
                                onFileClick(node)
                            }

                        }
                        viewModel.selectedFile[currentProject!!] = node.file
                    },
                    onLongClick = {
                        viewModel.selectedFile[currentProject!!] = node.file
                        scope.launch {
                            delay(50)
                            onFileLongClick(node)
                        }

                    }
                )
                .then(
                    if (viewModel.selectedFile[currentProject] == node.file) {
                        Modifier.background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    } else {
                        Modifier
                    }
                )
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(horizontalPadding))

            if (node.isDirectory) {
                val rotationDegree by animateFloatAsState(
                    targetValue = if (!isExpanded) 0f else 90f,
                    label = "rotation"
                )

                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(9.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(drawables.chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotationDegree)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

            } else {
                Spacer(modifier = Modifier.width(24.dp))

            }

            FileIcon(node.file)

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((getDrawerWidth()-61.dp)),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        AnimatedVisibility(
            visible = isExpanded && node.isDirectory,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                children.forEach { childNode ->
                    key(childNode.file.hashCode()) {
                        FileTreeNodeItem(
                            modifier = Modifier.fillMaxWidth(),
                            node = childNode,
                            depth = depth + 1,
                            onFileClick = onFileClick,
                            onFileLongClick = onFileLongClick,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

fun FileObject.getAppropriateName(): String {
    return if (getAbsolutePath() == Environment.getExternalStorageDirectory().absolutePath) {
        strings.storage.getString()
    } else {
        getName()
    }
}

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(
        file = this,
        isFile = isFile(),
        isDirectory = isDirectory(),
        name = getAppropriateName()
    )
}
