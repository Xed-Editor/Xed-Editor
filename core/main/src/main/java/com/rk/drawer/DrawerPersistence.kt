package com.rk.drawer

import com.rk.activities.main.ui.fileTreeViewModel
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.resources.strings
import com.rk.utils.application
import com.rk.utils.logError
import com.rk.utils.readObject
import com.rk.utils.toast
import com.rk.utils.writeObject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DrawerPersistence {
    private val saveMutex = Mutex()

    private const val DRAWER_TABS = "drawerTabs"
    private const val CURRENT_DRAWER_TAB = "currentDrawerTab"
    private const val EXPANDED_FILE_TREE_NODES = "expandedFileTree"

    suspend fun saveState(viewModel: DrawerViewModel) {
        saveMutex.withLock {
            // Until restoreState() is done, the view model only holds its empty initial state, which must not replace
            // the saved one.
            if (!viewModel.stateRestored) return@withLock

            val filesDir = application?.filesDir ?: return@withLock

            val file = FileWrapper(filesDir.child(DRAWER_TABS))
            val serializableList = ArrayList(viewModel.drawerTabs.value)
            file.writeObject(serializableList)

            val currentTabFile = FileWrapper(filesDir.child(CURRENT_DRAWER_TAB))
            val currentTab = viewModel.currentDrawerTab
            if (currentTab != null) {
                currentTabFile.writeObject(currentTab)
            } else {
                currentTabFile.delete()
            }

            val expandedNodeFile = FileWrapper(filesDir.child(EXPANDED_FILE_TREE_NODES))
            fileTreeViewModel.get()?.getExpandedNodes()?.let { expandedNodeFile.writeObject(it) }
        }
    }

    suspend fun restoreState(viewModel: DrawerViewModel) {
        saveMutex.withLock {
            if (viewModel.stateRestored) return@withLock

            val filesDir = application?.filesDir ?: return@withLock

            val tabsRestored =
                restoreFile(filesDir.child(DRAWER_TABS)) { saved ->
                    val tabs = (saved as? List<*>)?.filterIsInstance<DrawerTab>().orEmpty()

                    // Update the existing state list on Main thread
                    withContext(Dispatchers.Main) { viewModel.forcePushDrawerTabs(tabs) }
                }

            val currentTabRestored =
                restoreFile(filesDir.child(CURRENT_DRAWER_TAB)) { saved ->
                    if (saved is DrawerTab) viewModel.selectDrawerTab(saved)
                }

            val expandedNodesRestored =
                restoreFile(filesDir.child(EXPANDED_FILE_TREE_NODES)) { saved ->
                    @Suppress("UNCHECKED_CAST") val expandedNodes = saved as? Map<FileObject, Set<FileObject>>
                    if (expandedNodes != null) fileTreeViewModel.get()?.setExpandedNodes(expandedNodes)
                }

            // Even if a file could not be restored: it has been discarded, and unless saving resumes now, the drawer is
            // not saved again while the app runs and the same failure comes back on every start.
            viewModel.stateRestored = true

            if (!tabsRestored || !currentTabRestored || !expandedNodesRestored) {
                toast(strings.project_restore_failed)
            }
        }
    }

    /**
     * Passes what was saved in [file] to [restore], or null if nothing was saved.
     *
     * A file that cannot be read or restored is deleted, so it costs the state it held once instead of failing on every
     * start.
     *
     * @return whether [file] could be restored.
     */
    private suspend fun restoreFile(file: File, restore: suspend (saved: Any?) -> Unit): Boolean =
        try {
            val saved =
                withContext(Dispatchers.IO) {
                    if (file.exists() && file.canRead()) FileWrapper(file).readObject() else null
                }
            restore(saved)
            true
        } catch (e: CancellationException) {
            // The restore was interrupted, the file is not broken. It runs again when the drawer is composed again.
            throw e
        } catch (e: Throwable) {
            logError(e, "Discarding drawer state that could not be restored from ${file.name}")
            file.delete()
            false
        }
}
