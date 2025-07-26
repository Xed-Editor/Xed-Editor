package com.rk.pluginApi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.rk.controlpanel.ControlItem
import com.rk.extension.Extension
import com.rk.extension.Hooks
import com.rk.extension.SettingsScreen
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.errorDialog
import com.rk.libcommons.localDir
import com.rk.runner.RunnerImpl
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream


/**
 * Xed Editor Plugin API
 *
 * The PluginApi object provides a unified set of methods for plugin authors to register,
 * modify, and interact with the Xed Editor extension points.
 *
 * Typical usage: In your plugin's initialization, call `register*`
 */
object PluginApi {
    /**
     * Registers a custom editor tab that can be opened like a normal document tab.
     *
     * @param id Unique string ID for this tab type. Example: "myplugin-mytab-123"
     * @param tabName The 'file name' for the tab. Used to match open requests.
     * @param builder Lambda to create a CoreFragment when this custom tab type is opened.
     *
     */
    fun registerTab(id: String, tabName: String, builder: (TabFragment) -> CoreFragment) {
        Hooks.Editor.tabs[id] = { file, tabFragment ->
            if (file.getName() == tabName && file.getAbsolutePath() == localDir().child("customTabs/$id/$tabName").absolutePath) {
                builder(tabFragment)
            } else {
                //let xed handle this
                null
            }
        }
    }

    /**
     * Opens a registered custom tab by its plugin-defined ID.
     *
     * @param id The unique ID used when registering the tab (see registerTab).
     *
     * Example:
     * ```
     * PluginApi.openRegisteredTab("myplugin-mytab-123")
     * ```
     */

    fun isTabRegistered(id: String): Boolean{
        return Hooks.Editor.tabs.containsKey(id)
    }

    fun openRegisteredTab(id: String, tabName: String) {
        if (isTabRegistered(id).not()){
            errorDialog("PluginApi Error \nsid : $id is not registered")
            return
        }
        val file = FileWrapper(localDir().child("customTabs/$id/${tabName}").createFileIfNot())

        MainActivity.withContext {
            lifecycleScope.launch(Dispatchers.Main) {
                adapter!!.addFragment(file)
            }
        }
    }

    /**
     * Unregisters a custom tab type previously registered via registerTab.
     *
     * @param id The unique tab ID to remove.
     */
    fun unregisterTab(id: String, tabName: String) {
        localDir().child("customTabs/$id/${tabName}").createFileIfNot().delete()
        Hooks.Editor.tabs.remove(id)
    }

    /**
     * Registers a callback invoked whenever a new tab (document or custom) is created.
     *
     * @param id Unique ID for this event handler.
     * @param handler Lambda receiving the file object and tab fragment.
     *
     * Example:
     * ```
     * PluginApi.registerOnTabCreate("myplugin.onTabCreated") { file, tab ->
     *     // Track opened files or setup tab-specific state
     * }
     * ```
     */
    fun registerOnTabCreate(id: String, handler: (FileObject, TabFragment) -> Unit) {
        Hooks.Editor.onTabCreate[id] = handler
    }

    /**
     * Unregisters a tab creation callback by its unique handler ID.
     *
     * @param id The handler ID to remove.
     */
    fun unregisterOnTabCreate(id: String) {
        Hooks.Editor.onTabCreate.remove(id)
    }

    /**
     * Registers a callback invoked when a tab is destroyed.
     *
     * @param id Unique handler ID.
     * @param handler Lambda receiving the related file object.
     */
    fun registerOnTabDestroyed(id: String, handler: (FileObject) -> Unit) {
        Hooks.Editor.onTabDestroyed[id] = handler
    }

    /**
     * Unregisters a tab destruction handler.
     *
     * @param id The handler ID to remove.
     */
    fun unregisterOnTabDestroyed(id: String) {
        Hooks.Editor.onTabDestroyed.remove(id)
    }

    /**
     * Registers a callback invoked when a tab is closed.
     *
     * @param id Unique handler ID.
     * @param handler Lambda receiving the related file object.
     */
    fun registerOnTabClosed(id: String, handler: (FileObject) -> Unit) {
        Hooks.Editor.onTabClosed[id] = handler
    }

    /**
     * Unregisters a tab closed handler.
     *
     * @param id The handler ID to remove.
     */
    fun unregisterOnTabClosed(id: String) {
        Hooks.Editor.onTabClosed.remove(id)
    }

    /**
     * Registers a callback to be invoked when all tabs are cleared.
     *
     * @param id Unique handler ID.
     * @param handler Lambda with no parameters.
     */
    fun registerOnTabCleared(id: String, handler: () -> Unit) {
        Hooks.Editor.onTabCleared[id] = handler
    }

    /**
     * Unregisters an onTabCleared handler.
     *
     * @param id The handler ID to remove.
     */
    fun unregisterOnTabCleared(id: String) {
        Hooks.Editor.onTabCleared.remove(id)
    }

    /**
     * Adds a custom file action to the file browser or tab popup menu.
     *
     * @param id Unique action ID. Use a plugin-specific prefix, e.g. "myplugin.open".
     * @param title The menu item text shown to the user.
     * @param description Optional short description or subtitle.
     * @param icon Drawable icon to show with menu item (may be null).
     * @param shouldAttach Lambda returning true to show this action for a given FileObject.
     * @param onClick Callback when the menu item is clicked; receives the View and FileAction.
     *
     * * Example:
     *      * ```
     *      * PluginApi.registerFileAction(
     *      *     id = "myplugin.openWithSpecial",
     *      *     title = "Open with MyTool",
     *      *     description = "Process file with MyTool",
     *      *     icon = context.getDrawable(R.drawable.ic_mytool),
     *      *     shouldAttach = { file -> file.extension == "xyz" },
     *      *     onClick = { view, fileAction -> /* launch your logic */ }
     *      * )
     *      * ```
     *      */
    fun registerFileAction(
        id: String, title: String?,
        description: String?,
        icon: Drawable?,
        shouldAttach: (FileObject) -> Boolean,
        onClick: (View, FileAction) -> Unit
    ) {

        Hooks.FileActions.actionPopupHook[id] = { fileAction ->
            if (shouldAttach.invoke(fileAction.file)) {
                addItem(title = title, description = description, icon = icon, listener = {
                    onClick.invoke(it, fileAction)
                })
            }
        }
    }

    /**
     * Removes a file action previously registered for the file browser popup.
     *
     * @param id The action ID to unregister.
     */
    fun unregisterFileAction(id: String) {
        Hooks.FileActions.actionPopupHook.remove(id)
    }

    /**
     * Registers a custom control item for the editor's control panel or toolbar.
     *
     * @param id Unique control item ID.
     * @param item The ControlItem to show.
     *
     * Example:
     * ```
     * PluginApi.registerControlItem("myplugin.debugBtn", MyDebugButton())
     * ```
     */
    fun registerControlItem(id: String, item: ControlItem) {
        Hooks.ControlPanel.controlItems[id] = item
    }

    /**
     * Removes a previously registered control panel item.
     *
     * @param id The control item ID to remove.
     */
    fun unregisterControlItem(id: String) {
        Hooks.ControlPanel.controlItems.remove(id)
    }

    /**
     * Adds a preferences/settings screen for your plugin to the editor’s settings.
     *
     * @param id Unique settings screen ID.
     * @param screen The settings UI (SettingsScreen instance).
     *
     * Example:
     * ```
     * PluginApi.registerSettingsScreen("myplugin.settings", MySettingsScreen())
     * ```
     */
    fun registerSettingsScreen(id: String, screen: SettingsScreen) {
        Hooks.Settings.screens[id] = screen
    }

    /**
     * Removes the plugin's settings/preferences screen.
     *
     * @param id The settings screen ID to unregister.
     */
    fun unregisterSettingsScreen(id: String) {
        Hooks.Settings.screens.remove(id)
    }

    /**
     * Registers a custom Runner for building, running, or testing files/projects via UI.
     *
     * @param id Unique runner ID (use namespaced string, e.g., "myplugin.runner").
     * @param isSupported Lambda: returns true if this runner is available for a given file.
     * @param builder Lambda: builds/returns the RunnerImpl that executes when user runs files.
     *
     * Example:
     * ```
     * PluginApi.registerRunner(
     *   id = "myplugin.pythonRunner",
     *   isSupported = { file -> file.extension == "py" },
     *   builder = { file, ctx -> MyPythonRunner(file, ctx) }
     * )
     * ```
     */
    fun registerRunner(
        id: String,
        isSupported: (FileObject) -> Boolean,
        builder: (FileObject, Context) -> RunnerImpl?
    ) {
        Hooks.Runner.runners[id] = Pair(isSupported, builder)
    }

    /**
     * Unregisters a previously defined Runner.
     *
     * @param id The runner ID to remove.
     */
    fun unregisterRunner(id: String) {
        Hooks.Runner.runners.remove(id)
    }

    /**
     * Enum representing supported Android resource types.
     *
     * Use this enum to specify the type of resource you want to retrieve from either
     * an external APK or the host app itself.
     *
     * Example:
     * ```
     * val icon = PluginApi.getHostResource(context, "ic_launcher", ResourceType.DRAWABLE) as? Drawable
     * ```
     */
    enum class ResourceType(val typeName: String) {
        DRAWABLE("drawable"),
        MIPMAP("mipmap"),
        STRING("string"),
        COLOR("color"),
        LAYOUT("layout"),
        RAW("raw"),
        ANIM("anim"),
        ID("id"),
        DIMEN("dimen"),
        BOOL("bool"),
        INTEGER("integer"),
        ARRAY("array"),
        STYLE("style"),
        XML("xml")
    }


    /**
     * Loads a resource from an extension.
     *
     * @param extension The extension object that includes the target APK file and package name.
     * @param context Context from the host application.
     * @param name Name of the resource (e.g., "ic_launcher", "my_string").
     * @param type Type of the resource to load (from [ResourceType]).
     *
     * @return The loaded resource. Type depends on the resource:
     * - Drawable for DRAWABLE/MIPMAP
     * - String for STRING
     * - Int for COLOR
     * - InputStream for RAW
     * - Resource ID (Int) for other types
     * - null if not found or on error
     */
    @SuppressLint("DiscouragedApi")
    fun getResource(
        extension: Extension,
        context: Context,
        name: String,
        type: ResourceType,
    ): Any? {
        try {
            val resources: Resources
            val packageName: String = extension.packageName
            val apkPath = extension.apkFile.absolutePath

                val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
                AssetManager::class.java
                    .getMethod("addAssetPath", String::class.java)
                    .invoke(assetManager, apkPath)

                @Suppress("DEPRECATION")
                resources = Resources(
                    assetManager,
                    context.resources.displayMetrics,
                    context.resources.configuration
                )

            val resId = resources.getIdentifier(name, type.typeName, packageName)
            if (resId == 0) return null

            return when (type) {
                ResourceType.DRAWABLE, ResourceType.MIPMAP -> ResourcesCompat.getDrawable(resources, resId, context.theme)
                ResourceType.STRING -> resources.getString(resId)
                ResourceType.COLOR -> ResourcesCompat.getColor(resources, resId, context.theme)
                ResourceType.RAW -> resources.openRawResource(resId)
                ResourceType.LAYOUT,
                ResourceType.ANIM,
                ResourceType.ID,
                ResourceType.DIMEN,
                ResourceType.BOOL,
                ResourceType.INTEGER,
                ResourceType.ARRAY,
                ResourceType.STYLE,
                ResourceType.XML -> resId
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Loads a resource from the host application without using the R class.
     *
     * This is useful when dynamically accessing resources in your own app via resource name,
     * especially from plugins or scripts.
     *
     * @param context Host app context.
     * @param name Name of the resource to load (e.g., "ic_launcher", "welcome_message").
     * @param type Type of the resource (from [ResourceType]).
     *
     * @return The loaded resource. Type depends on the resource:
     * - Drawable for DRAWABLE/MIPMAP
     * - String for STRING
     * - Int for COLOR or resource ID
     * - InputStream for RAW
     * - null if not found or on error
     */
    @SuppressLint("DiscouragedApi")
    fun getHostResource(
        context: Context,
        name: String,
        type: ResourceType
    ): Any? {
        return try {
            val resources = context.resources
            val packageName = context.packageName

            val resId = resources.getIdentifier(name, type.typeName, packageName)
            if (resId == 0) return null

            when (type) {
                ResourceType.DRAWABLE, ResourceType.MIPMAP -> ResourcesCompat.getDrawable(resources, resId, context.theme)
                ResourceType.STRING -> resources.getString(resId)
                ResourceType.COLOR -> ResourcesCompat.getColor(resources, resId, context.theme)
                ResourceType.RAW -> resources.openRawResource(resId)
                ResourceType.LAYOUT,
                ResourceType.ANIM,
                ResourceType.ID,
                ResourceType.DIMEN,
                ResourceType.BOOL,
                ResourceType.INTEGER,
                ResourceType.ARRAY,
                ResourceType.STYLE,
                ResourceType.XML -> resId
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    fun Extension.getDrawable(context: Context, name: String): Drawable? =
        PluginApi.getResource(this, context, name, ResourceType.DRAWABLE) as? Drawable

    fun Extension.getMipmap(context: Context, name: String): Drawable? =
        PluginApi.getResource(this, context, name, ResourceType.MIPMAP) as? Drawable

    fun Extension.getString(context: Context, name: String): String? =
        PluginApi.getResource(this, context, name, ResourceType.STRING) as? String

    fun Extension.getColor(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.COLOR) as? Int

    fun Extension.getRaw(context: Context, name: String): InputStream? =
        PluginApi.getResource(this, context, name, ResourceType.RAW) as? InputStream

    fun Extension.getLayoutId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.LAYOUT) as? Int

    fun Extension.getAnimId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.ANIM) as? Int

    fun Extension.getId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.ID) as? Int

    fun Extension.getDimenId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.DIMEN) as? Int

    fun Extension.getBoolId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.BOOL) as? Int

    fun Extension.getIntegerId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.INTEGER) as? Int

    fun Extension.getArrayId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.ARRAY) as? Int

    fun Extension.getStyleId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.STYLE) as? Int

    fun Extension.getXmlId(context: Context, name: String): Int? =
        PluginApi.getResource(this, context, name, ResourceType.XML) as? Int

    ///////////////////////////////////////////////////////////////////////////

    fun Context.getHostDrawable(name: String): Drawable? =
        PluginApi.getHostResource(this, name, ResourceType.DRAWABLE) as? Drawable

    fun Context.getHostMipmap(name: String): Drawable? =
        PluginApi.getHostResource(this, name, ResourceType.MIPMAP) as? Drawable

    fun Context.getHostString(name: String): String? =
        PluginApi.getHostResource(this, name, ResourceType.STRING) as? String

    fun Context.getHostColor(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.COLOR) as? Int

    fun Context.getHostRaw(name: String): InputStream? =
        PluginApi.getHostResource(this, name, ResourceType.RAW) as? InputStream

    fun Context.getHostLayoutId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.LAYOUT) as? Int

    fun Context.getHostAnimId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.ANIM) as? Int

    fun Context.getHostId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.ID) as? Int

    fun Context.getHostDimenId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.DIMEN) as? Int

    fun Context.getHostBoolId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.BOOL) as? Int

    fun Context.getHostIntegerId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.INTEGER) as? Int

    fun Context.getHostArrayId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.ARRAY) as? Int

    fun Context.getHostStyleId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.STYLE) as? Int

    fun Context.getHostXmlId(name: String): Int? =
        PluginApi.getHostResource(this, name, ResourceType.XML) as? Int


}

