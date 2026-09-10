package com.rk.extension.model

import com.rk.common.PackageType
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.serialization.Serializable

@Serializable
data class PackageCache(
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val size: Long? = null,
)

@Serializable data class ReviewStats(val average: Float? = null, val count: Int = 0)

@Serializable data class ReviewsResponse(val reviews: List<Review>, val stats: ReviewStats)

@Serializable
data class Review(
    val id: Int,
    val score: Int,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val userName: String,
    val userImage: String,
)

interface Package {
    val id: String
    val type: PackageType
    val name: String
    val version: String
    val author: PackageAuthor
    val description: String?
    val tags: List<String>
    val repository: String?
    val license: String?
    val dependencies: List<String>
    val recommendations: List<String>
    val hasSettings: Boolean
    val iconUrl: String
    val readmeUrl: String
    val changelogUrl: String
    val minAppVersion: Int?
    val supportedArchitectures: List<String>?
    val downloads: Int?
    val rating: Float?
    val size: Long?
    val createdAt: Long?
    val updatedAt: Long?

    suspend fun getReviews(): List<Review>
}

interface UpdatablePackage : Package {
    val newVersion: String

    fun hasUpdate(): Boolean {
        val installedVersion = version.toVersionOrNull() ?: return false
        val storeVersion = newVersion.toVersionOrNull() ?: return false
        return installedVersion < storeVersion
    }
}
