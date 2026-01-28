package com.rk.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.rk.filetree.SortMode
import com.rk.settings.editor.DEFAULT_ACTION_ITEMS
import com.rk.settings.editor.DEFAULT_EXTRA_KEYS_COMMANDS
import com.rk.settings.editor.DEFAULT_EXTRA_KEYS_SYMBOLS
import com.rk.theme.blueberry
import com.rk.utils.application
import com.rk.utils.hasHardwareKeyboard
import com.rk.xededitor.BuildConfig
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps settings in `MutableState`s for reactive UI updates. This is necessary because the static `Settings` object,
 * which reads from SharedPreferences, will not trigger a recomposition when its values change.
 */
object ReactiveSettings {
    var toolbarActionIds by mutableStateOf(Settings.action_items)
    var showExtraKeys by mutableStateOf(Settings.show_extra_keys)
    var splitExtraKeys by mutableStateOf(Settings.split_extra_keys)
    var extraKeyCommandIds by mutableStateOf(Settings.extra_keys_commands)
    var extraKeySymbols by mutableStateOf(Settings.extra_keys_symbols)
    var extraKeysBackground by mutableStateOf(Settings.extra_keys_bg)
    var showHiddenFilesDrawer by mutableStateOf(Settings.show_hidden_files_drawer)

    fun update() {
        toolbarActionIds = Settings.action_items
        showExtraKeys = Settings.show_extra_keys
        splitExtraKeys = Settings.split_extra_keys
        extraKeyCommandIds = Settings.extra_keys_commands
        extraKeySymbols = Settings.extra_keys_symbols
        extraKeysBackground = Settings.extra_keys_bg
        showHiddenFilesDrawer = Settings.show_hidden_files_drawer
    }
}

// NOTE: USE snake_case FOR KEYS!
object Settings {
    var read_only_default by CachedPreference("read_only_default", false)
    var shown_disclaimer by CachedPreference("shown_disclaimer", false)
    var amoled by CachedPreference("amoled", false)
    var monet by CachedPreference("monet", false)
    var pin_line_number by CachedPreference("pin_line_number", false)
    var word_wrap_text by CachedPreference("word_wrap_text", true)
    var word_wrap by CachedPreference("word_wrap", false)
    var restore_sessions by CachedPreference("restore_sessions", true)
    var cursor_animation by CachedPreference("cursor_animation", true)
    var show_extra_keys by CachedPreference("show_extra_keys", hasHardwareKeyboard(application!!).not())
    var keep_drawer_locked by CachedPreference("drawer_lock", false)
    var show_line_numbers by CachedPreference("show_line_number", true)
    var render_whitespace by CachedPreference("render_whitespace", false)
    var sticky_scroll by CachedPreference("sticky_scroll", true)
    var quick_deletion by CachedPreference("fast_delete", true)
    var auto_save by CachedPreference("auto_save", false)
    var show_suggestions by CachedPreference("show_suggestions", false)
    var check_for_update by CachedPreference("check_update", false)
    var is_selected_font_asset by CachedPreference("is_font_asset", false)
    var smooth_tabs by CachedPreference("smooth_tab", false)
    var actual_tabs by CachedPreference("actual_tab", false)
    var scroll_to_bottom by CachedPreference("scroll_to_bottom", false)
    var hide_soft_keyboard_if_hardware by CachedPreference("always_show_soft_keyboard", true)
    var ignore_storage_permission by CachedPreference("ignore_storage_permission", false)
    var github by CachedPreference("github", true)
    var has_shown_private_data_dir_warning by CachedPreference("has_shown_private_data_dir_warning", false)
    var has_shown_terminal_dir_warning by CachedPreference("has_shown_terminal_dir_warning", false)
    var anr_watchdog by CachedPreference("anr", BuildConfig.DEBUG)
    var strict_mode by CachedPreference("strict_mode", BuildConfig.DEBUG)
    var expose_home_dir by CachedPreference("expose_home_dir", false)
    var verbose_error by CachedPreference("verbose_error", BuildConfig.DEBUG)
    var project_as_pwd by CachedPreference("project_as_pwd", true)
    var donated by CachedPreference("donated", false)
    var sandbox by CachedPreference("sandbox", true)
    var terminal_virus_notice by CachedPreference("terminal_virus_notice", false)
    var textmate_suggestions by CachedPreference("textmate_suggestions", true)
    var seccomp by CachedPreference("seccomp", false)
    var desktop_mode by CachedPreference("desktop_mode", false)
    var theme_flipper by CachedPreference("theme_flipper", false)
    var format_on_save by CachedPreference("format_on_save", false)
    var show_hidden_files_drawer by CachedPreference("show_hidden_files_drawer", true)
    var show_hidden_files_search by CachedPreference("show_hidden_files_search", false)
    var show_tab_icons by CachedPreference("show_tab_icons", true)
    var split_extra_keys by CachedPreference("split_extra_keys", false)
    var extra_keys_bg by CachedPreference("extra_keys_bg", false)
    var auto_open_new_files by CachedPreference("auto_open_new_files", true)
    var enable_html_runner by CachedPreference("enable_html_runner", true)
    var enable_md_runner by CachedPreference("enable_md_runner", true)
    var enable_universal_runner by CachedPreference("enable_universal_runner", true)
    var http_server_port by CachedPreference("http_server_port", 8357)
    var launch_in_browser by CachedPreference("launch_in_browser", false)
    var inject_eruda by CachedPreference("inject_eruda", true)
    var auto_close_tags by CachedPreference("auto_close_tags", true)
    var bullet_continuation by CachedPreference("bullet_continuation", true)
    var insert_final_newline by CachedPreference("insert_final_newline", true)
    var trim_trailing_whitespace by CachedPreference("trim_trailing_whitespace", true)
    var enable_editorconfig by CachedPreference("enable_editorconfig", true)
    var git_recursive_submodules by CachedPreference("git_recursive_submodules", true)

    // Int settings
    var tab_size by CachedPreference("tab_size", 4)
    var editor_text_size by CachedPreference("text_size", 14)
    var theme_mode by CachedPreference("default_night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    var terminal_font_size by CachedPreference("terminal_font_size", 13)

    var user_declined_value by CachedPreference("user_declined_value", false)
    var user_said_maybe_later by CachedPreference("user_said_maybe_later", false)
    var user_has_supported by CachedPreference("user_has_supported", false)
    var donation_ask_count by CachedPreference("donation_ask_count", 0)
    var saves by CachedPreference("saves", 0)
    var runs by CachedPreference("runs", 0)
    var last_donation_dialog_timestamp by CachedPreference("last_donation_dialog_timestamp", 0L)
    var sort_mode by CachedPreference("sort_mode", SortMode.SORT_BY_NAME.ordinal)

    // String settings
    var selected_project by CachedPreference("selected_project", "")
    var font_gson by CachedPreference("selected_font", "")
    var theme by CachedPreference("theme", blueberry.id)
    var icon_pack: String by CachedPreference("icon_pack", "")
    var selected_font_path by CachedPreference("selected_font_path", "")
    var encoding: String by CachedPreference("encoding", Charset.defaultCharset().name())
    var line_ending by CachedPreference("line_ending", "lf")
    var current_lang: String by
        CachedPreference("current_lang", application!!.resources.configuration.locales[0].language)
    var extra_keys_symbols by CachedPreference("extra_keys_symbols", DEFAULT_EXTRA_KEYS_SYMBOLS)
    var extra_keys_commands by CachedPreference("extra_keys_commands", DEFAULT_EXTRA_KEYS_COMMANDS)
    var git_username by CachedPreference("git_username", "")
    var git_password by CachedPreference("git_password", "")
    var git_name by CachedPreference("git_name", "")
    var git_email by CachedPreference("git_email", "")

    // Long settings
    var last_update_check_timestamp by CachedPreference("last_update", 0L)
    var last_version_code by CachedPreference("last_version_code", -1L)

    // Float settings
    var line_spacing by CachedPreference("line_spacing", 1f)

    var last_used_command by CachedPreference("last_used_command", "")
    var action_items by CachedPreference("action_items", DEFAULT_ACTION_ITEMS)
}

object Preference {
    private var sharedPreferences: SharedPreferences =
        application!!.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    val preferenceTypes: Map<String, KClass<*>> by lazy {
        Settings::class
            .declaredMemberProperties
            .mapNotNull { prop ->
                try {
                    prop.isAccessible = true
                    val delegate = prop.getDelegate(Settings)
                    if (delegate is CachedPreference<*>) {
                        delegate.key to delegate.defaultValue!!::class
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            .toMap()
    }

    // Weak reference caches to allow garbage collection of unused settings
    private val stringCache = mutableMapOf<String, WeakReference<String?>>()
    private val boolCache = mutableMapOf<String, WeakReference<Boolean>>()
    private val intCache = mutableMapOf<String, WeakReference<Int>>()
    private val longCache = mutableMapOf<String, WeakReference<Long>>()
    private val floatCache = mutableMapOf<String, WeakReference<Float>>()

    fun getAll(): Map<String, Any?> {
        return sharedPreferences.all
    }

    fun put(key: String, value: Any) {
        when (value) {
            is String -> setString(key, value)
            is Boolean -> setBoolean(key, value)
            is Int -> setInt(key, value)
            is Long -> setLong(key, value)
            is Float -> setFloat(key, value)
            else -> IllegalArgumentException("Unsupported preference type")
        }
    }

    // Preload all settings at startup
    suspend fun preloadAllSettings() =
        withContext(Dispatchers.IO) {
            // This will force all settings to be loaded into cache
            // The weak references will allow GC if settings aren't used
            Settings::class.members.forEach { member ->
                if (member is KProperty<*>) {
                    try {
                        member.getter.call(Settings)
                    } catch (e: Exception) {
                        // Ignore - some properties might not be accessible
                    }
                }
            }
        }

    fun clearData() {
        sharedPreferences.edit(commit = true) { clear() }
        clearCaches()
    }

    fun clearCaches() {
        stringCache.clear()
        boolCache.clear()
        intCache.clear()
        longCache.clear()
        floatCache.clear()
    }

    fun removeKey(key: String) {
        if (sharedPreferences.contains(key).not()) {
            return
        }

        sharedPreferences.edit { remove(key) }
        clearKeyFromCache(key)
    }

    private fun clearKeyFromCache(key: String) {
        stringCache.remove(key)
        boolCache.remove(key)
        intCache.remove(key)
        longCache.remove(key)
        floatCache.remove(key)
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return boolCache[key]?.get()
            ?: run {
                val value =
                    try {
                        sharedPreferences.getBoolean(key, default)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setBoolean(key, default)
                        default
                    }
                boolCache[key] = WeakReference(value)
                value
            }
    }

    fun setBoolean(key: String, value: Boolean) {
        boolCache[key] = WeakReference(value)
        runCatching { sharedPreferences.edit { putBoolean(key, value) } }.onFailure { it.printStackTrace() }
    }

    fun getString(key: String, default: String): String {
        return stringCache[key]?.get()
            ?: run {
                val value =
                    try {
                        sharedPreferences.getString(key, default) ?: default
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setString(key, default)
                        default
                    }
                stringCache[key] = WeakReference(value)
                value
            }
    }

    fun setString(key: String, value: String?) {
        stringCache[key] = WeakReference(value)
        runCatching { sharedPreferences.edit { putString(key, value) } }.onFailure { it.printStackTrace() }
    }

    fun getInt(key: String, default: Int): Int {
        return intCache[key]?.get()
            ?: run {
                val value =
                    try {
                        sharedPreferences.getInt(key, default)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setInt(key, default)
                        default
                    }
                intCache[key] = WeakReference(value)
                value
            }
    }

    fun setInt(key: String, value: Int) {
        intCache[key] = WeakReference(value)
        runCatching { sharedPreferences.edit { putInt(key, value) } }.onFailure { it.printStackTrace() }
    }

    fun getLong(key: String, default: Long): Long {
        return longCache[key]?.get()
            ?: run {
                val value =
                    try {
                        sharedPreferences.getLong(key, default)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setLong(key, default)
                        default
                    }
                longCache[key] = WeakReference(value)
                value
            }
    }

    fun setLong(key: String, value: Long) {
        longCache[key] = WeakReference(value)
        runCatching { sharedPreferences.edit { putLong(key, value) } }.onFailure { it.printStackTrace() }
    }

    fun getFloat(key: String, default: Float): Float {
        return floatCache[key]?.get()
            ?: run {
                val value =
                    try {
                        sharedPreferences.getFloat(key, default)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setFloat(key, default)
                        default
                    }
                floatCache[key] = WeakReference(value)
                value
            }
    }

    fun setFloat(key: String, value: Float) {
        floatCache[key] = WeakReference(value)
        runCatching { sharedPreferences.edit { putFloat(key, value) } }.onFailure { it.printStackTrace() }
    }
}

@Suppress("UNCHECKED_CAST")
class CachedPreference<T>(val key: String, val defaultValue: T) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return when (defaultValue) {
            is Boolean -> Preference.getBoolean(key, defaultValue) as T
            is String -> Preference.getString(key, defaultValue) as T
            is Int -> Preference.getInt(key, defaultValue) as T
            is Long -> Preference.getLong(key, defaultValue) as T
            is Float -> Preference.getFloat(key, defaultValue) as T
            else -> throw IllegalArgumentException("Unsupported preference type")
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        when (value) {
            is Boolean -> Preference.setBoolean(key, value)
            is String -> Preference.setString(key, value)
            is Int -> Preference.setInt(key, value)
            is Long -> Preference.setLong(key, value)
            is Float -> Preference.setFloat(key, value)
            else -> throw IllegalArgumentException("Unsupported preference type")
        }
    }
}
