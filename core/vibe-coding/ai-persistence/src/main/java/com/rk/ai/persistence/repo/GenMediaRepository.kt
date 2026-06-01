package com.rk.ai.persistence.repo

import androidx.paging.PagingSource
import com.rk.ai.persistence.db.dao.GenMediaDAO
import com.rk.ai.persistence.db.entity.GenMediaEntity

class GenMediaRepository(private val dao: GenMediaDAO) {
    fun getAllMedia(): PagingSource<Int, GenMediaEntity> = dao.getAll()

    suspend fun insertMedia(media: GenMediaEntity) = dao.insert(media)

    suspend fun deleteMedia(id: Int) = dao.delete(id)
}
