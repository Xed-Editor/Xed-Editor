package com.rk.events

import com.rk.drawer.DrawerTab
import com.rk.editor.Editor
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.icons.pack.LocalIconPack
import com.rk.lsp.LspConnectionStatus
import com.rk.lsp.LspLogEntry
import com.rk.lsp.LspServerInstance
import com.rk.settings.debugOptions.LogEntry
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import com.rk.theme.ThemeHolder
import com.rk.utils.logError
import java.util.Locale
import kotlin.reflect.KClass

/** Base interface for all events in the application. */
interface Event

/** Events related to the drawer and its tabs. */
sealed interface DrawerEvent : Event {

    /** Event triggered when a drawer tab has been added. */
    data class TabAdded(val tab: DrawerTab) : DrawerEvent

    /** Event triggered when a drawer tab has been removed. */
    data class TabRemoved(val tab: DrawerTab) : DrawerEvent

    /** Event triggered when the active drawer tab changes. */
    data class TabSelected(val tab: DrawerTab?) : DrawerEvent

    /** Event triggered when the registered service tabs have been initialized. */
    data class ServicesInitialized(val tabs: List<DrawerTab>) : DrawerEvent

    /** Event triggered when the active service tab changes. */
    data class ServiceTabSelected(val tab: DrawerTab?) : DrawerEvent
}

/** Events related to the file tree and its nodes. */
sealed interface FileTreeEvent : Event {

    /** Event triggered when a node in the file tree is expanded. */
    data class NodeExpanded(
        val projectRoot: FileObject,
        val path: FileObject,
    ) : FileTreeEvent

    /** Event triggered when a node in the file tree is collapsed. */
    data class NodeCollapsed(
        val projectRoot: FileObject,
        val path: FileObject,
    ) : FileTreeEvent

    /** Event triggered when a node in the file tree becomes focused. */
    data class Focused(
        val projectRoot: FileObject,
        val path: FileObject,
    ) : FileTreeEvent

    /** Event triggered when the selection in the file tree changes. */
    data class SelectionChanged(
        val projectRoot: FileObject,
        val selected: List<FileObject>,
    ) : FileTreeEvent

    /** Event triggered when a file tree drawer tab has been selected. */
    data class Opened(val projectRoot: FileObject) : FileTreeEvent

    /** Event triggered when a file tree drawer tab has been closed. */
    data class Closed(val projectRoot: FileObject) : FileTreeEvent

    /**
     * Event triggered when the file tree structure has been updated/synchronized with the file system. This can occur
     * after files have been moved or the refresh button has been pressed.
     *
     * **NOTE:** This event is not triggered on initial file tree load.
     */
    data class TreeSynchronized(val parent: FileObject) : FileTreeEvent
}

/** Events related to file system operations. */
sealed interface FileEvent : Event {

    /** Event triggered when a new file or directory is created. */
    data class Created(val file: FileObject) : FileEvent

    /** Event triggered when a file or directory is deleted. */
    data class Deleted(val path: String) : FileEvent

    /** Event triggered when a file or directory is renamed. */
    data class Renamed(
        val file: FileObject,
        val oldPath: String,
    ) : FileEvent

    /** Event triggered when a file or directory is moved. */
    data class Moved(
        val file: FileObject,
        val oldPath: String,
    ) : FileEvent

    /** Event triggered when a file or directory is copied. */
    data class Copied(
        val file: FileObject,
        val sourcePath: String,
    ) : FileEvent
}

/**
 * Events related to tab lifecycle and interaction.
 *
 * @see EditorTabEvent
 */
sealed interface TabEvent : Event {

    /** Event triggered when a tab is opened. */
    data class Opened(val tab: Tab) : TabEvent

    /** Event triggered when a tab is closed. */
    data class Closed(val tab: Tab) : TabEvent

    /** Event triggered when tabs are reordered. */
    data class Reordered(
        val tab: Tab,
        val from: Int,
        val to: Int,
    ) : TabEvent

    /** Event triggered when a tab is selected. */
    data class Selected(val tab: Tab) : TabEvent
}

/**
 * Events specifically related to editor tabs.
 *
 * @see TabEvent
 */
sealed interface EditorTabEvent : Event {

    /** Event triggered when an editor tab is refreshed. */
    data class Refreshed(val tab: EditorTab) : EditorTabEvent

    /** Event triggered when the content of an editor tab is saved. */
    data class Saved(val tab: EditorTab, val file: FileObject, val quickSave: Boolean) : EditorTabEvent

    /** Event triggered when an editor tab is opened. */
    data class Opened(val tab: EditorTab) : EditorTabEvent

    /** Event triggered when an editor tab is closed. */
    data class Closed(val tab: EditorTab) : EditorTabEvent

    /** Event triggered when editor tabs are reordered. */
    data class Reordered(
        val tab: EditorTab,
        val from: Int,
        val to: Int,
    ) : EditorTabEvent

    /** Event triggered when an editor tab is selected. */
    data class Selected(val tab: EditorTab) : EditorTabEvent
}

/** Events related to the editor instances. */
sealed interface EditorEvent : Event {
    /** Event triggered when a new editor instance is created. */
    data class InstanceCreated(val editor: Editor) : EditorEvent

    /** Event triggered when an editor instance is destroyed. */
    data class InstanceDestroyed(val editor: Editor) : EditorEvent
}

/** Events related to Language Server Protocol (LSP) operations. */
sealed interface LSPEvent : Event {

    /** Event triggered when an LSP server instance is created. */
    data class InstanceCreated(val instance: LspServerInstance) : LSPEvent

    /** Event triggered when the status of an LSP server instance changes. */
    data class StatusChanged(
        val instance: LspServerInstance,
        val newStatus: LspConnectionStatus,
        val oldStatus: LspConnectionStatus,
    ) : LSPEvent

    /** Event triggered when a log entry is written by an LSP server. */
    data class LogEntryWritten(val instance: LspServerInstance, val logEntry: LspLogEntry) : LSPEvent
}

/** General application-level events. */
sealed interface AppEvent : Event {

    /** Event triggered when the application theme changes. */
    data class ThemeChanged(val newTheme: ThemeHolder, val oldTheme: ThemeHolder?) : AppEvent

    /** Event triggered when the application icon pack changes. */
    data class IconPackChanged(val newIconPack: LocalIconPack?, val oldIconPack: LocalIconPack?) : AppEvent

    /** Event triggered when the application language changes. */
    data class LanguageChanged(val newLanguage: Locale, val oldLanguage: Locale?) : AppEvent

    /** Event triggered when a log entry is written to the application debug logs. */
    data class LogEntryWritten(val logEntry: LogEntry, val extensionId: String?) : AppEvent
}

/** Represents a subscription to an event. */
interface EventSubscription {
    /** Unsubscribes from the event. */
    fun unsubscribe()
}

/** Central event bus for the application. */
object Events {

    @PublishedApi internal val listeners = mutableMapOf<KClass<out Event>, MutableList<suspend (Event) -> Unit>>()

    /**
     * Subscribes to events of type [T].
     *
     * @param T The type of [Event] to subscribe to.
     * @param listener The listener function to be called when the event is published.
     * @return An [EventSubscription] object that can be used to unsubscribe.
     */
    @XedExtensionPoint
    inline fun <reified T : Event> subscribe(noinline listener: suspend (T) -> Unit): EventSubscription {
        val list = listeners.getOrPut(T::class) { mutableListOf() }

        val wrapper: suspend (Event) -> Unit = {
            listener(it as T)
        }

        list += wrapper

        return object : EventSubscription {
            override fun unsubscribe() {
                list -= wrapper
            }
        }
    }

    /**
     * Triggers an event for all subscribed listeners.
     *
     * @param event The event to trigger.
     */
    suspend fun publish(event: Event) {
        listeners
            .filterKeys { it.isInstance(event) }
            .values
            .flatten()
            .forEach { listener ->
                try {
                    listener(event)
                } catch (t: Throwable) {
                    logError(t, "Listener failed for ${event::class.simpleName}")
                }
            }
    }
}
