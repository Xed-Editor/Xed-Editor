package com.rk.activities.main

import com.rk.file.child
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import com.rk.utils.application
import com.rk.utils.toast
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Represents the state of a user's session, which can be serialized and saved. This allows for restoring the open tabs
 * and their states when the application is restarted.
 *
 * @property tabStates A list containing the state of each open tab. Each element in the list is a [TabState] object,
 *   which holds the specific information needed to restore a single tab.
 * @property currentTabIndex The index of the tab that was active when the session was saved. This is used to restore
 *   the user's focus to the correct tab.
 */
data class SessionState(val tabStates: List<TabState>, val currentTabIndex: Int) : Serializable

/**
 * Manages the saving and loading of the user's session state.
 *
 * This singleton object is responsible for persisting the state of open tabs and the currently selected tab to a cache
 * file. This allows the application to restore the previous session when it is restarted.
 *
 * Session state is stored in a file named "session" within the application's cache directory. Operations are
 * synchronized using a [kotlinx.coroutines.sync.Mutex] to ensure thread safety.
 */
object SessionManager {
    val mutex = Mutex()
    var preloadedSession: SessionState? = null
    var tabCacheFile = application!!.filesDir.child("session")

    suspend fun preloadSession() =
        mutex.withLock {
            runCatching {
                    if (tabCacheFile.exists() && tabCacheFile.canRead()) {
                        ObjectInputStream(FileInputStream(tabCacheFile)).use { ois ->
                            preloadedSession = ois.readObject() as? SessionState
                        }
                    }
                }
                .onFailure { it.printStackTrace() }
        }

    suspend fun saveSession(tabs: List<Tab>, currentTabIndex: Int) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                        val tabStates = tabs.mapNotNull { it.getState() }
                        val sessionState = SessionState(tabStates, currentTabIndex)

                        ObjectOutputStream(FileOutputStream(tabCacheFile)).use { oos -> oos.writeObject(sessionState) }
                    }
                    .onFailure {
                        it.printStackTrace()
                        toast(strings.save_tabs_error)
                    }
            }
        }
}
