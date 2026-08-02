package com.example.data.db

import androidx.room.*

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val videoFileName: String,
    val videoSourceUriOrUrl: String = "",
    val clipCount: Int = 0,
    val thumbnailPath: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "viral_clips")
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String = "default_project",
    val startTimeSeconds: Float,
    val endTimeSeconds: Float,
    val confidenceScore: Float,
    val suggestedHookText: String,
    val reason: String,
    val title: String,
    val description: String,
    val tagsCsv: String,
    val processedVideoPath: String?,
    val thumbnailPath: String?,
    val cropOffset: Float = 0f,
    val subtitlesJson: String = "",
    val isCompliant: Boolean = true,
    val complianceNote: String = "Compliant with Campaign Rules",
    val showTitle: Boolean = true,
    val showDescription: Boolean = true,
    val showTags: Boolean = true,
    val isExported: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_queue")
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val videoSourceType: String, // "LOCAL" or "GDRIVE"
    val videoSourceUriOrUrl: String,
    val fileName: String,
    val rulesContent: String,
    val customInstructions: String,
    val status: String, // "PENDING", "PROCESSING", "COMPLETED", "FAILED"
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

@Dao
interface ClipDao {
    @Query("SELECT * FROM projects ORDER BY createdAtMs DESC")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Query("UPDATE projects SET name = :newName WHERE id = :projectId")
    suspend fun renameProject(projectId: String, newName: String)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("SELECT * FROM viral_clips WHERE projectId = :projectId ORDER BY timestampMs DESC")
    suspend fun getClipsForProject(projectId: String): List<ClipEntity>

    @Query("SELECT * FROM viral_clips ORDER BY timestampMs DESC")
    suspend fun getAllClips(): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Query("DELETE FROM viral_clips WHERE id = :id")
    suspend fun deleteClip(id: String)

    @Query("DELETE FROM viral_clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)

    @Query("DELETE FROM viral_clips")
    suspend fun deleteAllClips()

    @Query("SELECT * FROM batch_queue ORDER BY timestampMs ASC")
    suspend fun getQueueItems(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueItemEntity)

    @Query("UPDATE batch_queue SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateQueueStatus(id: String, status: String, errorMessage: String? = null)

    @Query("DELETE FROM batch_queue WHERE id = :id")
    suspend fun deleteQueueItem(id: String)

    @Query("DELETE FROM batch_queue WHERE status = 'COMPLETED'")
    suspend fun clearCompletedQueue()
}

@Database(entities = [ProjectEntity::class, ClipEntity::class, QueueItemEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clipforge_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
