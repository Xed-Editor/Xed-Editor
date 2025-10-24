package com.rk.settings

import android.content.Context
import androidx.core.content.edit

/**
 * MigrationManager handles version-based migrations for e.g. editor settings.
 *
 * It ensures that settings and data are properly updated when the app is upgraded to a new version.
 */
object MigrationManager {
    private const val CURRENT_VERSION = 1

    /**
     * Runs all necessary migrations.
     * */
    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_version_code", 0)

        if (lastVersion == CURRENT_VERSION) return

        // Line spacing migration to multiplier
        if (lastVersion == 0 && Settings.line_spacing == 0f) {
            Settings.line_spacing = 1f
        }

        prefs.edit { putInt("last_version_code", CURRENT_VERSION) }
    }
}