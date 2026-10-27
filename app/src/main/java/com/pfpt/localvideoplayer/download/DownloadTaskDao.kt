package com.pfpt.localvideoplayer.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT COUNT(*) FROM download_tasks")
    suspend fun count(): Int

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadTaskEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE status = 'QUEUED')")
    suspend fun hasQueued(): Boolean

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'QUEUED'")
    suspend fun countQueued(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tasks: List<DownloadTaskEntity>)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = 'QUEUED', isMerging = 0 WHERE status = 'RUNNING'")
    suspend fun recoverRunningTasks(): Int

    @Query(
        "UPDATE download_tasks SET status = 'QUEUED', isMerging = 0 " +
            "WHERE status = 'RUNNING' AND id NOT IN (:activeIds)",
    )
    suspend fun recoverRunningTasksExcept(activeIds: List<String>): Int

    @Query("UPDATE download_tasks SET status = 'RUNNING' WHERE id = :id AND status = 'QUEUED'")
    suspend fun claimQueued(id: String): Int

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
