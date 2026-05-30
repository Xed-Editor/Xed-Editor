@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.persistence.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rk.ai.persistence.db.dao.FavoriteDAO
import com.rk.ai.persistence.db.entity.FavoriteEntity
import com.rk.ai.models.FavoriteType
import com.rk.ai.models.NodeFavoriteTarget
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

class FavoriteRepository(
    private val dao: FavoriteDAO,
) {
    fun listAll(): Flow<List<FavoriteEntity>> = dao.listAll()

    fun listByType(type: FavoriteType): Flow<List<FavoriteEntity>> = dao.listByType(type.value)

    suspend fun getByRefKey(refKey: String): FavoriteEntity? = dao.getByRefKey(refKey)

    suspend fun existsByRefKey(refKey: String): Boolean = dao.existsByRefKey(refKey)

    suspend fun deleteByRefKey(refKey: String): Int = dao.deleteByRefKey(refKey)

    suspend fun deleteById(id: String): Int = dao.deleteById(id)

    suspend fun upsert(entity: FavoriteEntity) = dao.upsert(entity)

    suspend fun addNodeFavorite(target: NodeFavoriteTarget): FavoriteEntity {
        val refKey = buildRefKey(target.conversationId.toString(), target.nodeId.toString())
        val existing = dao.getByRefKey(refKey)
        val now = System.currentTimeMillis()
        val favorite = FavoriteEntity(
            id = existing?.id ?: "${now}_$refKey",
            type = FavoriteType.MESSAGE.value,
            refKey = refKey,
            refJson = json.encodeToString(target),
            snapshotJson = target.node.currentMessage.toText(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(favorite)
        return favorite
    }

    suspend fun removeNodeFavorite(conversationId: Uuid, nodeId: Uuid): Int {
        return dao.deleteByRefKey(buildRefKey(conversationId.toString(), nodeId.toString()))
    }

    suspend fun isNodeFavorited(conversationId: Uuid, nodeId: Uuid): Boolean {
        return dao.existsByRefKey(buildRefKey(conversationId.toString(), nodeId.toString()))
    }

    private fun buildRefKey(conversationIdStr: String, nodeIdStr: String): String =
        "${conversationIdStr}_${nodeIdStr}"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
