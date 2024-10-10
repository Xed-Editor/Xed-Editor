package com.rk.settings

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Settings")

object PreferencesData {
    private const val PREFNAME = "Settings"

    private val DEFAULT_NIGHT_MODE_KEY = stringPreferencesKey(PreferencesKeys.DEFAULT_NIGHT_MODE)
    private val OLED_KEY = booleanPreferencesKey(PreferencesKeys.OLED)
    private val MONET_KEY = booleanPreferencesKey(PreferencesKeys.MONET)

    fun isDarkMode(ctx: Context): LiveData<Boolean> {
        val preferencesFlow = ctx.dataStore.data
            .map { preferences ->
                val mode = preferences[DEFAULT_NIGHT_MODE_KEY] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()
                when (mode) {
                    AppCompatDelegate.MODE_NIGHT_YES.toString() -> true
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString() -> {
                        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                    }
                    else -> false
                }
            }
        return preferencesFlow.asLiveData()
    }

    fun isOled(ctx: Context): LiveData<Boolean> {
        val preferencesFlow = ctx.dataStore.data
            .map { preferences -> preferences[OLED_KEY] ?: false }
        return preferencesFlow.asLiveData()
    }

    fun isMonet(ctx: Context): LiveData<Boolean> {
        val preferencesFlow = ctx.dataStore.data
            .map { preferences -> preferences[MONET_KEY] ?: false }
        return preferencesFlow.asLiveData()
    }

    fun getBoolean(ctx: Context, key: String?, default: Boolean): LiveData<Boolean> {
        val preferencesFlow = ctx.dataStore.data
            .map { preferences -> preferences[booleanPreferencesKey(key)] ?: default }
        return preferencesFlow.asLiveData()
    }

    fun setBoolean(ctx: Context, key: String?, value: Boolean) {
        ctx.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }

    fun initPref(ctx: Context) { }

    fun getString(ctx: Context, key: String?, default: String): LiveData<String> {
        val preferencesFlow = ctx.dataStore.data
            .map { preferences -> preferences[stringPreferencesKey(key)] ?: default }
        return preferencesFlow.asLiveData()
    }

    fun setString(ctx: Context, key: String?, value: String?) {
        ctx.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }
}