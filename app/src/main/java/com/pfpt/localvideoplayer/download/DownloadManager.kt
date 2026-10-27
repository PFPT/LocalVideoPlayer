package com.pfpt.localvideoplayer.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.pfpt.localvideoplayer.R
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val legacyFile = File(context.filesDir, "download_tasks.json")
    private val headerCipher = DownloadHeaderCipher(context)
    private val repository = DownloadTaskRepository(DownloadDatabase.get(context), headerCipher)
    private val ready = CompletableDeferred<Unit>()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    private val _maxConcurrentDownloads = MutableStateFlow(readMaxConcurrentDownloads())
    val maxConcurrentDownloadsFlow: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()
    private val _connectionsPerDownload = MutableStateFlow(readConnectionsPerDownload())
    val connectionsPerDownloadFlow: StateFlow<Int> = _connectionsPerDownload.asStateFlow()

    init {
        scope.launch {
            try {
                val migrated = runCatching { repository.initialize(readLegacyTasks()) }.getOrDefault(false)
                if (migrated) archiveLegacyFile()
            } finally {
                ready.complete(Unit)
            }
            repository.tasks.collect { current ->
                _tasks.value = current
                if (current.any { it.status == DownloadStatus.QUEUED }) {
                    scheduleRecovery()
                    startService()
                }
            }
        }
    }

    fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String>,
        saveToMovies: Boolean,
        trustFileName: Boolean = false,
        callback: ((Boolean) -> Unit)? = null,
    ) {
        scope.launch {
            val usedProxy = enqueueInternal(url, fileName, headers, saveToMovies, trustFileName)
            callback?.invoke(usedProxy)
        }
    }

    suspend fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String>,
        saveToMovies: Boolean,
        trustFileName: Boolean = false,
    ): Boolean = scope.async {
        enqueueInternal(url, fileName, headers, saveToMovies, trustFileName)
    }.await()

    private suspend fun enqueueInternal(
        url: String,
        fileName: String,
        headers: Map<String, String>,
        saveToMovies: Boolean,
        trustFileName: Boolean,
    ): Boolean {
        ready.await()
        val fallback = context.getString(R.string.download_default_file_name)
        val trustedFileName = fileName.trim().takeIf {
            it.isNotBlank() && (trustFileName || DownloadNameResolver.titleMatchesDownload(it, url))
        }.orEmpty()
        val resolvedName = DownloadNameResolver.resolveForEnqueue(
            url,
            trustedFileName,
            fallback,
            context,
            trustFileName && trustedFileName.isNotBlank(),
        )
        DownloadNameResolver.downloadCdnIdentityKey(url)?.let { cdnKey ->
            val cacheableTitle = DownloadNameResolver.cacheableTitle(resolvedName)
            DownloadTitleCache.put(context, cdnKey, cacheableTitle)
            DownloadNameResolver.downloadEpisodeIdentity(url)
                ?.folderStem
                ?.let(DownloadNameResolver::seriesStemPrefix)
                ?.let { stemPrefix ->
                    DownloadNameResolver.extractShowNameFromTitle(cacheableTitle)?.let { showName ->
                        DownloadTitleCache.putSeries(context, stemPrefix, showName)
                    }
                }
        }
        val resolvedHeaders = DownloadUrlResolver.inferHeaders(url, headers)
        val downloadUrl = DownloadUrlResolver.taskDownloadUrl(url)
        val usedProxy = DownloadUrlClassifier.isLocalProxyUrl(url)
        val task = DownloadTask(
            url = downloadUrl,
            fileName = resolvedName,
            headers = resolvedHeaders,
            saveToMovies = saveToMovies,
        )
        withContext(NonCancellable) {
            repository.insert(task)
        }
        startService()
        return usedProxy
    }

    fun setMaxConcurrentDownloads(limit: Int) {
        val stored = when {
            limit < 0 -> UNLIMITED_CONCURRENT
            else -> limit.coerceIn(1, MAX_CONCURRENT_DOWNLOADS)
        }
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_MAX_CONCURRENT_DOWNLOADS, stored)
            .apply()
        _maxConcurrentDownloads.value = stored
        startService()
    }

    fun setConnectionsPerDownload(connections: Int) {
        val stored = ProgressiveRangePlanner.clampConnections(connections)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_CONNECTIONS_PER_DOWNLOAD, stored)
            .apply()
        _connectionsPerDownload.value = stored
        startService()
    }

    internal fun connectionsPerDownload(): Int = readConnectionsPerDownload()

    fun pause(id: String) {
        scope.launch {
            ready.await()
            val task = repository.get(id) ?: return@launch
            repository.update(id) { it.copy(status = DownloadStatus.PAUSED) }
            if (task.status == DownloadStatus.RUNNING) {
                context.startService(
                    Intent(context, DownloadService::class.java)
                        .setAction(DownloadService.ACTION_STOP_CURRENT)
                        .putExtra(DownloadService.EXTRA_TASK_ID, id),
                )
            }
        }
    }

    fun resume(id: String) {
        scope.launch {
            ready.await()
            repository.update(id) { it.copy(status = DownloadStatus.QUEUED, error = null) }
            startService()
        }
    }

    fun redownload(id: String) {
        scope.launch {
            ready.await()
            val task = repository.get(id) ?: return@launch
            cleanupTaskFiles(task)
            repository.update(id) {
                it.copy(
                    status = DownloadStatus.QUEUED,
                    partialPath = null,
                    hlsCompletedSegments = 0,
                    hlsTotalSegments = 0,
                    downloadSpeedBps = 0,
                    isMerging = false,
                    hlsPlaylistUrl = null,
                    hlsPlaylistSignature = null,
                    outputUri = null,
                    downloadedBytes = 0,
                    totalBytes = -1,
                    error = null,
                )
            }
            startService()
        }
    }

    fun remove(id: String) {
        scope.launch {
            ready.await()
            val task = repository.get(id) ?: return@launch
            if (task.status == DownloadStatus.RUNNING) {
                context.startService(
                    Intent(context, DownloadService::class.java)
                        .setAction(DownloadService.ACTION_CANCEL_CURRENT)
                        .putExtra(DownloadService.EXTRA_TASK_ID, id),
                )
            }
            if (task.status != DownloadStatus.COMPLETED) cleanupTaskFiles(task)
            repository.delete(id)
        }
    }

    fun pauseAll() {
        _tasks.value
            .filter { task ->
                !task.isMerging &&
                    (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.RUNNING)
            }
            .forEach { pause(it.id) }
    }

    fun resumeAll() {
        _tasks.value
            .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
            .forEach { resume(it.id) }
    }

    fun clearCompleted() {
        _tasks.value
            .filter { it.status == DownloadStatus.COMPLETED }
            .forEach { remove(it.id) }
    }

    fun clearFailed() {
        _tasks.value
            .filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
            .forEach { remove(it.id) }
    }

    fun rename(id: String, newName: String, callback: (Boolean) -> Unit) {
        scope.launch {
            callback(renameInternal(id, newName))
        }
    }

    private suspend fun renameInternal(id: String, newName: String): Boolean {
        ready.await()
        val task = repository.get(id) ?: return false
        if (task.status != DownloadStatus.COMPLETED || task.outputUri.isNullOrBlank()) return false
        val finalName = DownloadNameResolver.composeRenameFileName(task.fileName, newName) ?: return false
        if (finalName == task.fileName) return true
        val uri = runCatching { Uri.parse(task.outputUri) }.getOrNull() ?: return false
        if (uri.scheme != "content") return false
        val updated = runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                },
                null,
                null,
            )
        }.getOrDefault(0)
        if (updated <= 0) return false
        repository.update(id) { it.copy(fileName = finalName) }
        return true
    }

    fun cancel(id: String) {
        scope.launch {
            ready.await()
            val task = repository.get(id) ?: return@launch
            repository.update(id) { it.copy(status = DownloadStatus.CANCELED, error = null) }
            if (task.status == DownloadStatus.RUNNING) {
                context.startService(
                    Intent(context, DownloadService::class.java)
                        .setAction(DownloadService.ACTION_CANCEL_CURRENT)
                        .putExtra(DownloadService.EXTRA_TASK_ID, id),
                )
            } else {
                cleanupTaskFiles(task)
                repository.update(id) {
                    it.copy(
                        partialPath = null,
                        hlsCompletedSegments = 0,
                        hlsTotalSegments = 0,
                        downloadSpeedBps = 0,
                        isMerging = false,
                        hlsPlaylistUrl = null,
                        hlsPlaylistSignature = null,
                        outputUri = null,
                        downloadedBytes = 0,
                        totalBytes = -1,
                    )
                }
            }
        }
    }

    internal suspend fun awaitReady() = ready.await()

    internal suspend fun claimNextQueued(): DownloadTask? = repository.claimNextQueued()

    internal suspend fun requeueOrphanedRunning(activeIds: Set<String>) {
        ready.await()
        repository.requeueOrphanedRunning(activeIds)
    }

    internal suspend fun hasQueued(): Boolean = repository.hasQueued()

    internal suspend fun hasPendingWork(): Boolean = repository.hasQueued()

    internal suspend fun countQueued(): Int = repository.countQueued()

    internal fun maxConcurrentDownloads(): Int = readMaxConcurrentDownloads()

    internal fun effectiveConcurrentLimit(queuedCount: Int): Int {
        val limit = maxConcurrentDownloads()
        return if (limit < 0) queuedCount.coerceAtLeast(1).coerceAtMost(HARD_CAP) else limit.coerceIn(1, HARD_CAP)
    }

    private fun readMaxConcurrentDownloads(): Int =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_MAX_CONCURRENT_DOWNLOADS, DEFAULT_MAX_CONCURRENT_DOWNLOADS)

    private fun readConnectionsPerDownload(): Int =
        ProgressiveRangePlanner.clampConnections(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getInt(PREF_CONNECTIONS_PER_DOWNLOAD, ProgressiveRangePlanner.DEFAULT_CONNECTIONS),
        )

    internal suspend fun get(id: String): DownloadTask? = repository.get(id)

    internal suspend fun update(id: String, transform: (DownloadTask) -> DownloadTask) = repository.update(id, transform)

    internal fun cleanupTaskFiles(task: DownloadTask) {
        DownloadService.hlsPackageDir(context, task.id).takeIf { it.exists() }?.deleteRecursively()
        DownloadService.torrentPackageDir(context, task.id).takeIf { it.exists() }?.deleteRecursively()
        DownloadService.cleanupRangeParts(context, task.id)
        task.partialPath?.let { path ->
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        task.outputUri?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_RUN_QUEUE),
            )
        } catch (_: IllegalStateException) {
            scheduleRecovery()
        } catch (_: SecurityException) {
            scheduleRecovery()
        }
    }

    private fun scheduleRecovery() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            RECOVERY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadRecoveryWorker>().build(),
        )
    }

    private fun readLegacyTasks(): List<DownloadTask> = runCatching {
        if (!legacyFile.exists()) return@runCatching emptyList()
        val array = JSONArray(legacyFile.readText())
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(it.toTask()) }
            }
        }
    }.getOrDefault(emptyList())

    private fun archiveLegacyFile() {
        if (!legacyFile.exists()) return
        val archive = File(context.filesDir, "${legacyFile.name}.migrated")
        if (archive.exists()) archive.delete()
        legacyFile.renameTo(archive)
    }

    private fun JSONObject.toTask(): DownloadTask {
        val headers = optString("encryptedHeaders")
            .takeIf { it.isNotBlank() }
            ?.let { encrypted -> runCatching { headerCipher.decrypt(encrypted) }.getOrDefault(emptyMap()) }
            ?: optJSONObject("headers")?.let { plaintext ->
                buildMap { plaintext.keys().forEach { key -> put(key, plaintext.optString(key)) } }
            }
            ?: emptyMap()
        return DownloadTask(
            id = getString("id"),
            url = getString("url"),
            fileName = getString("fileName"),
            headers = headers,
            saveToMovies = optBoolean("saveToMovies"),
            status = runCatching { DownloadStatus.valueOf(optString("status")) }.getOrDefault(DownloadStatus.FAILED),
            downloadedBytes = optLong("downloadedBytes"),
            totalBytes = optLong("totalBytes", -1),
            partialPath = optString("partialPath").takeIf { it.isNotBlank() && it != "null" },
            hlsCompletedSegments = optInt("hlsCompletedSegments"),
            hlsTotalSegments = optInt("hlsTotalSegments"),
            downloadSpeedBps = optLong("downloadSpeedBps"),
            isMerging = optBoolean("isMerging"),
            hlsPlaylistUrl = optString("hlsPlaylistUrl").takeIf { it.isNotBlank() && it != "null" },
            hlsPlaylistSignature = optString("hlsPlaylistSignature").takeIf { it.isNotBlank() && it != "null" },
            outputUri = optString("outputUri").takeIf { it.isNotBlank() && it != "null" },
            retryCount = optInt("retryCount"),
            error = optString("error").takeIf { it.isNotBlank() && it != "null" },
            createdAt = optLong("createdAt"),
        )
    }

    companion object {
        const val UNLIMITED_CONCURRENT = -1
        val CONCURRENT_DOWNLOAD_OPTIONS = listOf(1, 2, 4, 6, 8)
        val CONNECTIONS_PER_DOWNLOAD_OPTIONS = listOf(2, 4, 8, 12, 16, 24, 32)
        private const val RECOVERY_WORK_NAME = "download_recovery"
        private const val PREFERENCES_NAME = "download_manager"
        private const val PREF_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
        private const val PREF_CONNECTIONS_PER_DOWNLOAD = "connections_per_download"
        private const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = UNLIMITED_CONCURRENT
        private const val MAX_CONCURRENT_DOWNLOADS = 8
        private const val HARD_CAP = 12
        @Volatile private var instance: DownloadManager? = null

        fun get(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
