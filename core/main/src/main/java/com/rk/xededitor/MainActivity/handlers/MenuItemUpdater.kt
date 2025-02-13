package com.rk.xededitor.MainActivity.handlers

import android.view.Menu
import com.rk.file_wrapper.FileWrapper
import com.rk.libcommons.runOnUiThread
import com.rk.runner.Runner
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.file.FileManager.Companion.findGitRoot
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.git.GitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private var lastUpdate = 0L
suspend fun updateMenu(tabFragment: TabFragment?) = withContext(Dispatchers.Main) {
    if (System.currentTimeMillis() - lastUpdate < 200) {
        return@withContext
    }

    lastUpdate = System.currentTimeMillis()
    val menu = MainActivity.activityRef.get()?.menu
    if (menu != null) {
        val fragment = tabFragment?.fragment
        updateEditor(fragment as? EditorFragment, menu)
        updateSearchMenu(menu, fragment as? EditorFragment)
        updateGitMenuVisibility(menu, fragment as? EditorFragment)
    }
}

private suspend fun updateEditor(
    fragment: EditorFragment?,
    menu: Menu,
    showItems: Boolean = MainActivity.activityRef.get()?.adapter?.tabFragments?.isNotEmpty()
        ?: false
) {

    var show = showItems

    if (updateSearchMenu(menu, fragment)) {
        show = false
    }

    menu.apply {
        findItem(Id.action_save).isVisible = show
        findItem(Id.action_all).isVisible = show
        findItem(Id.action_print).isVisible = show
        findItem(Id.search).isVisible = show
        findItem(Id.share).isVisible = show
        findItem(Id.undo).isVisible = show
        findItem(Id.redo).isVisible = show
        findItem(Id.suggestions).isVisible = show
        findItem(Id.saveAs).isVisible = show
        findItem(Id.refreshEditor).isVisible = show
        findItem(Id.tools).isVisible = show
        findItem(Id.select_highlighting).isVisible = show
    }


    val isRunnable = withContext(Dispatchers.Default) {
        fragment?.file?.let { Runner.isRunnable(it) } == true
    }
    menu.findItem(Id.run).isVisible = isRunnable == true
}

private fun updateSearchMenu(menu: Menu, editorFragment: EditorFragment?): Boolean {
    val isSearching = editorFragment != null && (editorFragment.editor?.isSearching() == true)
    runOnUiThread {
        menu.apply {
            findItem(Id.search_next).isVisible = isSearching
            findItem(Id.search_previous).isVisible = isSearching
            findItem(Id.search_close).isVisible = isSearching
            findItem(Id.replace).isVisible = isSearching
        }
    }
    return isSearching
}

private suspend fun updateGitMenuVisibility(
    menu: Menu, editorFragment: EditorFragment?
) {
    if (editorFragment == null) {
        withContext(Dispatchers.Main) {
            menu.findItem(Id.git).isVisible = false
        }
        return
    }
    withContext(Dispatchers.IO) {
        if (editorFragment.file is FileWrapper) {
            val gitRoot = editorFragment.file?.let { findGitRoot((it as FileWrapper).file) }
            if (gitRoot != null) {
                MainActivity.activityRef.get()?.let {
                    GitClient.getCurrentBranch(it, gitRoot, onResult = { branch, err ->
                        runOnUiThread {
                            menu.findItem(Id.tools).subMenu?.findItem(Id.git)?.subMenu?.findItem(Id.action_branch)
                                ?.apply {
                                    title = "Branch : ${branch ?: "error"}"
                                }
                        }
                    })
                }
            }

            withContext(Dispatchers.Main) {
                menu.findItem(Id.git).isVisible =
                    gitRoot != null && MainActivity.activityRef.get()?.tabLayout!!.tabCount > 0
            }
        }

    }
}






