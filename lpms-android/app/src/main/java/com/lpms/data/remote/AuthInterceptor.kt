package com.lpms.data.remote

import com.lpms.data.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer …` and turns a 401 into a sign-out.
 *
 * Only 401 clears the session — 403 means "logged in, not allowed" and must
 * leave the user signed in.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = sessionStore.currentToken()

        val authed = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        val response = try {
            chain.proceed(authed)
        } catch (e: IOException) {
            throw e
        }

        if (response.code == 401 && token != null && !isAnonymousPath(original)) {
            sessionStore.clear()
        }
        return response
    }

    /** A rejected login is the user's problem, not a session to tear down. */
    private fun isAnonymousPath(original: okhttp3.Request): Boolean =
        original.url.encodedPath.endsWith("/auth/login")
}
