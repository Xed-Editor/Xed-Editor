package com.rk.settings

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Settings")

object PreferencesData {
    private const val PREFNAME = "Settings"

    private val DEFAULT_NIGHT_MODE_KEY = stringPreferencesKey(PreferencesKeys.DEFAULT_NIGHT_MODE)
    private val OLED_KEY = booleanPreferencesKey(PreferencesKeys.OLED)
    private val MONET_KEY = booleanPreferencesKey(PreferencesKeys.MONET)

    fun isDarkMode(ctx: Context): Boolean {
        val preferences = ctx.dataStore.data.map { it[DEFAULT_NIGHT_MODE_KEY] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString() }
        val mode = preferences.first()

        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES.toString() -> true
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString() -> {
                ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES)
            }
            else -> false
        }
    }

    fun isOled(): Boolean {
        val preferences = ctx.dataStore.data.map { it[OLED_KEY] ?: false }
        return preferences.first()
    }

    fun isMonet(): Boolean {
        val preferences = ctx.dataStore.data.map { it[MONET_KEY] ?: false }
        return preferences.first() 
    }

    fun getBoolean(key: String?, default: Boolean): Boolean {
        val preferences = ctx.dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }
        return preferences.first() 
    }

    fun setBoolean(key: String?, value: Boolean) {
        ctx.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }

    fun initPref(ctx: Context) { }

    fun getString(key: String?, default: String): String {
        val preferences = ctx.dataStore.data.map { it[stringPreferencesKey(key)] ?: default }
        return preferences.first()
    }

    fun setString(key: String?, value: String?) {
        ctx.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }
}