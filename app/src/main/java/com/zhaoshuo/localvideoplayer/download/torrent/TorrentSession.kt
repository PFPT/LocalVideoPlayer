package com.zhaoshuo.localvideoplayer.download.torrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object TorrentSession {
    private val lock = Any()
    private val nativeLock = Any()
    private var session: SessionManager? = null
    private val activeInfoHashes = ConcurrentHashMap<String, Sha1Hash>()

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "flv", "ts", "m2ts", "mpeg", "mpg", "wmv", "3gp",
    )

    private val junkExtensions = setOf(
        "nfo", "txt", "jpg", "jpeg", "png", "gif", "bmp", "url", "exe", "html", "htm",
        "pdf", "doc", "docx", "rtf", "ssa", "ass", "srt", "idx", "sub", "torrent",
    )

    private val publicTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "http://tracker.openbittorrent.com:80/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://bt1.archive.org:6969/announce",
        "udp://bt2.archive.org:6969/announce",
        "wss://tracker.openwebtorrent.com",
    )

    private const val REANNOUNCE_INTERVAL_MS = 30_000L
    private const val LOW_SPEED_BPS = 64L * 1024L
    private const val LOW_PEER_COUNT = 8
    private const val UPLOAD_RATE_LIMIT_BPS = 1 * 1024 * 1024

    private val dhtBootstrapNodes = listOf(
        "dht.libtorrent.org:25401",
        "router.bittorrent.com:6881",
        "router.utorrent.com:6881",
        "dht.transmissionbt.com:6881",
        "router.bt.ouinet.work:6881",
        "dht.aelitis.com:6881",
    ).joinToString(",")

    fun ensureStarted(): SessionManager = synchronized(lock) {
        val existing = session
        if (existing != null && existing.isRunning) return existing
        val manager = SessionManager()
        manager.start(SessionParams(buildSpeedSettings()))
        session = manager
        manager
    }

    fun stop() {
        synchronized(lock) {
            val manager = session
            synchronized(nativeLock) {
                activeInfoHashes.keys.toList().forEach { taskId ->
                    removeTaskLocked(manager, taskId, deleteFiles = false)
                }
                activeInfoHashes.clear()
            }
            manager?.stop()
            session = null
        }
    }

    fun releaseForServiceDestroy() {
        synchronized(lock) {
            val manager = session ?: return
            if (activeInfoHashes.isNotEmpty()) {
                synchronized(nativeLock) {
                    activeInfoHashes.keys.toList().forEach { taskId ->
                        pauseTaskLocked(manager, taskId)
                    }
                }
                return
            }
            manager.stop()
            session = null
        }
    }

    suspend fun downloadMagnet(
        taskId: String,
        magnetUri: String,
        saveDir: File,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        onName: suspend (String) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        saveDir.mkdirs()
        val manager = ensureStarted()
        val magnetHash = parseMagnetInfoHash(magnetUri)
        val infoHashRef = AtomicReference(magnetHash)
        val torrentInfoRef = AtomicReference<TorrentInfo?>(null)
        val metadataLatch = CountDownLatch(1)
        val finishedLatch = CountDownLatch(1)
        val errorRef = AtomicReference<String?>(null)
        val prioritized = AtomicBoolean(false)
        val discoveryBoosted = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        var outcome = Outcome.FAILED

        if (magnetHash != null) {
            activeInfoHashes[taskId] = magnetHash
        }

        val listener = object : AlertListener {
            override fun types(): IntArray = intArrayOf(
                AlertType.ADD_TORRENT.swig(),
                AlertType.METADATA_RECEIVED.swig(),
                AlertType.TORRENT_FINISHED.swig(),
                AlertType.TORRENT_ERROR.swig(),
                AlertType.METADATA_FAILED.swig(),
            )

            override fun alert(alert: Alert<*>) {
                if (closed.get()) return
                when (alert.type()) {
                    AlertType.ADD_TORRENT -> {
                        val add = alert as AddTorrentAlert
                        if (add.error().value != 0) {
                            errorRef.set(add.error().message)
                            metadataLatch.countDown()
                            finishedLatch.countDown()
                            return
                        }
                        onHandleReady(add.handle())
                    }
                    AlertType.METADATA_RECEIVED -> {
                        onHandleReady((alert as MetadataReceivedAlert).handle())
                        metadataLatch.countDown()
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val hash = (alert as? TorrentAlert<*>)?.handle()?.let { safeInfoHash(it) }
                        val expected = infoHashRef.get()
                        if (hash == null || expected == null || hash == expected) {
                            finishedLatch.countDown()
                        }
                    }
                    AlertType.METADATA_FAILED, AlertType.TORRENT_ERROR -> {
                        errorRef.set(alert.message())
                        metadataLatch.countDown()
                        finishedLatch.countDown()
                    }
                    else -> Unit
                }
            }

            private fun onHandleReady(handle: TorrentHandle) {
                if (closed.get()) return
                synchronized(nativeLock) {
                    if (closed.get()) return
                    val hash = safeInfoHash(handle) ?: return
                    infoHashRef.compareAndSet(null, hash)
                    activeInfoHashes[taskId] = hash
                    boostPeerDiscoveryLocked(handle, discoveryBoosted)
                    captureMetadataLocked(handle)
                }
            }

            private fun captureMetadataLocked(handle: TorrentHandle) {
                val info = safeTorrentFile(handle) ?: return
                if (!info.isValid) return
                torrentInfoRef.compareAndSet(null, info)
                if (prioritized.compareAndSet(false, true)) {
                    applyVideoPrioritiesLocked(handle, info)
                }
                metadataLatch.countDown()
            }
        }

        manager.addListener(listener)
        try {
            val resumedExisting = attachExistingLocked(
                manager = manager,
                taskId = taskId,
                infoHashRef = infoHashRef,
                torrentInfoRef = torrentInfoRef,
                prioritized = prioritized,
                discoveryBoosted = discoveryBoosted,
                metadataLatch = metadataLatch,
                finishedLatch = finishedLatch,
            )
            if (!resumedExisting) {
                val attachedBeforeAdd = attachExistingLocked(
                    manager = manager,
                    taskId = taskId,
                    infoHashRef = infoHashRef,
                    torrentInfoRef = torrentInfoRef,
                    prioritized = prioritized,
                    discoveryBoosted = discoveryBoosted,
                    metadataLatch = metadataLatch,
                    finishedLatch = finishedLatch,
                )
                if (!attachedBeforeAdd) {
                    manager.download(magnetUri, saveDir, torrent_flags_t())
                    attachExistingLocked(
                        manager = manager,
                        taskId = taskId,
                        infoHashRef = infoHashRef,
                        torrentInfoRef = torrentInfoRef,
                        prioritized = prioritized,
                        discoveryBoosted = discoveryBoosted,
                        metadataLatch = metadataLatch,
                        finishedLatch = finishedLatch,
                    )
                }
            }

            while (!metadataLatch.await(500, TimeUnit.MILLISECONDS)) {
                checkControl(shouldStop, isCanceled, errorRef)
            }
            checkControl(shouldStop, isCanceled, errorRef)

            val info = waitForCachedInfo(torrentInfoRef, infoHashRef, manager, taskId, shouldStop, isCanceled, errorRef)
            onName(info.name())
            if (!prioritized.get()) {
                withHandle(manager, taskId, infoHashRef.get()) { handle ->
                    if (prioritized.compareAndSet(false, true)) {
                        applyVideoPrioritiesLocked(handle, info)
                    }
                }
            }

            var lastReannounceAt = 0L
            var handleMissCount = 0
            while (!finishedLatch.await(0, TimeUnit.MILLISECONDS)) {
                checkControl(shouldStop, isCanceled, errorRef)
                val progress = withHandle(manager, taskId, infoHashRef.get()) { handle ->
                    val status = handle.status()
                    val snapshot = ProgressSnapshot(
                        downloaded = status.totalWantedDone().coerceAtLeast(0L),
                        total = status.totalWanted().coerceAtLeast(0L),
                        speedBps = status.downloadPayloadRate().toLong().coerceAtLeast(0L),
                        finished = status.isFinished,
                        numPeers = status.numPeers(),
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastReannounceAt >= REANNOUNCE_INTERVAL_MS &&
                        (snapshot.numPeers < LOW_PEER_COUNT || snapshot.speedBps < LOW_SPEED_BPS)
                    ) {
                        runCatching {
                            handle.forceDHTAnnounce()
                            handle.forceReannounce()
                            handle.forceLSDAnnounce()
                        }
                        lastReannounceAt = now
                    }
                    snapshot
                }
                if (progress == null) {
                    handleMissCount++
                    if (handleMissCount >= 10) throw IOException("torrent handle invalid")
                    delay(500)
                    continue
                }
                handleMissCount = 0
                onProgress(progress.downloaded, if (progress.total > 0) progress.total else -1L, progress.speedBps)
                if (progress.finished || (progress.total > 0 && progress.downloaded >= progress.total)) break
                delay(500)
            }
            checkControl(shouldStop, isCanceled, errorRef)
            val finalProgress = withHandle(manager, taskId, infoHashRef.get()) { handle ->
                val status = handle.status()
                ProgressSnapshot(
                    downloaded = status.totalWantedDone().coerceAtLeast(0L),
                    total = status.totalWanted().coerceAtLeast(0L),
                    speedBps = 0L,
                    finished = true,
                )
            }
            if (finalProgress != null) {
                onProgress(finalProgress.downloaded, finalProgress.total, 0L)
            }
            val video = resolveLargestVideoFile(saveDir, info)
                ?: throw IOException("no video file in torrent")
            outcome = Outcome.COMPLETED
            video
        } catch (error: IOException) {
            outcome = when {
                isCanceled() -> Outcome.CANCELED
                shouldStop() -> Outcome.PAUSED
                else -> Outcome.FAILED
            }
            throw error
        } finally {
            closed.set(true)
            manager.removeListener(listener)
            synchronized(nativeLock) {
                when (outcome) {
                    Outcome.PAUSED -> pauseTaskLocked(manager, taskId)
                    Outcome.CANCELED -> removeTaskLocked(manager, taskId, deleteFiles = true)
                    Outcome.COMPLETED, Outcome.FAILED -> removeTaskLocked(manager, taskId, deleteFiles = false)
                }
            }
        }
    }

    private fun attachExistingLocked(
        manager: SessionManager,
        taskId: String,
        infoHashRef: AtomicReference<Sha1Hash?>,
        torrentInfoRef: AtomicReference<TorrentInfo?>,
        prioritized: AtomicBoolean,
        discoveryBoosted: AtomicBoolean,
        metadataLatch: CountDownLatch,
        finishedLatch: CountDownLatch,
    ): Boolean = synchronized(nativeLock) {
        val hash = infoHashRef.get() ?: return false
        val existing = manager.find(hash) ?: return false
        if (!safeIsValid(existing)) return false
        activeInfoHashes[taskId] = hash
        boostPeerDiscoveryLocked(existing, discoveryBoosted)
        val info = safeTorrentFile(existing)
        if (info != null && info.isValid) {
            torrentInfoRef.compareAndSet(null, info)
            if (prioritized.compareAndSet(false, true)) {
                applyVideoPrioritiesLocked(existing, info)
            }
            metadataLatch.countDown()
        }
        if (safeIsFinished(existing)) {
            finishedLatch.countDown()
        }
        true
    }

    private enum class Outcome {
        PAUSED,
        CANCELED,
        COMPLETED,
        FAILED,
    }

    private data class ProgressSnapshot(
        val downloaded: Long,
        val total: Long,
        val speedBps: Long,
        val finished: Boolean,
        val numPeers: Int = 0,
    )

    private fun parseMagnetInfoHash(magnetUri: String): Sha1Hash? = runCatching {
        AddTorrentParams.parseMagnetUri(magnetUri).infoHashes.getBest()
    }.getOrNull()

    private fun checkControl(
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        errorRef: AtomicReference<String?>,
    ) {
        errorRef.get()?.let { throw IOException(it) }
        if (shouldStop()) throw IOException("paused")
        if (isCanceled()) throw IOException("canceled")
    }

    private suspend fun waitForCachedInfo(
        torrentInfoRef: AtomicReference<TorrentInfo?>,
        infoHashRef: AtomicReference<Sha1Hash?>,
        manager: SessionManager,
        taskId: String,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        errorRef: AtomicReference<String?>,
    ): TorrentInfo {
        repeat(240) {
            torrentInfoRef.get()?.takeIf { it.isValid }?.let { return it }
            withHandle(manager, taskId, infoHashRef.get()) { handle ->
                val info = safeTorrentFile(handle)
                if (info != null && info.isValid) {
                    torrentInfoRef.compareAndSet(null, info)
                }
            }
            torrentInfoRef.get()?.takeIf { it.isValid }?.let { return it }
            checkControl(shouldStop, isCanceled, errorRef)
            delay(500)
        }
        throw IOException("torrent metadata timeout")
    }

    private inline fun <T> withHandle(
        manager: SessionManager,
        taskId: String,
        infoHash: Sha1Hash?,
        block: (TorrentHandle) -> T,
    ): T? = synchronized(nativeLock) {
        val hash = infoHash ?: activeInfoHashes[taskId] ?: return null
        activeInfoHashes[taskId] = hash
        val handle = manager.find(hash) ?: return null
        if (!safeIsValid(handle)) return null
        runCatching { block(handle) }.getOrNull()
    }

    private fun pauseTaskLocked(manager: SessionManager, taskId: String) {
        val hash = activeInfoHashes[taskId] ?: return
        if (!manager.isRunning) return
        val handle = manager.find(hash) ?: return
        if (!safeIsValid(handle)) return
        runCatching { handle.pause() }
    }

    private fun removeTaskLocked(manager: SessionManager?, taskId: String, deleteFiles: Boolean) {
        val hash = activeInfoHashes.remove(taskId) ?: return
        if (manager == null || !manager.isRunning) return
        val handle = manager.find(hash) ?: return
        if (!safeIsValid(handle)) return
        runCatching {
            if (deleteFiles) {
                manager.remove(handle, SessionHandle.DELETE_FILES)
            } else {
                manager.remove(handle)
            }
        }
    }

    private fun safeIsValid(handle: TorrentHandle): Boolean =
        runCatching { handle.isValid }.getOrDefault(false)

    private fun safeIsFinished(handle: TorrentHandle): Boolean =
        runCatching { handle.isValid && handle.status().isFinished }.getOrDefault(false)

    private fun safeInfoHash(handle: TorrentHandle): Sha1Hash? =
        runCatching { if (handle.isValid) handle.infoHash() else null }.getOrNull()

    private fun safeTorrentFile(handle: TorrentHandle): TorrentInfo? =
        runCatching { if (handle.isValid) handle.torrentFile() else null }.getOrNull()

    private fun buildSpeedSettings(): SettingsPack = SettingsPack().apply {
        connectionsLimit(800)
        downloadRateLimit(0)
        uploadRateLimit(UPLOAD_RATE_LIMIT_BPS)
        activeDownloads(-1)
        activeLimit(-1)
        activeSeeds(-1)
        maxPeerlistSize(8000)
        setEnableDht(true)
        setEnableLsd(true)
        setDhtBootstrapNodes(dhtBootstrapNodes)

        setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), 128)
        setInteger(settings_pack.int_types.connection_speed.swigValue(), 100)
        setInteger(settings_pack.int_types.num_optimistic_unchoke_slots.swigValue(), 16)
        setInteger(settings_pack.int_types.aio_threads.swigValue(), 4)

        setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
        setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
        setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
        setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true)
        setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true)
        setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
        setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        setBoolean(settings_pack.bool_types.prefer_udp_trackers.swigValue(), true)
        setBoolean(settings_pack.bool_types.dont_count_slow_torrents.swigValue(), true)
        setBoolean(settings_pack.bool_types.allow_multiple_connections_per_ip.swigValue(), true)
        setBoolean(settings_pack.bool_types.smooth_connects.swigValue(), false)
        setBoolean(settings_pack.bool_types.prioritize_partial_pieces.swigValue(), true)
        setBoolean(settings_pack.bool_types.dht_restrict_routing_ips.swigValue(), false)
        setBoolean(settings_pack.bool_types.dht_restrict_search_ips.swigValue(), false)
    }

    private fun boostPeerDiscoveryLocked(handle: TorrentHandle, boosted: AtomicBoolean) {
        if (!safeIsValid(handle)) return
        runCatching {
            handle.resume()
            handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
            handle.setUploadLimit(0)
            handle.setDownloadLimit(0)
            val existing = handle.trackers().map { it.url().lowercase() }.toHashSet()
            for (tracker in publicTrackers) {
                if (tracker.lowercase() in existing) continue
                handle.addTracker(AnnounceEntry(tracker))
            }
            if (boosted.compareAndSet(false, true)) {
                handle.forceDHTAnnounce()
                handle.forceReannounce()
                handle.forceLSDAnnounce()
            }
        }
    }

    private fun applyVideoPrioritiesLocked(handle: TorrentHandle, info: TorrentInfo) {
        if (!safeIsValid(handle)) return
        val count = info.numFiles()
        if (count <= 0) return
        val storage = info.files()
        val topIndex = (0 until count)
            .filter { index ->
                val ext = storage.fileName(index).substringAfterLast('.', "").lowercase()
                ext in videoExtensions
            }
            .maxByOrNull { storage.fileSize(it) }
            ?: (0 until count).maxByOrNull { storage.fileSize(it) }
            ?: 0
        val priorities = Array(count) { index ->
            val name = storage.fileName(index)
            val ext = name.substringAfterLast('.', "").lowercase()
            when {
                index == topIndex -> Priority.TOP_PRIORITY
                ext in junkExtensions || name.startsWith("_____padding_file") -> Priority.IGNORE
                else -> Priority.DEFAULT
            }
        }
        runCatching { handle.prioritizeFiles(priorities) }
    }

    private fun resolveLargestVideoFile(saveDir: File, info: TorrentInfo): File? {
        val storage = info.files()
        val count = info.numFiles()
        var bestFile: File? = null
        var bestSize = -1L
        for (index in 0 until count) {
            val name = storage.fileName(index)
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in videoExtensions) continue
            val relative = storage.filePath(index)
            val file = File(saveDir, relative)
            if (!file.isFile) continue
            val size = file.length()
            if (size > bestSize) {
                bestSize = size
                bestFile = file
            }
        }
        if (bestFile != null) return bestFile
        return saveDir.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in videoExtensions }
            .maxByOrNull { it.length() }
            ?: saveDir.walkTopDown().filter { it.isFile }.maxByOrNull { it.length() }
    }
}
