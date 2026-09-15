package com.rk.account

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rk.utils.application
import kotlinx.serialization.json.Json

internal val accountJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal object AccountStore {
    private const val PREFS_NAME = "xed_account"

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_SIGNED_IN_AT = "signed_in_at"
    private const val KEY_USER = "user"

    private const val KEY_PENDING_STATE = "pending_state"
    private const val KEY_PENDING_VERIFIER = "pending_verifier"
    private const val KEY_PENDING_CREATED_AT = "pending_created_at"

    private const val PENDING_TTL_MS = 30 * 60 * 1000L

    private val prefs: SharedPreferences
        get() = application!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSession(): AccountSession? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        val userJson = prefs.getString(KEY_USER, null) ?: return null

        val user =
            runCatching { accountJson.decodeFromString<AccountUser>(userJson) }.getOrNull() ?: return null

        return AccountSession(
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
            sessionId = sessionId,
            expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L),
            signedInAt = prefs.getLong(KEY_SIGNED_IN_AT, 0L),
        )
    }

    fun saveSession(session: AccountSession) {
        prefs.edit(commit = true) {
            putString(KEY_ACCESS_TOKEN, session.accessToken)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
            putString(KEY_SESSION_ID, session.sessionId)
            putLong(KEY_EXPIRES_AT, session.expiresAt)
            putLong(KEY_SIGNED_IN_AT, session.signedInAt)
            putString(KEY_USER, accountJson.encodeToString(session.user))
        }
    }

    fun clearSession() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_SESSION_ID)
            remove(KEY_EXPIRES_AT)
            remove(KEY_SIGNED_IN_AT)
            remove(KEY_USER)
        }
    }

    fun savePending(state: String, verifier: String) {
        prefs.edit(commit = true) {
            putString(KEY_PENDING_STATE, state)
            putString(KEY_PENDING_VERIFIER, verifier)
            putLong(KEY_PENDING_CREATED_AT, System.currentTimeMillis())
        }
    }

    fun loadPending(): PendingSignIn? {
        val state = prefs.getString(KEY_PENDING_STATE, null) ?: return null
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null) ?: return null
        val createdAt = prefs.getLong(KEY_PENDING_CREATED_AT, 0L)
        if (System.currentTimeMillis() - createdAt > PENDING_TTL_MS) {
            clearPending()
            return null
        }
        return PendingSignIn(state, verifier)
    }

    fun clearPending() {
        prefs.edit {
            remove(KEY_PENDING_STATE)
            remove(KEY_PENDING_VERIFIER)
            remove(KEY_PENDING_CREATED_AT)
        }
    }
}

internal data class PendingSignIn(val state: String, val verifier: String)
