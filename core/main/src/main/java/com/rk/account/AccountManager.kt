package com.rk.account

import android.net.Uri
import com.rk.utils.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AccountManager {
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

    sealed interface SignInState {
        data object Idle : SignInState

        data object AwaitingBrowser : SignInState

        data class Failed(val message: String) : SignInState
    }

    private val _session = MutableStateFlow<AccountSession?>(null)
    val session: StateFlow<AccountSession?> = _session.asStateFlow()

    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            val stored = AccountStore.loadSession()
            _session.value = stored
            if (stored != null && stored.expiresAt - System.currentTimeMillis() <= REFRESH_MARGIN_MS) {
                refresh()
            }
        }

    fun isSignInRedirect(uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "https", "http" ->
                uri.host.equals(AccountConfig.WEB_HOST, ignoreCase = true) &&
                    uri.path?.startsWith(AccountConfig.WEB_CALLBACK_PATH) == true

            AccountConfig.DEEP_LINK_SCHEME ->
                uri.host.equals(AccountConfig.DEEP_LINK_HOST, ignoreCase = true) &&
                    uri.path?.startsWith(AccountConfig.DEEP_LINK_PATH) == true

            else -> false
        }
    }

    suspend fun startSignIn(): Result<String> {
        _signInState.value = SignInState.AwaitingBrowser
        return withContext(Dispatchers.IO) {
            runCatching {
                    val verifier = Pkce.createVerifier()
                    val state = XedAccountClient.begin(Pkce.createChallenge(verifier))
                    AccountStore.savePending(state = state.state, verifier = verifier)
                    "${AccountConfig.WEB_CALLBACK_URL}?state=${Uri.encode(state.state)}"
                }
                .onFailure {
                    logError(it)
                    _signInState.value = SignInState.Failed(it.message ?: "Could not start sign-in")
                }
        }
    }

    suspend fun completeSignIn(uri: Uri): Result<AccountUser> =
        withContext(Dispatchers.IO) {
            val pending = AccountStore.loadPending()
            if (pending == null) {
                val failure = AccountApiException("This sign-in request expired. Please try again.")
                _signInState.value = SignInState.Failed(failure.message!!)
                return@withContext Result.failure(failure)
            }

            val state = uri.getQueryParameter("state")
            if (state.isNullOrEmpty() || state != pending.state) {
                AccountStore.clearPending()
                _signInState.value = SignInState.Idle
                return@withContext Result.failure(
                    AccountApiException("Sign-in response did not match this request.")
                )
            }

            val code = uri.getQueryParameter("code")
            if (code.isNullOrEmpty()) {
                AccountStore.clearPending()
                val message = uri.getQueryParameter("error") ?: "Authorization code missing"
                _signInState.value = SignInState.Failed(message)
                return@withContext Result.failure(AccountApiException(message))
            }

            runCatching {
                    val tokens = XedAccountClient.exchange(code, pending.verifier)
                    val session = tokens.toSession(signedInAt = System.currentTimeMillis())
                    AccountStore.saveSession(session)
                    AccountStore.clearPending()
                    _session.value = session
                    _signInState.value = SignInState.Idle
                    session.user
                }
                .onFailure {
                    logError(it)
                    _signInState.value = SignInState.Failed(it.message ?: "Sign-in failed")
                }
        }

    suspend fun signOut() =
        withContext(Dispatchers.IO) {
            val accessToken = _session.value?.accessToken
            clearSession()
            if (accessToken != null) {
                runCatching { XedAccountClient.logout(accessToken) }.onFailure { logError(it) }
            }
        }

    fun cancelSignIn() {
        AccountStore.clearPending()
        _signInState.value = SignInState.Idle
    }

    suspend fun validAccessToken(): String? {
        val current = _session.value ?: return null
        if (current.expiresAt - System.currentTimeMillis() > REFRESH_MARGIN_MS) {
            return current.accessToken
        }
        return if (refresh()) _session.value?.accessToken else null
    }

    private suspend fun refresh(): Boolean =
        refreshMutex.withLock {
            withContext(Dispatchers.IO) {
                val current = _session.value ?: return@withContext false
                try {
                    val session = XedAccountClient.refresh(current.refreshToken).toSession(current.signedInAt)
                    AccountStore.saveSession(session)
                    _session.value = session
                    true
                } catch (e: AccountApiException) {
                    if (e.status == 401 || e.status == 403) clearSession() else logError(e)
                    false
                } catch (e: Exception) {
                    logError(e)
                    false
                }
            }
        }

    private fun clearSession() {
        AccountStore.clearSession()
        AccountStore.clearPending()
        _session.value = null
        _signInState.value = SignInState.Idle
    }

    private fun TokenResponse.toSession(signedInAt: Long): AccountSession =
        AccountSession(
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
            sessionId = sessionId,
            expiresAt = System.currentTimeMillis() + (expiresIn * 1000).coerceAtLeast(0),
            signedInAt = signedInAt,
        )
}
