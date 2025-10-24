package com.rk.settings
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.rk.libcommons.application
import com.rk.libcommons.hasHardwareKeyboard
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.theme.blueberry
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.core.content.edit

object Settings {
    var readOnlyByDefault by CachedPreference("readOnly", false)
    var amoled by CachedPreference("oled", false)
    var monet by CachedPreference("monet", false)
    var pin_line_number by CachedPreference("pinline", false)
    var wordwrap by CachedPreference("wordwrap", false)
    var restore_sessions by CachedPreference("restore_sessions", true)
    var word_wrap_for_text by CachedPreference("ww_txt", true)
    var cursor_animation by CachedPreference("cursor_animation", false)
    var show_arrow_keys by CachedPreference("arrow_keys", hasHardwareKeyboard(application!!).not())
    var keep_drawer_locked by CachedPreference("drawer_lock", false)
    var show_line_numbers by CachedPreference("show_line_number", true)
    var render_whitespace by CachedPreference("render_whitespace", false)
    var sticky_scroll by CachedPreference("sticky_scroll", false)
    var quick_deletion by CachedPreference("fast_delete", false)
    var auto_save by CachedPreference("auto_save", false)
    var show_suggestions by CachedPreference("show_suggestions", false)
    var check_for_update by CachedPreference("check_update", false)
    var is_selected_font_assest by CachedPreference("is_font_asset", false)
    var smooth_tabs by CachedPreference("smooth_tab", false)
    var actual_tabs by CachedPreference("actual_tab", false)
    var scroll_to_bottom by CachedPreference("scroll_to_bottom", false)
    var hide_soft_keyboard_if_hardware by CachedPreference("always_show_soft_keyboard", true)
    var ignore_storage_permission by CachedPreference("ignore_storage_permission", false)
    var github by CachedPreference("github", true)
    var has_shown_private_data_dir_warning by CachedPreference("has_shown_private_data_dir_warning", false)
    var has_shown_terminal_dir_warning by CachedPreference("has_shown_terminal_dir_warning", false)
    var anr_watchdog by CachedPreference("anr", BuildConfig.DEBUG)
    var strict_mode by CachedPreference("strictMode", BuildConfig.DEBUG)
    var expose_home_dir by CachedPreference("expose_home_dir", false)
    var verbose_error by CachedPreference("verbose_error", BuildConfig.DEBUG)
    var project_as_pwd by CachedPreference("project_as_pwd", true)
    var donated by CachedPreference("donated", false)
    var sandbox by CachedPreference("sandbox", true)
    var terminalVirusNotice by CachedPreference("terminal-virus-notice", false)
    var textMateSuggestion by CachedPreference("textMateSuggestion", true)
    var seccomp by CachedPreference("seccomp", false)

    // Int settings
    var tab_size by CachedPreference("tabsize", 4)
    var editor_text_size by CachedPreference("textsize", 14)
    var default_night_mode by CachedPreference(
        "default_night_mode",
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )
    var terminal_font_size by CachedPreference("terminal_font_size", 13)
    var visits by CachedPreference("visits", 0)

    // String settings
    var selectedProject by CachedPreference("selected_project", "")
    var font_gson by CachedPreference("selected_font", "")
    var theme by CachedPreference("theme", blueberry.id)
    var selected_font_path by CachedPreference("selected_font_path", "")
    var encoding: String? by CachedPreference("encoding", Charset.defaultCharset().name())
    var currentLang: String? by CachedPreference("currentLang", application!!.resources.configuration.locales[0].language)

    // Long settings
    var last_update_check_timestamp by CachedPreference("last_update", 0L)
    var lastVersionCode by CachedPreference("last_version_code", -1L)

    // Float settings
    var line_spacing by CachedPreference("line_spacing", 1f)
}

object Preference {
    private var sharedPreferences: SharedPreferences =
        application!!.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    // Weak reference caches to allow garbage collection of unused settings
    private val stringCache = mutableMapOf<String, WeakReference<String?>>()
    private val boolCache = mutableMapOf<String, WeakReference<Boolean>>()
    private val intCache = mutableMapOf<String, WeakReference<Int>>()
    private val longCache = mutableMapOf<String, WeakReference<Long>>()
    private val floatCache = mutableMapOf<String, WeakReference<Float>>()

    // Preload all settings at startup
    fun preloadAllSettings() {
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

    @SuppressLint("ApplySharedPref")
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
        return boolCache[key]?.get() ?: run {
            val value = try {
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
        runCatching {
            sharedPreferences.edit { putBoolean(key, value) }
        }.onFailure { it.printStackTrace() }
    }

    fun getString(key: String, default: String): String {
        return stringCache[key]?.get() ?: run {
            val value = try {
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
        runCatching {
            sharedPreferences.edit { putString(key, value) }
        }.onFailure { it.printStackTrace() }
    }

    fun getInt(key: String, default: Int): Int {
        return intCache[key]?.get() ?: run {
            val value = try {
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
        runCatching {
            sharedPreferences.edit { putInt(key, value) }
        }.onFailure { it.printStackTrace() }
    }

    fun getLong(key: String, default: Long): Long {
        return longCache[key]?.get() ?: run {
            val value = try {
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
        runCatching {
            sharedPreferences.edit { putLong(key, value) }
        }.onFailure { it.printStackTrace() }
    }

    fun getFloat(key: String, default: Float): Float {
        return floatCache[key]?.get() ?: run {
            val value = try {
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
        runCatching {
            sharedPreferences.edit { putFloat(key, value) }
        }.onFailure { it.printStackTrace() }
    }
}

@Suppress("UNCHECKED_CAST")
class CachedPreference<T>(private val key: String, private val defaultValue: T) : ReadWriteProperty<Any?, T> {
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