package com.rk.ai.persistence.db

import androidx.room.AutoMigration
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
import com.rk.ai.persistence.db.migrations.Migration_16_17
import com.rk.ai.persistence.db.migrations.Migration_8_9
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
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 16, to = 17, spec = Migration_16_17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
    ]
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
