package com.pfpt.localvideoplayer.cast

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.PlaybackStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CastManager(
    context: Context,
    private val playbackStore: PlaybackStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val server = LocalCastServer(appContext)
    private val controller = CastPlayerController()
    private val castExecutor = Executors.newSingleThreadExecutor()
    private val castContextRef = AtomicReference<CastContext?>()

    private val _uiState = MutableStateFlow<CastUiState>(CastUiState.Idle)
    val uiState: StateFlow<CastUiState> = _uiState.asStateFlow()

    val playback: CastPlaybackFacade = controller

    private var mediaRouter: MediaRouter? = null
    private var routeCallback: MediaRouter.Callback? = null
    private var castPlayer: CastPlayer? = null
    private var localPlayer: Player? = null
    private var activeContentUri: Uri? = null
    private var activeTitle: String = ""
    private var activeRouteId: String? = null
    private var discovering = false
    private val devicesById = LinkedHashMap<String, CastDevice>()
    private val routeById = LinkedHashMap<String, MediaRouter.RouteInfo>()

    private val sessionListener = object : SessionManagerListener<Session> {
        override fun onSessionStarting(session: Session) = Unit

        override fun onSessionStarted(session: Session, sessionId: String) {
            val castSession = session as? CastSession
            val deviceName = castSession?.castDevice?.friendlyName
                ?: appContext.getString(R.string.cast)
            val routeId = activeRouteId
            val device = if (routeId != null) {
                devicesById[routeId] ?: CastDevice(id = routeId, name = deviceName)
            } else {
                CastDevice(id = sessionId, name = deviceName)
            }
            _uiState.value = CastUiState.Connected(device, activeTitle)
        }

        override fun onSessionStartFailed(session: Session, error: Int) {
            fallbackToLocal(appContext.getString(R.string.cast_unavailable))
        }

        override fun onSessionEnding(session: Session) = Unit

        override fun onSessionEnded(session: Session, error: Int) {
            fallbackToLocal(null)
        }

        override fun onSessionResuming(session: Session, sessionId: String) = Unit

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) = Unit

        override fun onSessionResumeFailed(session: Session, error: Int) {
            fallbackToLocal(appContext.getString(R.string.cast_unavailable))
        }

        override fun onSessionSuspended(session: Session, reason: Int) = Unit
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            fallbackToLocal(appContext.getString(R.string.cast_unavailable))
        }
    }

    fun ensureInitialized() {
        if (mediaRouter == null) {
            mediaRouter = MediaRouter.getInstance(appContext)
        }
        if (castContextRef.get() != null) return
        scope.launch {
            val context = withContext(Dispatchers.IO) {
                tryGetCastContext()
            }
            if (context != null && castContextRef.compareAndSet(null, context)) {
                context.sessionManager.addSessionManagerListener(sessionListener)
            }
        }
    }

    fun startDiscovery() {
        ensureInitialized()
        val router = mediaRouter ?: run {
            _uiState.value = CastUiState.Error(appContext.getString(R.string.cast_unavailable))
            return
        }
        discovering = true
        devicesById.clear()
        routeById.clear()
        _uiState.value = CastUiState.Searching
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastOptionsProvider.DEFAULT_RECEIVER_APP_ID),
            )
            .build()
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                upsertRoute(route)
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                upsertRoute(route)
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                devicesById.remove(route.id)
                routeById.remove(route.id)
                publishDevices()
            }
        }
        routeCallback?.let { router.removeCallback(it) }
        routeCallback = callback
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        router.routes.forEach { upsertRoute(it) }
        publishDevices()
    }

    fun stopDiscovery() {
        discovering = false
        routeCallback?.let { callback ->
            mediaRouter?.removeCallback(callback)
        }
        routeCallback = null
        if (_uiState.value is CastUiState.Searching || _uiState.value is CastUiState.Devices) {
            if (_uiState.value !is CastUiState.Connected && _uiState.value !is CastUiState.Connecting) {
                _uiState.value = CastUiState.Idle
            }
        }
    }

    fun bindLocalPlayer(player: Player?) {
        localPlayer = player
        controller.attachLocal(player)
    }

    fun connectAndPlay(
        device: CastDevice,
        title: String,
        contentUri: Uri,
        startPositionMs: Long,
        mimeType: String? = null,
    ) {
        ensureInitialized()
        val route = routeById[device.id]
        _uiState.value = CastUiState.Connecting(device)
        activeTitle = title
        activeContentUri = contentUri
        activeRouteId = device.id
        scope.launch {
            try {
                val context = castContextRef.get() ?: withContext(Dispatchers.IO) {
                    tryGetCastContext()?.also { loaded ->
                        if (castContextRef.compareAndSet(null, loaded)) {
                            loaded.sessionManager.addSessionManagerListener(sessionListener)
                        }
                    }
                }
                if (context == null) {
                    _uiState.value = CastUiState.Error(appContext.getString(R.string.cast_unavailable))
                    return@launch
                }
                val streamUrl = withContext(Dispatchers.IO) {
                    server.start(contentUri, mimeType)
                }
                val resolvedMime = mimeType
                    ?: appContext.contentResolver.getType(contentUri)
                    ?: "video/mp4"
                val player = castPlayer ?: CastPlayer(context).also { created ->
                    castPlayer = created
                    created.addListener(playerListener)
                }
                controller.attachCast(player)
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(resolvedMime)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .build(),
                    )
                    .build()
                if (route != null && !route.isSelected) {
                    mediaRouter?.selectRoute(route)
                }
                player.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
                player.prepare()
                player.playWhenReady = true
                localPlayer?.pause()
                controller.startListening()
                CastKeepAliveService.start(appContext)
                _uiState.value = CastUiState.Connected(device, title)
            } catch (e: Exception) {
                server.stop()
                CastKeepAliveService.stop(appContext)
                _uiState.value = CastUiState.Error(
                    e.message ?: appContext.getString(R.string.cast_unavailable),
                )
            }
        }
    }

    fun disconnect() {
        fallbackToLocal(null, resumeLocal = true)
    }

    fun release() {
        stopDiscovery()
        controller.stopListening()
        castPlayer?.removeListener(playerListener)
        castPlayer?.release()
        castPlayer = null
        castContextRef.get()?.sessionManager?.removeSessionManagerListener(sessionListener)
        server.stop()
        CastKeepAliveService.stop(appContext)
        localPlayer = null
        activeRouteId = null
        _uiState.value = CastUiState.Idle
        castExecutor.shutdownNow()
    }

    private fun upsertRoute(route: MediaRouter.RouteInfo) {
        if (!route.isEnabled || route.isDefault) return
        if (!route.supportsControlCategory(
                CastMediaControlIntent.categoryForCast(CastOptionsProvider.DEFAULT_RECEIVER_APP_ID),
            )
        ) {
            return
        }
        val device = CastDevice(
            id = route.id,
            name = route.name,
            description = route.description.orEmpty(),
        )
        devicesById[device.id] = device
        routeById[device.id] = route
        publishDevices()
    }

    private fun publishDevices() {
        if (!discovering) return
        if (_uiState.value is CastUiState.Connecting || _uiState.value is CastUiState.Connected) return
        val devices = devicesById.values.toList()
        _uiState.value = if (devices.isEmpty()) {
            CastUiState.Searching
        } else {
            CastUiState.Devices(devices)
        }
    }

    private fun fallbackToLocal(message: String?, resumeLocal: Boolean = true) {
        val position = controller.positionMs.value
        val duration = controller.durationMs.value
        val uri = activeContentUri
        val title = activeTitle
        controller.stopListening()
        try {
            castPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            castContextRef.get()?.sessionManager?.endCurrentSession(true)
        } catch (_: Exception) {
        }
        server.stop()
        CastKeepAliveService.stop(appContext)
        if (uri != null && title.isNotBlank()) {
            scope.launch {
                playbackStore.saveProgress(uri, title, position, duration)
            }
        }
        val player = localPlayer
        if (resumeLocal && player != null && position > 0) {
            player.seekTo(position)
            player.playWhenReady = true
        }
        activeContentUri = null
        activeTitle = ""
        activeRouteId = null
        _uiState.value = if (message != null) {
            CastUiState.Error(message)
        } else {
            CastUiState.Idle
        }
        if (message != null) {
            mainHandler.post {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun tryGetCastContext(): CastContext? {
        return try {
            CastContext.getSharedInstance(appContext, castExecutor).result
        } catch (_: Exception) {
            try {
                CastContext.getSharedInstance(appContext)
            } catch (_: Exception) {
                null
            }
        }
    }
}
