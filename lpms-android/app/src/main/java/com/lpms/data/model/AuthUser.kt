package com.lpms.data.model

import kotlinx.serialization.Serializable

/** Mirrors the backend's `Role` enum (ADMIN, PHARMACIST, EMPLOYEE). */
enum class Role { ADMIN, PHARMACIST, EMPLOYEE }

/**
 * The signed-in user as the app models it — richer than the login payload,
 * because only `/auth/me` reports [enabled] and [mustChangePassword].
 *
 * Serializable because the session is persisted (encrypted at rest, see
 * [com.lpms.data.remote.TokenCipher]).
 */
@Serializable
data class AuthUser(
    val id: Long,
    val username: String,
    val role: Role,
    val enabled: Boolean,
    val mustChangePassword: Boolean,
)
