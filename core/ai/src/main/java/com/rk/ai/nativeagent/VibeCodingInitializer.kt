package com.rk.ai.nativeagent

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide singleton holder for the [VibeCodingEngine] instance.
 * Typed explicitly to avoid unsafe `Any?` casts at call sites.
 */
object VibeCodingProvider {
    val engineRef = AtomicReference<VibeCodingEngine?>(null)
}

/**
 * ContentProvider stub used solely to hook into the app startup lifecycle
 * via AndroidX App Startup or manifest-declared initialization.
 */
class VibeCodingInitializer : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
