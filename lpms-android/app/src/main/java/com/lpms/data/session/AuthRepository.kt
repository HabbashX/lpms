package com.lpms.data.session

import com.lpms.data.model.AuthUser
import com.lpms.data.model.Role
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.ChangePasswordRequest
import com.lpms.data.remote.dto.LoginRequest
import com.lpms.data.remote.dto.UserResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that moves the session between [AuthState]s.
 *
 * The rule that shapes this class: a token is only believed after `/auth/me`
 * has repeated it back. [SessionStore] never flips to `Authenticated` on its
 * own, so a stored-but-revoked session can never render the home screen.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: LpmsApi,
    private val session: SessionStore,
    private val executor: ApiExecutor,
) {

    /**
     * Signs in, then confirms the token with `/auth/me`.
     *
     * If `/auth/me` fails the staged token is thrown away — otherwise the next
     * app launch would treat an unverified token as a valid session.
     */
    suspend fun login(username: String, password: String): ApiResult<AuthUser> {
        val issued = executor.run { api.login(LoginRequest(username, password)) }
        val token = (issued as? ApiResult.Success)?.value?.accessToken
            ?: return issued as ApiResult.Failure

        session.stageToken(token)

        return when (val confirmed = executor.run { api.me() }) {
            is ApiResult.Failure -> {
                session.stageToken(null)
                session.clear()
                confirmed
            }
            is ApiResult.Success -> {
                val user = confirmed.value.toAuthUser()
                session.commit(user)
                ApiResult.Success(user)
            }
        }
    }

    /**
     * Re-validates the stored session at launch.
     *
     * - HTTP failure → the token is dead: sign out.
     * - Transport failure → keep the stored session. The next real call will
     *   401 if the token was in fact revoked, and [AuthInterceptor] will tear
     *   it down; refusing to open the app because the network is down is worse.
     */
    suspend fun restore(): ApiResult<AuthUser> {
        val stored = session.storedUser()
        if (!session.hasStoredSession() || stored == null) {
            session.clear()
            return ApiResult.Failure(ApiError.Http(401, null, null))
        }

        return when (val confirmed = executor.run { api.me() }) {
            is ApiResult.Success -> {
                val user = confirmed.value.toAuthUser()
                session.update(user)
                ApiResult.Success(user)
            }
            is ApiResult.Failure -> when (confirmed.error) {
                is ApiError.Http -> {
                    session.clear()
                    confirmed
                }
                else -> {
                    session.update(stored)
                    ApiResult.Success(stored)
                }
            }
        }
    }

    /**
     * Ends the session on the server too. The local session is cleared
     * either way — a failed logout request must not leave the device signed in.
     */
    suspend fun logout(): ApiResult<Unit> {
        val result = executor.run { api.logout() }
        session.clear()
        return result
    }

    /**
     * The backend revokes *every* token issued to the user on a password
     * change, so the session is already dead server-side. We keep the local
     * copy only until [completePasswordChange], which lets the screen tell the
     * user what happened before dropping them on the login form.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> =
        executor.run { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }

    /** Ends the local session after a successful password change. */
    fun completePasswordChange() = session.clear()

    /** The currently signed-in user, if any. */
    fun currentUser(): AuthUser? = session.storedUser()

    private fun UserResponse.toAuthUser() = AuthUser(
        id = id,
        username = username,
        role = role.asRole(),
        enabled = enabled,
        mustChangePassword = mustChangePassword,
    )

    private fun String.asRole(): Role =
        entries.firstOrNull { it.name == this } ?: Role.EMPLOYEE
}
