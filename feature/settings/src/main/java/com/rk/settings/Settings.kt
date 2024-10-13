package com.rk.settings
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.rk.settings.viewmodels.AppPreferencesViewModel

object Settings {
    private var preferencesViewModel: AppPreferencesViewModel? = null
    private var isInitialized = false
    
    fun initialize(app: Application) {
        if (!isInitialized) {
            preferencesViewModel = ViewModelProvider.AndroidViewModelFactory(app)
                .create(AppPreferencesViewModel::class.java)
            
            isInitialized = true
        }
    }
   
    fun getPreferencesViewModel(): AppPreferencesViewModel {
        return preferencesViewModel ?: throw IllegalStateException("PreferencesViewModel not initialized. Call initPref() first.")
    }
}
