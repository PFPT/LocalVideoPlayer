package com.pfpt.localvideoplayer.player

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.R
import kotlin.math.abs

@OptIn(UnstableApi::class)
object FloatingPlayerWindow {
    private const val CONTROLS_HIDE_MS = 3_000L
    private const val CORNER_DP = 28
    private const val CORNER_MARK_DP = 16

    private var root: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var windowManager: WindowManager? = null
    private var playbackListener: Player.Listener? = null
    private var boundPlayer: Player? = null
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var savedX: Int? = null
    private var savedY: Int? = null
    private var savedWidth: Int? = null
    private var savedHeight: Int? = null
    private var callbacksRegistered = false
    private var transport: View? = null
    private var topActions: View? = null
    private var cornerMarks: List<View> = emptyList()
    private var awaitingOverlayPermission = false
    private var previousButton: ImageButton? = null
    private var playButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var controlsVisible = false
    private var pendingTap: Runnable? = null
    private val hideControls = Runnable { setControlsVisible(false) }

    private val callbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            relayout()
        }

        override fun onLowMemory() = Unit
    }

    fun isAttached(): Boolean = root != null

    fun request(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            awaitingOverlayPermission = true
            Toast.makeText(
                context,
                context.getString(R.string.overlay_permission_required),
                Toast.LENGTH_SHORT,
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return false
        }
        awaitingOverlayPermission = false
        return show()
    }

    fun openIfOverlayGranted(context: Context): Boolean {
        if (!awaitingOverlayPermission) return false
        if (!Settings.canDrawOverlays(context)) return false
        awaitingOverlayPermission = false
        return show()
    }

    fun clearOverlayRetry() {
        awaitingOverlayPermission = false
    }

    private fun show(): Boolean {
        if (FloatingPlayback.isShowing() || isAttached()) return false
        FloatingPlayback.setShowing(true)
        return true
    }

    fun attach(context: Context, videoWidth: Int, videoHeight: Int): Boolean {
        if (root != null) return true
        val player = PlayerSession.current() ?: return false
        val app = context.applicationContext
        val manager = app.getSystemService(WindowManager::class.java)
        this.videoWidth = videoWidth
        this.videoHeight = videoHeight
        val (screenWidth, screenHeight) = screenSize(manager)
        val (defaultWidth, defaultHeight) = floatingWindowSize(screenWidth, screenHeight, videoWidth, videoHeight)
        val margin = dp(app, 16)
        val width = savedWidth ?: defaultWidth
        val height = savedHeight ?: defaultHeight
        val placed = fitFloatingFrame(
            FloatingFrame(
                x = savedX ?: (screenWidth - width - margin),
                y = savedY ?: margin,
                width = width,
                height = height,
            ),
            screenWidth,
            screenHeight,
        )
        val frame = FrameLayout(app)
        val background = GradientDrawable().apply {
            setColor(android.graphics.Color.BLACK)
            cornerRadius = dp(app, 12).toFloat()
        }
        frame.background = background
        frame.outlineProvider = ViewOutlineProvider.BACKGROUND
        frame.clipToOutline = true
        val surface = LayoutInflater.from(app).inflate(R.layout.floating_player_view, frame, false) as PlayerView
        surface.player = player
        frame.addView(
            surface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val drag = View(app)
        frame.addView(
            drag,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val cornerSize = dp(app, CORNER_DP)
        val markSize = dp(app, CORNER_MARK_DP)
        val handles = FloatingCorner.entries.associateWith { corner ->
            View(app).also { handle ->
                frame.addView(
                    handle,
                    FrameLayout.LayoutParams(cornerSize, cornerSize, Gravity.TOP or Gravity.LEFT),
                )
                handle.setOnTouchListener(cornerTouch(frame, manager, app, corner))
            }
        }
        val marks = FloatingCorner.entries.associateWith { corner ->
            CornerMarkView(app, corner).also { mark ->
                mark.visibility = View.INVISIBLE
                frame.addView(
                    mark,
                    FrameLayout.LayoutParams(markSize, markSize, Gravity.TOP or Gravity.LEFT),
                )
            }
        }
        cornerMarks = marks.values.toList()
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val widthPx = frame.width
            val heightPx = frame.height
            handles.forEach { (corner, handle) ->
                val placed = floatingCornerHandle(widthPx, heightPx, cornerSize, corner)
                handle.x = placed.x.toFloat()
                handle.y = placed.y.toFloat()
            }
            marks.forEach { (corner, mark) ->
                val placed = floatingCornerHandle(widthPx, heightPx, markSize, corner)
                mark.x = placed.x.toFloat()
                mark.y = placed.y.toFloat()
            }
        }
        val previous = iconButton(app)
        val play = iconButton(app)
        val next = iconButton(app)
        val close = iconButton(app)
        previous.setImageResource(android.R.drawable.ic_media_previous)
        previous.contentDescription = app.getString(R.string.previous)
        next.setImageResource(android.R.drawable.ic_media_next)
        next.contentDescription = app.getString(R.string.next)
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        close.contentDescription = app.getString(R.string.floating_close)
        previous.setOnClickListener {
            if (!previous.isEnabled) return@setOnClickListener
            PlayerSession.skipBy(-1)
            syncTransport(app)
            scheduleHide(frame)
        }
        next.setOnClickListener {
            if (!next.isEnabled) return@setOnClickListener
            PlayerSession.skipBy(1)
            syncTransport(app)
            scheduleHide(frame)
        }
        play.setOnClickListener {
            PlayerSession.togglePlayPause()
            syncTransport(app)
            scheduleHide(frame)
        }
        close.setOnClickListener {
            dismiss(pause = true)
        }
        val open = iconButton(app)
        open.setImageResource(R.drawable.ic_floating_return)
        open.contentDescription = app.getString(R.string.floating_return)
        open.setOnClickListener { openApp(app) }
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncTransport(app)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncTransport(app)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                syncTransport(app)
            }
        }
        player.addListener(listener)
        val buttonSize = dp(app, 36)
        val bar = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pad = dp(app, 4)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
        }
        listOf(previous, play, next).forEachIndexed { index, button ->
            val params = LinearLayout.LayoutParams(buttonSize, buttonSize)
            if (index > 0) params.marginStart = dp(app, 8)
            bar.addView(button, params)
        }
        frame.addView(
            bar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        val actions = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val actionGap = LinearLayout.LayoutParams(buttonSize, buttonSize)
        actions.addView(open, actionGap)
        val closeGap = LinearLayout.LayoutParams(buttonSize, buttonSize)
        closeGap.marginStart = dp(app, 8)
        actions.addView(close, closeGap)
        frame.addView(
            actions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                val inset = dp(app, 6)
                topMargin = inset
                marginEnd = inset
            },
        )
        val params = WindowManager.LayoutParams(
            placed.width,
            placed.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = placed.x
            y = placed.y
        }
        val slop = ViewConfiguration.get(app).scaledTouchSlop
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        drag.setOnTouchListener(object : View.OnTouchListener {
            private var originX = 0
            private var originY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val layout = frame.layoutParams as WindowManager.LayoutParams
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        originX = layout.x
                        originY = layout.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (abs(dx) > slop || abs(dy) > slop) {
                            moved = true
                            cancelPendingTap(frame)
                        }
                        if (moved) {
                            val (widthPx, heightPx) = screenSize(manager)
                            layout.x = clampWindowOffset(originX + dx, 0, widthPx - layout.width)
                            layout.y = clampWindowOffset(originY + dy, 0, heightPx - layout.height)
                            manager.updateViewLayout(frame, layout)
                            rememberFrame(layout)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        when (
                            floatingPointerAction(
                                movedPastSlop = moved,
                                secondTapWithinTimeout = pendingTap != null,
                            )
                        ) {
                            FloatingPointerAction.Move -> Unit
                            FloatingPointerAction.OpenPlayer -> {
                                cancelPendingTap(frame)
                                openApp(app)
                            }
                            FloatingPointerAction.ToggleControls -> {
                                val task = Runnable {
                                    pendingTap = null
                                    setControlsVisible(!controlsVisible)
                                }
                                pendingTap = task
                                frame.postDelayed(task, doubleTapTimeout)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                    else -> return false
                }
            }
        })
        try {
            manager.addView(frame, params)
        } catch (error: SecurityException) {
            player.removeListener(listener)
            surface.player = null
            return false
        } catch (error: WindowManager.BadTokenException) {
            player.removeListener(listener)
            surface.player = null
            return false
        }
        root = frame
        playerView = surface
        windowManager = manager
        playbackListener = listener
        boundPlayer = player
        transport = bar
        topActions = actions
        previousButton = previous
        playButton = play
        nextButton = next
        controlsVisible = false
        syncTransport(app)
        rememberFrame(params)
        if (!callbacksRegistered) {
            app.registerComponentCallbacks(callbacks)
            callbacksRegistered = true
        }
        return true
    }

    fun dismiss(pause: Boolean) {
        val frame = root
        val manager = windowManager
        frame?.removeCallbacks(hideControls)
        cancelPendingTap(frame)
        if (frame != null && manager != null) {
            val layout = frame.layoutParams as? WindowManager.LayoutParams
            if (layout != null) rememberFrame(layout)
            val player = boundPlayer
            val listener = playbackListener
            if (pause) player?.pause()
            if (player != null && listener != null) {
                player.removeListener(listener)
            }
            playerView?.player = null
            manager.removeViewImmediate(frame)
        }
        val app = frame?.context?.applicationContext
        if (callbacksRegistered && app != null) {
            app.unregisterComponentCallbacks(callbacks)
            callbacksRegistered = false
        }
        root = null
        playerView = null
        windowManager = null
        playbackListener = null
        boundPlayer = null
        transport = null
        topActions = null
        cornerMarks = emptyList()
        previousButton = null
        playButton = null
        nextButton = null
        controlsVisible = false
        FloatingPlayback.setShowing(false)
    }

    private fun relayout() {
        val frame = root ?: return
        val manager = windowManager ?: return
        val layout = frame.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenWidth, screenHeight) = screenSize(manager)
        val fitted = fitFloatingFrame(
            FloatingFrame(layout.x, layout.y, layout.width, layout.height),
            screenWidth,
            screenHeight,
        )
        layout.x = fitted.x
        layout.y = fitted.y
        layout.width = fitted.width
        layout.height = fitted.height
        manager.updateViewLayout(frame, layout)
        rememberFrame(layout)
    }

    private fun cornerTouch(
        frame: FrameLayout,
        manager: WindowManager,
        context: Context,
        corner: FloatingCorner,
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var origin = FloatingFrame(0, 0, 1, 1)

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val layout = frame.layoutParams as WindowManager.LayoutParams
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        origin = FloatingFrame(layout.x, layout.y, layout.width, layout.height)
                        setCornerMarksVisible(true)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val (screenWidth, screenHeight) = screenSize(manager)
                        val resized = resizeFloatingFrame(
                            origin = origin,
                            corner = corner,
                            fingerX = event.rawX.toInt(),
                            fingerY = event.rawY.toInt(),
                            ratio = windowRatio(layout),
                            minWidth = floatingMinWidthPx(
                                minOf(screenWidth, screenHeight),
                                context.resources.displayMetrics.density,
                            ),
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                        )
                        layout.x = resized.x
                        layout.y = resized.y
                        layout.width = resized.width
                        layout.height = resized.height
                        manager.updateViewLayout(frame, layout)
                        rememberFrame(layout)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        setCornerMarksVisible(false)
                        return true
                    }
                    else -> return false
                }
            }
        }
    }

    private fun windowRatio(layout: WindowManager.LayoutParams): Float {
        if (videoWidth > 0 && videoHeight > 0) {
            return (videoWidth.toFloat() / videoHeight).coerceIn(100f / 239f, 239f / 100f)
        }
        if (layout.height > 0) return layout.width.toFloat() / layout.height
        return 16f / 9f
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        val visibility = if (visible) View.VISIBLE else View.GONE
        transport?.visibility = visibility
        topActions?.visibility = visibility
        val frame = root ?: return
        frame.removeCallbacks(hideControls)
        if (visible) frame.postDelayed(hideControls, CONTROLS_HIDE_MS)
    }

    private fun scheduleHide(frame: FrameLayout) {
        if (!controlsVisible) return
        frame.removeCallbacks(hideControls)
        frame.postDelayed(hideControls, CONTROLS_HIDE_MS)
    }

    private fun cancelPendingTap(frame: FrameLayout?) {
        val task = pendingTap ?: return
        frame?.removeCallbacks(task)
        pendingTap = null
    }

    private fun syncTransport(context: Context) {
        val play = playButton
        if (play != null) syncPlayButton(play, context)
        val player = PlayerSession.current()
        val index = player?.currentMediaItemIndex ?: 0
        val hasPrevious = player != null && index > 0
        val hasNext = player?.hasNextMediaItem() == true
        previousButton?.let { button ->
            button.isEnabled = hasPrevious
            button.alpha = if (hasPrevious) 1f else 0.35f
        }
        nextButton?.let { button ->
            button.isEnabled = hasNext
            button.alpha = if (hasNext) 1f else 0.35f
        }
    }

    private fun rememberFrame(layout: WindowManager.LayoutParams) {
        savedX = layout.x
        savedY = layout.y
        savedWidth = layout.width
        savedHeight = layout.height
    }

    private fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra(PlaybackMediaService.EXTRA_OPEN_PLAYER, true)
        }
        context.startActivity(intent)
    }

    private fun syncPlayButton(button: ImageButton, context: Context) {
        val player = PlayerSession.current()
        val playing = player != null && showsPauseIcon(player.playWhenReady, player.playbackState)
        button.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        button.contentDescription = context.getString(
            if (playing) R.string.pip_pause else R.string.pip_play,
        )
    }

    private fun iconButton(context: Context): ImageButton {
        return ImageButton(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x88000000.toInt())
            }
            setColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
            val pad = dp(context, 6)
            setPadding(pad, pad, pad, pad)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun setCornerMarksVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        cornerMarks.forEach { mark -> mark.visibility = visibility }
    }

    private class CornerMarkView(
        context: Context,
        private val corner: FloatingCorner,
    ) : View(context) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.SQUARE
            strokeWidth = 2f * resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            val inset = stroke.strokeWidth / 2f
            val right = width - inset
            val bottom = height - inset
            val along = if (corner == FloatingCorner.TopLeft || corner == FloatingCorner.BottomLeft) {
                width * 0.72f
            } else {
                width * 0.28f
            }
            val down = if (corner == FloatingCorner.TopLeft || corner == FloatingCorner.TopRight) {
                height * 0.72f
            } else {
                height * 0.28f
            }
            val x = if (corner == FloatingCorner.TopLeft || corner == FloatingCorner.BottomLeft) inset else right
            val y = if (corner == FloatingCorner.TopLeft || corner == FloatingCorner.TopRight) inset else bottom
            canvas.drawLine(x, y, along, y, stroke)
            canvas.drawLine(x, y, x, down, stroke)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    private fun screenSize(windowManager: WindowManager): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
}
