package com.rk.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.rk.settings.viewmodels.AppPreferencesViewModel

object Settings {
    private var preferencesViewModel: AppPreferencesViewModel? = null
    private var init = false
    private val Context.DataStore: DataStore<Preferences> by preferencesDataStore(
        name = "Settings"
    )
    
    fun initPref(context: Context, viewModelStoreOwner: ViewModelStoreOwner) {
        if (init.not()){
            preferencesViewModel = ViewModelProvider(
                viewModelStoreOwner,
                AppPreferencesViewModelFactory(context.DataStore)
            )[AppPreferencesViewModel::class.java]
            init = true
        }
    }
    
    fun getPreferencesViewModel(): AppPreferencesViewModel {
        return preferencesViewModel ?: throw IllegalStateException("PreferencesViewModel not initialized. Call initPref() first.")
    }
    
    class AppPreferencesViewModelFactory(private val dataStore: DataStore<Preferences>) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppPreferencesViewModel::class.java)) {
                return AppPreferencesViewModel(dataStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
