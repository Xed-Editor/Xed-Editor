package com.rk.activities.main.session

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.Serializable

@Entity(tableName = "document_state")
data class DocumentState(
    @PrimaryKey val path: String,
    val cursorLineLeft: Int,
    val cursorColumnLeft: Int,
    val cursorLineRight: Int,
    val cursorColumnRight: Int,
    val scrollX: Int,
    val scrollY: Int,
    val lastOpened: Long,
) : Serializable

@Dao
interface DocumentStateDao {
    @Query("SELECT * FROM document_state WHERE path = :path") suspend fun getState(path: String): DocumentState?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertState(state: DocumentState)

    @Query(
        "DELETE FROM document_state WHERE path NOT IN (SELECT path FROM document_state ORDER BY lastOpened DESC LIMIT :limit)"
    )
    suspend fun deleteOldestRecords(limit: Int)

    @Query("DELETE FROM document_state WHERE path = :path") suspend fun deleteByPath(path: String)

    @Query("DELETE FROM document_state WHERE lastOpened < :timestamp") suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM document_state") suspend fun clear()
}

@Database(entities = [DocumentState::class], version = 1, exportSchema = false)
abstract class DocumentStateDatabase : RoomDatabase() {
    abstract fun documentStateDao(): DocumentStateDao

    companion object {
        @Volatile private var INSTANCE: DocumentStateDatabase? = null

        fun getDatabase(context: Context): DocumentStateDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                                context,
                                DocumentStateDatabase::class.java,
                                "document_state_database",
                            )
                            .build()
                    INSTANCE = instance
                    instance
                }
        }
    }
}
