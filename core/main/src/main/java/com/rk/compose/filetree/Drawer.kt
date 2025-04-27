package com.rk.compose.filetree

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.toFileObject
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.handlers.updateMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Serializable
import java.net.URI


data class FileObjectWrapper(val fileObject: FileObject,val name: String): Serializable{
    override fun equals(other: Any?): Boolean {
        if (other is FileObject){
            return other == fileObject
        }
        if (other !is FileObjectWrapper){
            return false
        }
        return other.fileObject == fileObject
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + fileObject.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}


private val mutex = Mutex()

private suspend fun save(){
    mutex.withLock{
        withContext(Dispatchers.IO){
            val gson = Gson()
            val uniqueProjects = projects.map { it.fileObject.getAbsolutePath() }
            val jsonString = gson.toJson(uniqueProjects)
            Settings.projects = jsonString
        }
    }
}

private suspend fun restore(){
    mutex.withLock{
        withContext(Dispatchers.IO){
            runCatching {
                val jsonString = Settings.projects
                if (jsonString.isNotEmpty()) {
                    val gson = Gson()
                    val projectsList = gson.fromJson(jsonString, Array<String>::class.java).toList()

                    projectsList.forEach {
                        addProject(it.toFileObject())
                        delay(100)
                    }

                }
            }.onFailure { it.printStackTrace() }
        }
    }

}


val projects = mutableStateListOf<FileObjectWrapper>()
var currentProject by mutableStateOf<FileObject?>(null)

fun addProject(fileObject: FileObject){
    if (projects.find { it.fileObject == fileObject } != null){
        return
    }
    projects.add(FileObjectWrapper(fileObject = fileObject, name = fileObject.getName()))
    currentProject = fileObject
    GlobalScope.launch(Dispatchers.IO){
        save()
    }
}

fun removeProject(fileObject: FileObject){
    projects.remove(projects.find { it.fileObject == fileObject })
    if (currentProject == fileObject){
        currentProject = if (projects.size-1 >= 0){
            projects[projects.size-1].fileObject
        }else{
            null
        }
    }
    GlobalScope.launch(Dispatchers.IO){
        save()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        isLoading = true
        restore()
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()){
        if (isLoading){
            CircularProgressIndicator()
        }else{
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxSize()) {
                val scope = rememberCoroutineScope()
                NavigationRail(modifier = Modifier.width(61.dp)) {
                    projects.forEach { file ->
                        NavigationRailItem(
                            selected = file.fileObject == currentProject, icon = {
                            val iconId = if ((file.fileObject is UriWrapper && file.fileObject.isTermuxUri()) || (file.fileObject is FileWrapper && file.fileObject.file == alpineHomeDir())){
                                drawables.terminal
                            }else{
                                drawables.outline_folder_24
                            }
                            Icon(painter = painterResource(iconId),contentDescription = null)
                        }, onClick = {
                            scope.launch{
                                delay(50)
                                currentProject = file.fileObject
                            }

                        }, label = {Text(file.fileObject.getAppropriateName(), maxLines = 1, overflow = TextOverflow.Ellipsis)})
                    }

                    NavigationRailItem(selected = false, icon = {
                        Icon(imageVector = Icons.Outlined.Add,contentDescription = null)
                    }, onClick = {
                        //show add popup

                        MainActivity.withContext {
                            fun handleAddNew() {
                                ActionPopup(this,true).apply {
                                    addItem(
                                        getString(strings.open_directory),
                                        getString(strings.open_dir_desc),
                                        ContextCompat.getDrawable(this@withContext, drawables.outline_folder_24),
                                        listener = {
                                            fileManager?.requestOpenDirectory()
                                        }
                                    )

                                    val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                    val isManager = Environment.isExternalStorageManager()
                                    val legacyPermission = ContextCompat.checkSelfPermission(
                                        this@withContext,
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                    ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                                        this@withContext,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    ) != PackageManager.PERMISSION_GRANTED




                                    if ((is11Plus && isManager) || (!is11Plus && legacyPermission)){
                                        addItem(
                                            getString(strings.open_path),
                                            getString(strings.open_path_desc),
                                            ContextCompat.getDrawable(this@withContext, drawables.android),
                                            listener = {
                                                fileManager?.requestOpenFromPath()
                                            }
                                        )
                                    }

                                    if (BuildConfig.DEBUG){
                                        addItem(
                                            getString(strings.private_files),
                                            getString(strings.private_files_desc),
                                            ContextCompat.getDrawable(this@withContext, drawables.build),
                                            listener = {
                                                if (Settings.has_shown_private_data_dir_warning.not()){
                                                    MaterialAlertDialogBuilder(this@withContext).apply {
                                                        setCancelable(false)
                                                        setTitle(strings.warning)
                                                        setMessage(strings.warning_private_dir)
                                                        setPositiveButton(strings.ok){ _,_ ->
                                                            Settings.has_shown_private_data_dir_warning = true
                                                            lifecycleScope.launch {
                                                                addProject(FileWrapper(filesDir.parentFile!!))
                                                            }
                                                        }
                                                        show()
                                                    }
                                                }else{
                                                    lifecycleScope.launch {
                                                        addProject(FileWrapper(filesDir.parentFile!!))
                                                    }
                                                }

                                            }
                                        )
                                    }


                                    addItem(
                                        strings.terminal_home.getString(),
                                        strings.terminal_home_desc.getString(),
                                        ContextCompat.getDrawable(this@withContext, drawables.terminal),
                                        listener = {
                                            if (Settings.has_shown_terminal_dir_warning.not()){
                                                MaterialAlertDialogBuilder(this@withContext).apply {
                                                    setCancelable(false)
                                                    setTitle(strings.warning)
                                                    setMessage(strings.warning_private_dir)
                                                    setPositiveButton(strings.ok){ _,_ ->
                                                        Settings.has_shown_terminal_dir_warning = true
                                                        lifecycleScope.launch {
                                                            addProject(FileWrapper(alpineHomeDir()))
                                                        }
                                                    }
                                                    show()
                                                }

                                            }else{
                                                lifecycleScope.launch {
                                                    addProject(FileWrapper(alpineHomeDir()))
                                                }
                                            }
                                        }
                                    )


                                    setTitle(getString(strings.add))
                                    getDialogBuilder().setNegativeButton(getString(strings.cancel), null)
                                    show()
                                }
                            }

                            handleAddNew()
                        }
                    }, label = {
                        Text(stringResource(strings.add))
                    })
                }

                VerticalDivider()

                Crossfade(targetState = currentProject) { project ->
                    if (project != null){
                        FileTree(modifier = Modifier.fillMaxSize().weight(1f), rootNode = project.toFileTreeNode(), onFileClick = {
                            if (it.isFile) {
                                MainActivity.withContext {
                                    if (!isPaused){
                                        adapter!!.addFragment(it.file)
                                        if (!Settings.keep_drawer_locked) {
                                            binding!!.drawerLayout.close()
                                        }

                                        DefaultScope.launch { updateMenu(activityRef.get()?.adapter?.getCurrentFragment()) }
                                    }
                                }
                            }
                        }, onFileLongClick = {
                            FileAction(activityRef.get()!!,this.file,it.file)
                        })
                    }else{
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Column(modifier = Modifier.fillMaxSize().weight(1f),verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(drawables.outline_folder_24),contentDescription = null)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No Folder Opened")
                            }
                        }
                    }

                }

            }
        }
    }


}

@Composable
fun ProjectItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    label: String? = null
) {
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .width(50.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {


        val OvalShape = GenericShape { size, _ ->
            addOval(Rect(Offset.Zero, size))
        }


        Surface(
            shape = OvalShape,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surface,
            tonalElevation = if (selected) 2.dp else 0.dp,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
                icon()
            }
        }


        Spacer(modifier = Modifier.height(2.dp))
        label?.let {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
            )

        }
    }
}



