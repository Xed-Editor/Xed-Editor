package com.rk.extension.model

import com.rk.common.PackageType
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class PackageCache(
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val size: Long? = null,
)

data class Review(val rating: Int, val text: String, val author: String, val date: Date, val authorResponse: String?)

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

    fun hasUpdate(): Boolean
}
