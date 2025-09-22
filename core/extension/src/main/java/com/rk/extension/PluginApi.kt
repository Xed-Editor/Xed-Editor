package com.rk.extension

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
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

    fun registerTab(id: String,tab: CustomTab) {
        Hooks.Editor.tabs[id] = tab
    }

    fun isTabRegistered(id: String): Boolean{
        return Hooks.Editor.tabs.containsKey(id)
    }

    fun openRegisteredTab(id: String) {
        if (isTabRegistered(id).not()){
            throw IllegalStateException("Tab with id : $id not registered")
            return
        }

        MainActivity.instance?.apply {
            lifecycleScope.launch{
                viewModel.newTab(Hooks.Editor.tabs[id]!!.tab)
            }
        }

    }

    fun unregisterTab(id: String) {
        Hooks.Editor.tabs.remove(id)
    }


    fun registerFileAction(
        action: Hooks.FileAction,
    ) {
        Hooks.FileAction.actions[action.id] = action
    }

    fun unregisterFileAction(id: String) {
        Hooks.FileAction.actions.remove(id)
    }


    fun registerControlItem(id: String, item: ControlItem) {
        Hooks.ControlItems.items[id] = item
    }

    fun unregisterControlItem(id: String) {
        Hooks.ControlItems.items.remove(id)
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
     * @param isFileRunnable Lambda: returns true if this runner is available for a given file.
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
        isFileRunnable: (FileObject) -> Boolean,
        builder: (FileObject, Context) -> RunnerImpl?
    ) {
        TODO()
    }

    /**
     * Unregisters a previously defined Runner.
     *
     * @param id The runner ID to remove.
     */
    fun unregisterRunner(id: String) {
        TODO()
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


    fun Context.getHostDrawable(name: String): Drawable? =
        getHostResource(this, name, ResourceType.DRAWABLE) as? Drawable

    fun Context.getHostMipmap(name: String): Drawable? =
        getHostResource(this, name, ResourceType.MIPMAP) as? Drawable

    fun Context.getHostString(name: String): String? =
        getHostResource(this, name, ResourceType.STRING) as? String

    fun Context.getHostColor(name: String): Int? =
        getHostResource(this, name, ResourceType.COLOR) as? Int

    fun Context.getHostRaw(name: String): InputStream? =
        getHostResource(this, name, ResourceType.RAW) as? InputStream

    fun Context.getHostLayoutId(name: String): Int? =
        getHostResource(this, name, ResourceType.LAYOUT) as? Int

    fun Context.getHostAnimId(name: String): Int? =
        getHostResource(this, name, ResourceType.ANIM) as? Int

    fun Context.getHostId(name: String): Int? =
        getHostResource(this, name, ResourceType.ID) as? Int

    fun Context.getHostDimenId(name: String): Int? =
        getHostResource(this, name, ResourceType.DIMEN) as? Int

    fun Context.getHostBoolId(name: String): Int? =
        getHostResource(this, name, ResourceType.BOOL) as? Int

    fun Context.getHostIntegerId(name: String): Int? =
        getHostResource(this, name, ResourceType.INTEGER) as? Int

    fun Context.getHostArrayId(name: String): Int? =
        getHostResource(this, name, ResourceType.ARRAY) as? Int

    fun Context.getHostStyleId(name: String): Int? =
        getHostResource(this, name, ResourceType.STYLE) as? Int

    fun Context.getHostXmlId(name: String): Int? =
        getHostResource(this, name, ResourceType.XML) as? Int


}