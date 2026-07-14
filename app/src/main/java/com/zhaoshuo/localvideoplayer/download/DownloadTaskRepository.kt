package com.zhaoshuo.localvideoplayer.download

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadTaskRepository(
    private val database: DownloadDatabase,
    private val headerCipher: DownloadHeaderCipher,
) {
    private val dao = database.downloadTaskDao()

    val tasks: Flow<List<DownloadTask>> = dao.observeAll().map { entities ->
        entities.map(::toTask)
    }

    suspend fun initialize(legacyTasks: List<DownloadTask>): Boolean = database.withTransaction {
        val shouldMigrate = dao.count() == 0 && legacyTasks.isNotEmpty()
        if (shouldMigrate) dao.insertAll(legacyTasks.map(::toEntity))
        dao.recoverRunningTasks()
        shouldMigrate
    }

    suspend fun insert(task: DownloadTask) {
        dao.insert(toEntity(task))
    }

    suspend fun get(id: String): DownloadTask? = dao.getById(id)?.let(::toTask)

    suspend fun hasQueued(): Boolean = dao.hasQueued()

    suspend fun countQueued(): Int = dao.countQueued()

    suspend fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        val current = get(id) ?: return
        dao.update(toEntity(transform(current)))
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun claimNextQueued(): DownloadTask? = database.withTransaction {
        val queued = dao.nextQueued() ?: return@withTransaction null
        if (dao.claimQueued(queued.id) == 1) toTask(queued.copy(status = DownloadStatus.RUNNING.name)) else null
    }

    private fun toEntity(task: DownloadTask) = DownloadTaskEntity(
        id = task.id,
        url = task.url,
        fileName = task.fileName,
        encryptedHeaders = headerCipher.encrypt(task.headers),
        saveToMovies = task.saveToMovies,
        status = task.status.name,
        downloadedBytes = task.downloadedBytes,
        totalBytes = task.totalBytes,
        partialPath = task.partialPath,
        hlsCompletedSegments = task.hlsCompletedSegments,
        hlsTotalSegments = task.hlsTotalSegments,
        downloadSpeedBps = task.downloadSpeedBps,
        isMerging = task.isMerging,
        hlsPlaylistUrl = task.hlsPlaylistUrl,
        hlsPlaylistSignature = task.hlsPlaylistSignature,
        outputUri = task.outputUri,
        retryCount = task.retryCount,
        error = task.error,
        createdAt = task.createdAt,
    )

    private fun toTask(entity: DownloadTaskEntity) = DownloadTask(
        id = entity.id,
        url = entity.url,
        fileName = entity.fileName,
        headers = runCatching { headerCipher.decrypt(entity.encryptedHeaders) }.getOrDefault(emptyMap()),
        saveToMovies = entity.saveToMovies,
        status = runCatching { DownloadStatus.valueOf(entity.status) }.getOrDefault(DownloadStatus.FAILED),
        downloadedBytes = entity.downloadedBytes,
        totalBytes = entity.totalBytes,
        partialPath = entity.partialPath,
        hlsCompletedSegments = entity.hlsCompletedSegments,
        hlsTotalSegments = entity.hlsTotalSegments,
        downloadSpeedBps = entity.downloadSpeedBps,
        isMerging = entity.isMerging,
        hlsPlaylistUrl = entity.hlsPlaylistUrl,
        hlsPlaylistSignature = entity.hlsPlaylistSignature,
        outputUri = entity.outputUri,
        retryCount = entity.retryCount,
        error = entity.error,
        createdAt = entity.createdAt,
    )
}
