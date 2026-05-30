package com.rk.ai.persistence.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.rk.ai.core.TokenUsage
import com.rk.ai.persistence.db.dao.ConversationDAO
import com.rk.ai.persistence.db.dao.FavoriteDAO
import com.rk.ai.persistence.db.dao.GenMediaDAO
import com.rk.ai.persistence.db.dao.ManagedFileDAO
import com.rk.ai.persistence.db.dao.MemoryDAO
import com.rk.ai.persistence.db.dao.MessageNodeDAO
import com.rk.ai.persistence.db.entity.ConversationEntity
import com.rk.ai.persistence.db.entity.FavoriteEntity
import com.rk.ai.persistence.db.entity.GenMediaEntity
import com.rk.ai.persistence.db.entity.ManagedFileEntity
import com.rk.ai.persistence.db.entity.MemoryEntity
import com.rk.ai.persistence.db.entity.MessageNodeEntity
import com.rk.ai.streaming.JsonInstant

@Database(
    entities = [
        ConversationEntity::class,
        MemoryEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileEntity::class,
        FavoriteEntity::class
    ],
    version = 20,
    exportSchema = false
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun messageNodeDao(): MessageNodeDAO

    abstract fun managedFileDao(): ManagedFileDAO

    abstract fun favoriteDao(): FavoriteDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}
