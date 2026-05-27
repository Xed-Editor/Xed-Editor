package com.rk.activities.main

import com.google.gson.*
import com.rk.file.child
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import com.rk.utils.application
import com.rk.utils.toast
import java.io.File
import java.lang.reflect.Type
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
data class SessionState(val tabStates: List<TabState>, val currentTabIndex: Int)

class TabStateAdapter : JsonSerializer<TabState>, JsonDeserializer<TabState> {
    override fun serialize(src: TabState, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonElement = when (src) {
            is EditorTabState -> context.serialize(src, EditorTabState::class.java)
            is FileTabState -> context.serialize(src, FileTabState::class.java)
        }
        jsonElement.asJsonObject.addProperty("type", src.javaClass.simpleName)
        return jsonElement
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TabState {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("type").asString
        return when (type) {
            "EditorTabState" -> context.deserialize(json, EditorTabState::class.java)
            "FileTabState" -> context.deserialize(json, FileTabState::class.java)
            else -> throw JsonParseException("Unknown type: $type")
        }
    }
}

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
    private val gson = GsonBuilder()
        .registerTypeAdapter(TabState::class.java, TabStateAdapter())
        .create()
        
    val mutex = Mutex()
    var preloadedSession: SessionState? = null
    var tabCacheFile = application!!.filesDir.child("session.json")

    suspend fun preloadSession() =
        mutex.withLock {
            runCatching {
                    if (tabCacheFile.exists() && tabCacheFile.canRead()) {
                        val json = tabCacheFile.readText()
                        preloadedSession = gson.fromJson(json, SessionState::class.java)
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
                        val json = gson.toJson(sessionState)
                        tabCacheFile.writeText(json)
                    }
                    .onFailure {
                        it.printStackTrace()
                        toast(strings.save_tabs_error)
                    }
            }
        }
}
