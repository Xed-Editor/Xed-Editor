package com.rk.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountUser(
    val id: String,
    val name: String? = null,
    val email: String = "",
    val emailVerified: Boolean = false,
    val image: String? = null,
    val isBanned: Boolean = false,
    val isAdmin: Boolean = false,
)

@Serializable
data class AuthState(val state: String, val expiresAt: String = "")

@Serializable
data class TokenResponse(
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("session_id") val sessionId: String,
    val user: AccountUser,
)

@Serializable data class ApiError(val error: String? = null)

data class AccountSession(
    val user: AccountUser,
    val accessToken: String,
    val refreshToken: String,
    val sessionId: String,
    val expiresAt: Long,
    val signedInAt: Long,
)
