package com.rk.xededitor.ui.activities.mainactivity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.xededitor.MainActivity.file.getFragmentType
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.ui.components.ScrollableTabLayout
import com.rk.xededitor.ui.components.TabContent
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarbonTheme {
                MainScreen()
            }
        }
    }
}

class MyViewModel : ViewModel() {
    val openedTabs = mutableStateListOf<String>()
    val openedTabsTypes = mutableStateListOf<FragmentType>()
    val openedTabsFiles = mutableStateListOf<com.rk.file.FileObject?>()

    fun addNewTab(title: String, type: FragmentType) {
        openedTabs.add(title)
        openedTabsTypes.add(type)
        openedTabsFiles.add(null)
    }

    fun addNewTab(file: com.rk.file.FileObject, type: FragmentType? = null) {
        openedTabs.add(file.getName())
        openedTabsTypes.add(type ?: file.getFragmentType())
        openedTabsFiles.add(file)
    }

    fun removeTabAt(index: Int) {
        if (index in openedTabs.indices) {
            openedTabs.removeAt(index)
            openedTabsTypes.removeAt(index)
            openedTabsFiles.removeAt(index)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MyViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        DrawerContent()
    }, content = {
        Scaffold(topBar = {
            MainTopAppBar(onMenuClick = {
                coroutineScope.launch { drawerState.open() }
            })
        }, content = { paddingValues ->
            MainContent(
                modifier = Modifier.padding(paddingValues), viewModel = viewModel
            )
        })
    })
}

@Composable
fun DrawerContent() {
    ModalDrawerSheet {
        Text("Drawer Content")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(onMenuClick: () -> Unit) {
    TopAppBar(title = {}, navigationIcon = {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
    })
}

@Composable
fun MainContent(modifier: Modifier = Modifier, viewModel: MyViewModel) {
    if (viewModel.openedTabs.isNotEmpty()) {
        ScrollableTabLayout(
            modifier = modifier, tabs = viewModel.openedTabs, content = { index ->
                Box(Modifier.fillMaxSize()) {
                    val file = viewModel.openedTabsFiles[index]
                    if (file != null) {
                        TabContent(
                            file = file, type = viewModel.openedTabsTypes[index]
                        )
                    }
                }
            }, animation = false
        )
    }

    val isInitialized = remember { mutableStateOf(false) }

    if (!isInitialized.value) {
        val file =
            com.rk.file.FileWrapper(File(LocalContext.current.filesDir.parentFile, "test.txt"))
        if (!file.exists()) {
            file.createNewFile()
            file.writeText("This is a test")
        }
        repeat(9) {
            viewModel.addNewTab(file)
        }
        isInitialized.value = true
    }

}