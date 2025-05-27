package com.rk.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.rk.isTermuxInstalled
import com.rk.libcommons.application
import com.rk.libcommons.isFdroid
import com.rk.xededitor.BuildConfig
import java.nio.charset.Charset

object Settings {
    var amoled
        get() = Preference.getBoolean(key = "oled", default = false)
        set(value) = Preference.setBoolean(key = "oled", value)
    var monet
        get() = Preference.getBoolean(
            key = "monet",
            default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        set(value) = Preference.setBoolean(key = "monet", value)
    var pin_line_number
        get() = Preference.getBoolean(key = "pinline", default = false)
        set(value) = Preference.setBoolean(key = "pinline", value)
    var wordwrap
        get() = Preference.getBoolean(key = "wordwrap", default = false)
        set(value) = Preference.setBoolean(key = "wordwrap", value)
    var word_wrap_for_text
        get() = Preference.getBoolean(key = "ww_txt", default = true)
        set(value) = Preference.setBoolean(key = "ww_txt", value)
    var cursor_animation
        get() = Preference.getBoolean(key = "cursor_animation", default = false)
        set(value) = Preference.setBoolean(key = "cursor_animation", value)
    var show_arrow_keys
        get() = Preference.getBoolean(key = "arrow_keys", default = true)
        set(value) = Preference.setBoolean(key = "arrow_keys", value)
    var keep_drawer_locked
        get() = Preference.getBoolean(key = "drawer_lock", default = false)
        set(value) = Preference.setBoolean(key = "drawer_lock", value)
    var show_line_numbers
        get() = Preference.getBoolean(key = "show_line_number", default = true)
        set(value) = Preference.setBoolean(key = "show_line_number", value)
    var auto_save
        get() = Preference.getBoolean(key = "auto_save", default = false)
        set(value) = Preference.setBoolean(key = "auto_save", value)
    var show_suggestions
        get() = Preference.getBoolean(key = "show_suggestions", default = false)
        set(value) = Preference.setBoolean(key = "show_suggestions", value)
    var check_for_update
        get() = Preference.getBoolean(key = "check_update", default = false)
        set(value) = Preference.setBoolean(key = "check_update", value)
    var use_sora_search
        get() = Preference.getBoolean(key = "sora_search", default = true)
        set(value) = Preference.setBoolean(key = "sora_search", value)
    var is_selected_font_assest
        get() = Preference.getBoolean(key = "is_font_asset", default = false)
        set(value) = Preference.setBoolean(key = "is_font_asset", value)
    var smooth_tabs
        get() = Preference.getBoolean(key = "smooth_tab", default = false)
        set(value) = Preference.setBoolean(key = "smooth_tab", value)
    var restore_session
        get() = Preference.getBoolean(key = "restore_sessions", default = true)
        set(value) = Preference.setBoolean(key = "restore_sessions", value)
    var scroll_to_bottom
        get() = Preference.getBoolean(key = "scroll_to_bottom", default = false)
        set(value) = Preference.setBoolean(key = "scroll_to_bottom", value)

    var hide_soft_keyboard_if_hardware
        get() = Preference.getBoolean(key = "always_show_soft_keyboard", default = true)
        set(value) = Preference.setBoolean(key = "always_show_soft_keyboard", value)


    var ignore_storage_permission
        get() = Preference.getBoolean(key = "ignore_storage_permission", default = false)
        set(value) = Preference.setBoolean(key = "ignore_storage_permission", value)
    var unrestricted_files
        get() = Preference.getBoolean(key = "unrestricted_file", default = false)
        set(value) = Preference.setBoolean(key = "unrestricted_file", value)
    var github
        get() = Preference.getBoolean(key = "github", default = true)
        set(value) = Preference.setBoolean(key = "github", value)
    var has_shown_private_data_dir_warning
        get() = Preference.getBoolean(key = "has_shown_private_data_dir_warning", default = false)
        set(value) = Preference.setBoolean(key = "has_shown_private_data_dir_warning", value)
    var has_shown_terminal_dir_warning
        get() = Preference.getBoolean(key = "has_shown_terminal_dir_warning", default = false)
        set(value) = Preference.setBoolean(key = "has_shown_terminal_dir_warning", value)
    var anr_watchdog
        get() = Preference.getBoolean(key = "anr", default = false)
        set(value) = Preference.setBoolean(key = "anr", value)
    var strict_mode
        get() = Preference.getBoolean(key = "strictMode", default = false)
        set(value) = Preference.setBoolean(key = "strictMode", value)

    var expose_home_dir
        get() = Preference.getBoolean(key = "expose_home_dir", default = false)
        set(value) = Preference.setBoolean(key = "expose_home_dir", value)

    var auto_complete
        get() = Preference.getBoolean(key = "auto_complete", default = true)
        set(value) = Preference.setBoolean(key = "auto_complete", value)

    var verbose_error
        get() = Preference.getBoolean(key = "verbose_error", default = BuildConfig.DEBUG)
        set(value) = Preference.setBoolean(key = "verbose_error", value)


    //Int
    var tab_size
        get() = Preference.getInt(key = "tabsize", default = 4)
        set(value) = Preference.setInt(key = "tabsize", value)
    var editor_text_size
        get() = Preference.getInt(key = "textsize", default = 14)
        set(value) = Preference.setInt(key = "textsize", value)
    var auto_save_interval
        get() = Preference.getInt(key = "auto_save_interval", default = 10000)
        set(value) = Preference.setInt(key = "auto_save_interval", value)
    var default_night_mode
        get() = Preference.getInt(
            key = "default_night_mode",
            default = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        set(value) = Preference.setInt(key = "default_night_mode", value)
    var terminal_font_size
        get() = Preference.getInt(key = "terminal_font_size", default = 13)
        set(value) = Preference.setInt(key = "terminal_font_size", value)

    //String
    var projects
        get() = Preference.getString(key = "projects", default = "")
        set(value) = Preference.setString(key = "projects", value)
    var font_gson
        get() = Preference.getString(key = "selected_font", default = "")
        set(value) = Preference.setString(key = "selected_font", value)
    var selected_font_path
        get() = Preference.getString(key = "selected_font_path", default = "")
        set(value) = Preference.setString(key = "selected_font_path", value)
    var encoding
        get() = Preference.getString(key = "encoding", default = Charset.defaultCharset().name())
        set(value) = Preference.setString(key = "encoding", value)
    var mutators
        get() = Preference.getString(key = "mutators", default = "")
        set(value) = Preference.setString(key = "mutators", value)
    var terminal_runtime: String
        get() {
            val default = if (isFdroid) {
                "Alpine"
            } else
                if (isTermuxInstalled()) {
                    "Termux"
                } else {
                    "Android"
                }

            val result = Preference.getString(
                key = "terminal_runtime", default = default
            )

            return if (!isFdroid && result == "Alpine") {
                Settings.terminal_runtime = default
                default
            } else {
                result
            }
        }
        set(value) = Preference.setString(key = "terminal_runtime", value)
    var git_url
        get() = Preference.getString(key = "git_url", default = "github.com")
        set(value) = Preference.setString(key = "git_url", value)


    //Long
    var last_update_check_timestamp
        get() = Preference.getLong(key = "last_update", default = 0)
        set(value) = Preference.setLong(key = "last_update", value)
    var lastVersionCode
        get() = Preference.getLong(key = "last_version_code", default = -1)
        set(value) = Preference.setLong(key = "last_version_code", value)


    //Float
    var line_spacing
        get() = Preference.getFloat(key = "line_spacing", default = 0F)
        set(value) = Preference.setFloat(key = "line_spacing", value)


}

object Preference {
    private var sharedPreferences: SharedPreferences =
        application!!.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    //store the result into memory for faster access
    private val stringCache = hashMapOf<String, String?>()
    private val boolCache = hashMapOf<String, Boolean>()
    private val intCache = hashMapOf<String, Int>()
    private val longCache = hashMapOf<String, Long>()
    private val floatCache = hashMapOf<String, Float>()

    @SuppressLint("ApplySharedPref")
    fun clearData() {
        sharedPreferences.edit().clear().commit()
    }

    fun removeKey(key: String) {
        if (sharedPreferences.contains(key).not()) {
            return
        }

        sharedPreferences.edit().remove(key).apply()

        if (stringCache.containsKey(key)) {
            stringCache.remove(key)
            return
        }

        if (boolCache.containsKey(key)) {
            boolCache.remove(key)
            return
        }

        if (intCache.containsKey(key)) {
            intCache.remove(key)
            return
        }

        if (longCache.containsKey(key)) {
            longCache.remove(key)
            return
        }

        if (floatCache.containsKey(key)) {
            floatCache.remove(key)
            return
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        runCatching {
            return boolCache[key] ?: sharedPreferences.getBoolean(key, default)
                .also { boolCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setBoolean(key, default)
        }
        return default
    }

    fun setBoolean(key: String, value: Boolean) {
        boolCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }.onFailure { it.printStackTrace() }
    }


    fun getString(key: String, default: String): String {
        runCatching {
            return stringCache[key] ?: sharedPreferences.getString(key, default)!!
                .also { stringCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setString(key, default)
        }
        return default
    }

    fun setString(key: String, value: String?) {
        stringCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putString(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getInt(key: String, default: Int): Int {
        runCatching {
            return intCache[key] ?: sharedPreferences.getInt(key, default)
                .also { intCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setInt(key, default)
        }
        return default
    }

    fun setInt(key: String, value: Int) {
        intCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putInt(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getLong(key: String, default: Long): Long {
        runCatching {
            return longCache[key] ?: sharedPreferences.getLong(key, default)
                .also { longCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setLong(key, default)
        }
        return default
    }

    fun setLong(key: String, value: Long) {
        longCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putLong(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun getFloat(key: String, default: Float): Float {
        runCatching {
            return floatCache[key] ?: sharedPreferences.getFloat(key, default)
                .also { floatCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setFloat(key, default)
        }
        return default
    }

    fun setFloat(key: String, value: Float) {
        floatCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putFloat(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

}
