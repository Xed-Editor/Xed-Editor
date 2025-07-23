package com.rk.xededitor.MainActivity.handlers

import android.view.Menu
import androidx.annotation.OptIn
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.x
import com.rk.runner.Runner
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.getCurrentEditorFragment
import com.rk.xededitor.R
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures
import com.rk.xededitor.ui.screens.terminal.isV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.indexOf

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
        menu.findItem(R.id.terminal).isVisible = InbuiltFeatures.terminal.state.value
    }
}

@OptIn(ExperimentalBadgeUtils::class)
private suspend fun updateEditor(
    fragment: EditorFragment?,
    menu: Menu,
    showItems: Boolean = fragment != null && MainActivity.activityRef.get()?.adapter?.tabFragments?.isNotEmpty() == true
) {

    var show = showItems

    if (updateSearchMenu(menu, fragment)) {
        show = false
    }


    menu.apply {
        findItem(Id.action_save).apply {
            val currentFragment = getCurrentEditorFragment()
            var showBadge = currentFragment?.isModified() == true

            currentFragment?.apply {
                MainActivity.activityRef.get()?.tabViewModel?.apply {
                    val index = fragmentFiles.indexOf(file)
                    val currentTitle = fragmentTitles[index]

                    showBadge = currentTitle.endsWith("*")
                }
            }

            MainActivity.withContext {
                badge?.let {
                    if (show && showBadge) {
                        BadgeUtils.attachBadgeDrawable(it, binding!!.toolbar, R.id.action_save)
                    } else {
                        BadgeUtils.detachBadgeDrawable(it, binding!!.toolbar, R.id.action_save)
                    }
                }

                if (isV) {
                    x(tabViewModel.fragmentFiles, tabViewModel.fragmentFiles.size)
                }
            }
            isVisible = show
        }
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
        findItem(Id.toggle_word_wrap).isVisible = show
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






