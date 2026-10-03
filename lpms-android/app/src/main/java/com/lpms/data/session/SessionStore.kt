package com.lpms.data.session

import android.content.Context
import com.lpms.data.model.AuthUser
import com.lpms.data.remote.TokenCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * What the app knows about the current session.
 *
 * [Initializing] means "we have a stored token but the server has not agreed
 * to it yet" — the app shows its launch screen and calls `/auth/me`.
 */
sealed interface AuthState {
    data object Initializing : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
}

/**
 * Session persistence.
 *
 * The JWT is never written in clear text: [TokenCipher] encrypts it with an
 * Android Keystore key before it reaches `SharedPreferences`. The plaintext is
 * cached in memory so OkHttp threads can read it synchronously.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Volatile
    private var token: String? = null

    @Volatile
    private var user: AuthUser? = null

    init {
        load()
    }

    /** Bearer token for the auth interceptor; null when signed out. */
    fun currentToken(): String? = token

    /** A stored session exists but has not been confirmed by the server yet. */
    fun hasStoredSession(): Boolean = token != null

    /**
     * The user persisted alongside the stored token. Used when `/auth/me` is
     * unreachable (offline) so the app can still show who is signed in.
     */
    fun storedUser(): AuthUser? = user

    /**
     * Stages a freshly issued token so the immediately following `/auth/me`
     * call is authenticated. Nothing is persisted until [commit].
     */
    fun stageToken(value: String?) {
        token = value
    }

    /** Persists the staged token plus the confirmed user. */
    fun commit(user: AuthUser) {
        val staged = token ?: return
        this.user = user
        prefs.edit()
            .putString(KEY_TOKEN, TokenCipher.encrypt(staged))
            .putString(KEY_USER, json.encodeToString(AuthUser.serializer(), user))
            .apply()
        _authState.value = AuthState.Authenticated(user)
    }

    /** Persists an updated user (e.g. `mustChangePassword` cleared) in place. */
    fun update(user: AuthUser) {
        val staged = token ?: return
        this.user = user
        prefs.edit()
            .putString(KEY_TOKEN, TokenCipher.encrypt(staged))
            .putString(KEY_USER, json.encodeToString(AuthUser.serializer(), user))
            .apply()
        _authState.value = AuthState.Authenticated(user)
    }

    /** Signs out. Safe to call from any thread (including a 401 interceptor). */
    fun clear() {
        token = null
        user = null
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated
    }

    private fun load() {
        val encoded = prefs.getString(KEY_TOKEN, null)
        val userJson = prefs.getString(KEY_USER, null)
        if (encoded == null || userJson == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }
        val decoded = TokenCipher.decrypt(encoded)
        val stored = runCatching { json.decodeFromString(AuthUser.serializer(), userJson) }.getOrNull()
        if (decoded == null || stored == null) {
            // Corrupt, or the Keystore key was rotated — start clean rather
            // than half-restoring a session we cannot trust.
            clear()
            return
        }
        token = decoded
        user = stored
        // Deliberately left at Initializing: /auth/me still has to agree.
    }

    private companion object {
        const val PREFS = "lpms_session"
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
    }
}
