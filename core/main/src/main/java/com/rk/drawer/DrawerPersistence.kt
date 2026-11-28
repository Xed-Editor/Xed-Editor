package com.rk.drawer

import com.rk.activities.main.ui.fileTreeViewModel
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.filetree.FileTreeTab
import com.rk.resources.strings
import com.rk.utils.application
import com.rk.utils.logError
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DrawerPersistence {
    private val saveMutex = Mutex()

    private const val STATE_FILE = "drawerState.json"

    suspend fun saveState(viewModel: DrawerViewModel) {
        saveMutex.withLock {
            // Until restoreState() is done, the view model only holds its empty initial state, which must not replace
            // the saved one.
            if (!viewModel.stateRestored) return@withLock

            val filesDir = application?.filesDir ?: return@withLock

            val state =
                SavedDrawerState(
                    projects = viewModel.drawerTabs.value.mapNotNull { (it as? FileTreeTab)?.root?.toSavedFile() },
                    selectedProject = (viewModel.currentDrawerTab as? FileTreeTab)?.root?.toSavedFile(),
                    expandedNodes =
                        fileTreeViewModel.get()?.getExpandedNodes().orEmpty().mapNotNull { (root, nodes) ->
                            val savedRoot = root.toSavedFile() ?: return@mapNotNull null
                            SavedExpandedNodes(savedRoot, nodes.mapNotNull { it.toSavedFile() })
                        },
                )

            withContext(Dispatchers.IO) { filesDir.child(STATE_FILE).writeText(state.toJson()) }
        }
    }

    suspend fun restoreState(viewModel: DrawerViewModel) {
        saveMutex.withLock {
            if (viewModel.stateRestored) return@withLock

            val filesDir = application?.filesDir ?: return@withLock

            var complete = true
            try {
                val restored =
                    withContext(Dispatchers.IO) {
                        val state =
                            readState(filesDir) {
                                logError(it, "Could not migrate part of the drawer state")
                                complete = false
                            }
                        state.restore()
                    }

                // Update the existing state on Main thread
                withContext(Dispatchers.Main) {
                    viewModel.forcePushDrawerTabs(restored.tabs)
                    restored.selectedTab?.let { viewModel.selectDrawerTab(it) }
                    fileTreeViewModel.get()?.setExpandedNodes(restored.expandedNodes)
                }
            } catch (e: CancellationException) {
                // The restore was interrupted, the saved state is not broken. It runs again when the drawer is composed
                // again.
                throw e
            } catch (e: Throwable) {
                // Discard what cannot be restored, so it costs the state it held once instead of failing on every
                // start.
                logError(e, "Discarding drawer state that could not be restored")
                filesDir.child(STATE_FILE).delete()
                LegacyDrawerState.delete(filesDir)
                complete = false
            }

            // Also after a failure: unless saving resumes now, the drawer is not saved again while the app runs.
            viewModel.stateRestored = true

            if (!complete) toast(strings.project_restore_failed)
        }
    }

    /** Reads the saved state. The first time, this migrates the files that versions before the JSON format wrote. */
    private fun readState(filesDir: File, onMigrationError: (Throwable) -> Unit): SavedDrawerState {
        val stateFile = filesDir.child(STATE_FILE)
        if (stateFile.exists()) return SavedDrawerState.fromJson(stateFile.readText())
        if (!LegacyDrawerState.exists(filesDir)) return SavedDrawerState()

        val state = LegacyDrawerState.read(filesDir, onMigrationError)
        stateFile.writeText(state.toJson())
        LegacyDrawerState.delete(filesDir)
        return state
    }

    private class RestoredState(
        val tabs: List<FileTreeTab>,
        val selectedTab: FileTreeTab?,
        val expandedNodes: Map<FileObject, Set<FileObject>>,
    )

    /** Creates the tabs and file objects of this state. A file that cannot be restored is left out. */
    private fun SavedDrawerState.restore(): RestoredState {
        fun SavedFile.toFileObjectOrNull(): FileObject? =
            try {
                toFileObject()
            } catch (e: Exception) {
                logError(e, "Leaving out a file of the drawer state that cannot be restored")
                null
            }

        val tabs = projects.mapNotNull { it.toFileObjectOrNull() }.map { FileTreeTab(it) }
        val selectedRoot = selectedProject?.toFileObjectOrNull()

        return RestoredState(
            tabs = tabs,
            selectedTab = tabs.find { it.root == selectedRoot },
            expandedNodes =
                expandedNodes
                    .mapNotNull { (root, nodes) ->
                        val restoredRoot = root.toFileObjectOrNull() ?: return@mapNotNull null
                        restoredRoot to nodes.mapNotNull { it.toFileObjectOrNull() }.toSet()
                    }
                    .toMap(),
        )
    }
}
