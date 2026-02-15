package com.rk.search

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
import com.rk.file.FileObject
import java.io.Serializable

@Entity(tableName = "files")
data class FileMeta(@PrimaryKey val path: String, val fileName: String, val lastModified: Long, val size: Long) :
    Serializable

@Dao
interface FileMetaDao {
    @Query("SELECT * FROM files") suspend fun getAll(): List<FileMeta>

    @Query("SELECT * FROM files WHERE fileName LIKE '%' || :prefix || '%'")
    suspend fun search(prefix: String): List<FileMeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(files: List<FileMeta>)

    @Query("DELETE FROM files WHERE path = :path") suspend fun deleteByPath(path: String)

    @Query("DELETE FROM files") suspend fun clear()
}

@Entity(tableName = "code_index")
data class CodeLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val path: String,
    val lineNumber: Int,
    val chunkStart: Int = 0,
) : Serializable

@Dao
interface CodeLineDao {
    @Query("SELECT * FROM code_index WHERE content LIKE '%' || :query || '%' LIMIT :limit OFFSET :offset")
    suspend fun search(query: String, limit: Int, offset: Int): List<CodeLine>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(lines: List<CodeLine>)

    @Query("DELETE FROM code_index WHERE path = :path") suspend fun deleteByPath(path: String)

    @Query("DELETE FROM code_index") suspend fun clear()
}

@Database(entities = [CodeLine::class, FileMeta::class], version = 1, exportSchema = false)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun codeIndexDao(): CodeLineDao

    abstract fun fileMetaDao(): FileMetaDao

    lateinit var projectRoot: FileObject

    companion object {
        @Volatile private var INSTANCES = mutableMapOf<FileObject, IndexDatabase>()

        fun getDatabase(context: Context, projectRoot: FileObject): IndexDatabase {
            return INSTANCES[projectRoot]
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                                context,
                                IndexDatabase::class.java,
                                "index_database_${projectRoot.hashCode()}",
                            )
                            .build()
                    instance.projectRoot = projectRoot
                    INSTANCES[projectRoot] = instance
                    instance
                }
        }

        fun removeDatabase(context: Context, projectRoot: FileObject) {
            INSTANCES[projectRoot]?.close()
            INSTANCES.remove(projectRoot)
            context.deleteDatabase("index_database_${projectRoot.hashCode()}")
        }

        suspend fun findDatabasesFor(file: FileObject): List<IndexDatabase> {
            var startFile: FileObject? = file
            val databases = mutableListOf<IndexDatabase>()

            while (startFile != null) {
                val database = INSTANCES[startFile]
                database?.let { databases.add(it) }
                startFile = startFile.getParentFile()
            }

            return databases
        }

        fun getDatabaseSize(context: Context, projectRoot: FileObject): Long {
            return context.getDatabasePath("index_database_${projectRoot.hashCode()}").length()
        }
    }
}
