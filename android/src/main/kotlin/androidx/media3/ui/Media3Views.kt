package androidx.media3.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.lagradost.desktop.runtime.ui.DesktopVideoOutput
import com.lagradost.desktop.runtime.ui.MpvSurfaceView
import com.lagradost.desktop.runtime.ui.ViewAttributes
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs

private fun View.dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()).toInt()
private fun View.resId(type: String, name: String): Int = getContext().getResources().getIdentifier(name, type, null)

/** media3 AspectRatioFrameLayout: resizes itself to the content aspect ratio for the resize mode */
open class AspectRatioFrameLayout : FrameLayout {
    companion object {
        const val RESIZE_MODE_FIT = 0
        const val RESIZE_MODE_FIXED_WIDTH = 1
        const val RESIZE_MODE_FIXED_HEIGHT = 2
        const val RESIZE_MODE_FILL = 3
        const val RESIZE_MODE_ZOOM = 4
        private const val MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (attrs != null) ViewAttributes.reader(this, attrs).int("resize_mode")?.let { mResizeMode = it }
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var mResizeMode = RESIZE_MODE_FIT
    private var mAspectRatio = 0f

    open var resizeMode: Int
        get() = mResizeMode
        set(value) {
            if (mResizeMode != value) {
                mResizeMode = value
                requestLayout()
            }
        }

    open var aspectRatio: Float
        get() = mAspectRatio
        set(value) {
            if (mAspectRatio != value) {
                mAspectRatio = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (mAspectRatio <= 0f) return
        var width = getMeasuredWidth()
        var height = getMeasuredHeight()
        if (width == 0 || height == 0) return
        val viewAspectRatio = width.toFloat() / height
        val aspectDeformation = mAspectRatio / viewAspectRatio - 1
        if (abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) return
        when (mResizeMode) {
            RESIZE_MODE_FIXED_WIDTH -> height = (width / mAspectRatio).toInt()
            RESIZE_MODE_FIXED_HEIGHT -> width = (height * mAspectRatio).toInt()
            RESIZE_MODE_ZOOM -> if (aspectDeformation > 0) width = (height * mAspectRatio).toInt() else height = (width / mAspectRatio).toInt()
            RESIZE_MODE_FIT -> if (aspectDeformation > 0) height = (width / mAspectRatio).toInt() else width = (height * mAspectRatio).toInt()
            RESIZE_MODE_FILL -> {}
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}

interface TimeBar {
    interface OnScrubListener {
        fun onScrubStart(timeBar: TimeBar, position: Long)
        fun onScrubMove(timeBar: TimeBar, position: Long)
        fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean)
    }

    fun addListener(listener: OnScrubListener)
    fun removeListener(listener: OnScrubListener)
    fun setPosition(position: Long)
    fun setDuration(duration: Long)
    fun setBufferedPosition(bufferedPosition: Long)
    fun setKeyTimeIncrement(time: Long)
    fun setKeyCountIncrement(count: Int)
    fun setEnabled(enabled: Boolean)
    fun getPreferredUpdateDelay(): Long = 1000L
}

/** media3 DefaultTimeBar: unplayed, buffered and played bars with a scrubber; touch and arrow keys scrub */
open class DefaultTimeBar : View, TimeBar, com.lagradost.desktop.runtime.ui.DrawsItself {
    private val listeners = CopyOnWriteArraySet<TimeBar.OnScrubListener>()
    private val paint = Paint().apply { setAntiAlias(true) }
    private val rect = RectF()

    private var barHeight = dp(4f)
    private var touchTargetHeight = dp(26f)
    private var scrubberEnabledSize = dp(12f)
    private var scrubberDisabledSize = 0
    private var scrubberDraggedSize = dp(16f)
    private var playedColor = 0xFFFFFFFF.toInt()
    private var scrubberColor = 0xFFFFFFFF.toInt()
    private var bufferedColor = 0xCCFFFFFF.toInt()
    private var unplayedColor = 0x33FFFFFF

    private var duration = C.TIME_UNSET
    private var position = 0L
    private var bufferedPosition = 0L
    private var keyTimeIncrement = C.TIME_UNSET
    private var keyCountIncrement = 20
    private var scrubbing = false
    private var scrubPosition = 0L

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        if (attrs != null) {
            val r = ViewAttributes.reader(this, attrs)
            r.dim("bar_height")?.let { barHeight = it }
            r.dim("touch_target_height")?.let { touchTargetHeight = it }
            r.dim("scrubber_enabled_size")?.let { scrubberEnabledSize = it }
            r.dim("scrubber_disabled_size")?.let { scrubberDisabledSize = it }
            r.dim("scrubber_dragged_size")?.let { scrubberDraggedSize = it }
            r.colorStateList("played_color")?.let { playedColor = it.getDefaultColor() }
            scrubberColor = r.colorStateList("scrubber_color")?.getDefaultColor() ?: playedColor
            r.colorStateList("buffered_color")?.let { bufferedColor = it.getDefaultColor() }
            r.colorStateList("unplayed_color")?.let { unplayedColor = it.getDefaultColor() }
        }
        setFocusable(true)
    }

    private val scrubberPadding get() = maxOf(scrubberDisabledSize, maxOf(scrubberEnabledSize, scrubberDraggedSize)) / 2

    override fun addListener(listener: TimeBar.OnScrubListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: TimeBar.OnScrubListener) {
        listeners.remove(listener)
    }

    override fun setPosition(position: Long) {
        if (this.position == position) return
        this.position = position
        invalidate()
    }

    override fun setBufferedPosition(bufferedPosition: Long) {
        if (this.bufferedPosition == bufferedPosition) return
        this.bufferedPosition = bufferedPosition
        invalidate()
    }

    override fun setDuration(duration: Long) {
        if (this.duration == duration) return
        this.duration = duration
        if (scrubbing && duration == C.TIME_UNSET) stopScrubbing(true)
        invalidate()
    }

    override fun setKeyTimeIncrement(time: Long) {
        keyTimeIncrement = time
    }

    override fun setKeyCountIncrement(count: Int) {
        keyCountIncrement = count
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (scrubbing && !enabled) stopScrubbing(true)
        invalidate()
    }

    override fun getPreferredUpdateDelay(): Long {
        val barWidth = (getWidth() - getPaddingLeft() - getPaddingRight() - 2 * scrubberPadding).toLong()
        return if (duration == 0L || duration == C.TIME_UNSET || barWidth <= 0) Long.MAX_VALUE else duration / barWidth
    }

    open fun setPlayedColor(color: Int) {
        playedColor = color
        invalidate()
    }

    open fun setScrubberColor(color: Int) {
        scrubberColor = color
        invalidate()
    }

    open fun setBufferedColor(color: Int) {
        bufferedColor = color
        invalidate()
    }

    open fun setUnplayedColor(color: Int) {
        unplayedColor = color
        invalidate()
    }

    open fun showScrubber() {}
    open fun hideScrubber(disableScrubberPadding: Boolean) {}

    private fun barLeft() = getPaddingLeft() + scrubberPadding
    private fun barRight() = getWidth() - getPaddingRight() - scrubberPadding

    private fun positionToX(pos: Long): Float {
        val left = barLeft()
        val width = barRight() - left
        if (duration <= 0 || duration == C.TIME_UNSET || width <= 0) return left.toFloat()
        return left + width * (pos.coerceIn(0, duration).toFloat() / duration)
    }

    private fun xToPosition(x: Float): Long {
        val left = barLeft()
        val width = barRight() - left
        if (duration <= 0 || duration == C.TIME_UNSET || width <= 0) return 0
        return ((x - left) / width * duration).toLong().coerceIn(0, duration)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.UNSPECIFIED -> touchTargetHeight
            MeasureSpec.EXACTLY -> heightSize
            else -> minOf(touchTargetHeight, heightSize)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val top = (getHeight() - barHeight) / 2f
        val bottom = top + barHeight
        val left = barLeft().toFloat()
        val right = barRight().toFloat()
        if (right <= left) return
        fun bar(from: Float, to: Float, color: Int) {
            if (to <= from) return
            paint.setColor(color)
            rect.set(from, top, to, bottom)
            canvas.drawRect(rect, paint)
        }
        val shown = if (scrubbing) scrubPosition else position
        val playedX = if (duration > 0 && duration != C.TIME_UNSET) positionToX(shown) else left
        val bufferedX = if (duration > 0 && duration != C.TIME_UNSET) positionToX(maxOf(bufferedPosition, shown)) else left
        bar(left, right, unplayedColor)
        bar(playedX, bufferedX, bufferedColor)
        bar(left, playedX, playedColor)
        if (duration <= 0 || duration == C.TIME_UNSET) return
        val size = when {
            scrubbing || isFocused() -> scrubberDraggedSize
            isEnabled() -> scrubberEnabledSize
            else -> scrubberDisabledSize
        }
        if (size <= 0) return
        paint.setColor(scrubberColor)
        canvas.drawCircle(playedX, getHeight() / 2f, size / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled() || duration <= 0 || duration == C.TIME_UNSET) return false
        val x = event.getX()
        when (event.getActionMasked()) {
            MotionEvent.ACTION_DOWN -> {
                if (x < getPaddingLeft() || x > getWidth() - getPaddingRight()) return false
                startScrubbing(xToPosition(x))
                return true
            }
            MotionEvent.ACTION_MOVE -> if (scrubbing) {
                scrubPosition = xToPosition(x)
                for (l in listeners) l.onScrubMove(this, scrubPosition)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (scrubbing) {
                stopScrubbing(event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                return true
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled() && duration > 0 && duration != C.TIME_UNSET) {
            val increment = if (keyTimeIncrement != C.TIME_UNSET) keyTimeIncrement else duration / keyCountIncrement
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -increment
                KeyEvent.KEYCODE_DPAD_RIGHT -> increment
                else -> 0L
            }
            if (delta != 0L) {
                if (!scrubbing) startScrubbing(position)
                scrubPosition = (scrubPosition + delta).coerceIn(0, duration)
                for (l in listeners) l.onScrubMove(this, scrubPosition)
                invalidate()
                return true
            }
            if (scrubbing && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                stopScrubbing(false)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startScrubbing(pos: Long) {
        scrubPosition = pos
        scrubbing = true
        setPressed(true)
        getParent()?.requestDisallowInterceptTouchEvent(true)
        for (l in listeners) l.onScrubStart(this, pos)
        invalidate()
    }

    private fun stopScrubbing(canceled: Boolean) {
        scrubbing = false
        setPressed(false)
        getParent()?.requestDisallowInterceptTouchEvent(false)
        invalidate()
        for (l in listeners) l.onScrubStop(this, scrubPosition, canceled)
    }
}

/** media3 PlayerControlView: inflates controller_layout_id and keeps its exo_* views in sync with the player */
open class PlayerControlView : FrameLayout {
    fun interface ProgressUpdateListener {
        fun onProgressUpdate(position: Long, bufferedPosition: Long)
    }

    fun interface VisibilityListener {
        fun onVisibilityChange(visibility: Int)
    }

    private var playButton: View? = null
    private var pauseButton: View? = null
    private var playPauseButton: View? = null
    private var previousButton: View? = null
    private var nextButton: View? = null
    private var fastForwardButton: View? = null
    private var rewindButton: View? = null
    private var positionView: TextView? = null
    private var durationView: TextView? = null
    private var timeBar: TimeBar? = null

    private var progressUpdateListener: ProgressUpdateListener? = null
    private val visibilityListeners = CopyOnWriteArraySet<VisibilityListener>()
    private var scrubbing = false
    private var attached = false
    private val updateProgressAction = Runnable { updateProgress() }
    private val hideAction = Runnable { hide() }

    var showTimeoutMs: Int = 5000
        set(value) {
            field = value
            if (isVisible()) resetHideCallbacks()
        }

    private val componentListener = object : Player.Listener, TimeBar.OnScrubListener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateAll()
        override fun onPlaybackStateChanged(playbackState: Int) = updateAll()
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = updateProgress()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = updateAll()

        override fun onScrubStart(timeBar: TimeBar, position: Long) {
            scrubbing = true
            positionView?.setText(formatTime(position))
            removeCallbacks(hideAction)
        }

        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            positionView?.setText(formatTime(position))
        }

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            scrubbing = false
            if (!canceled) player?.seekTo(position)
            resetHideCallbacks()
            updateProgress()
        }
    }

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, playbackAttrs: AttributeSet?) : super(context, attrs, defStyleAttr) {
        var layoutId = resId("layout", "exo_player_control_view")
        if (playbackAttrs != null) {
            val r = ViewAttributes.reader(this, playbackAttrs)
            r.resourceId("controller_layout_id").takeIf { it != 0 }?.let { layoutId = it }
            r.int("show_timeout")?.let { showTimeoutMs = it }
        }
        LayoutInflater.from(getContext()).inflate(layoutId, this, true)
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS)

        playButton = findViewById(resId("id", "exo_play"))
        pauseButton = findViewById(resId("id", "exo_pause"))
        playPauseButton = findViewById(resId("id", "exo_play_pause"))
        previousButton = findViewById(resId("id", "exo_prev"))
        nextButton = findViewById(resId("id", "exo_next"))
        fastForwardButton = findViewById(resId("id", "exo_ffwd"))
        rewindButton = findViewById(resId("id", "exo_rew"))
        positionView = findViewById(resId("id", "exo_position"))
        durationView = findViewById(resId("id", "exo_duration"))
        timeBar = findViewById<View>(resId("id", "exo_progress")) as? TimeBar
        timeBar?.addListener(componentListener)

        playButton?.setOnClickListener { player?.play() }
        pauseButton?.setOnClickListener { player?.pause() }
        playPauseButton?.setOnClickListener { player?.let { if (it.isPlaying) it.pause() else it.play() } }
        previousButton?.setOnClickListener { player?.seekToPrevious() }
        nextButton?.setOnClickListener { player?.seekToNext() }
        fastForwardButton?.setOnClickListener { player?.seekForward() }
        rewindButton?.setOnClickListener { player?.seekBack() }
    }

    var player: Player? = null
        set(value) {
            if (field === value) return
            field?.removeListener(componentListener)
            field = value
            value?.addListener(componentListener)
            updateAll()
        }

    fun setProgressUpdateListener(listener: ProgressUpdateListener?) {
        progressUpdateListener = listener
    }

    fun getProgressUpdateListener(): ProgressUpdateListener? = progressUpdateListener

    fun addVisibilityListener(listener: VisibilityListener) {
        visibilityListeners.add(listener)
    }

    fun removeVisibilityListener(listener: VisibilityListener) {
        visibilityListeners.remove(listener)
    }

    fun isVisible(): Boolean = getVisibility() == VISIBLE
    fun isFullyVisible(): Boolean = isVisible()

    fun show() {
        if (!isVisible()) {
            setVisibility(VISIBLE)
            for (l in visibilityListeners) l.onVisibilityChange(getVisibility())
            updateAll()
        }
        resetHideCallbacks()
    }

    fun hide() {
        if (isVisible()) {
            setVisibility(GONE)
            for (l in visibilityListeners) l.onVisibilityChange(getVisibility())
            removeCallbacks(updateProgressAction)
            removeCallbacks(hideAction)
        }
    }

    fun hideImmediately() = hide()

    private fun resetHideCallbacks() {
        removeCallbacks(hideAction)
        if (showTimeoutMs > 0 && attached) postDelayed(hideAction, showTimeoutMs.toLong())
    }

    fun setShowFastForwardButton(show: Boolean) {}
    fun setShowRewindButton(show: Boolean) {}
    fun setShowPreviousButton(show: Boolean) {}
    fun setShowNextButton(show: Boolean) {}
    fun setShowMultiWindowTimeBar(show: Boolean) {}
    fun setRepeatToggleModes(modes: Int) {}
    fun setShowShuffleButton(show: Boolean) {}
    fun setTimeBarMinUpdateInterval(minUpdateIntervalMs: Int) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (isVisible()) resetHideCallbacks()
        updateAll()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attached = false
        removeCallbacks(updateProgressAction)
        removeCallbacks(hideAction)
    }

    private fun updateAll() {
        updatePlayPauseButton()
        updateProgress()
    }

    private fun updatePlayPauseButton() {
        if (!isVisible() || !attached) return
        val playing = player?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
        playButton?.setVisibility(if (playing) GONE else VISIBLE)
        pauseButton?.setVisibility(if (playing) VISIBLE else GONE)
    }

    private fun updateProgress() {
        removeCallbacks(updateProgressAction)
        if (!isVisible() || !attached) return
        val p = player
        val position = p?.currentPosition ?: 0L
        val buffered = p?.bufferedPosition ?: 0L
        val duration = p?.duration?.takeIf { it > 0 } ?: C.TIME_UNSET
        durationView?.setText(formatTime(duration))
        if (!scrubbing) positionView?.setText(formatTime(position))
        timeBar?.let {
            it.setDuration(duration)
            it.setBufferedPosition(buffered)
            if (!scrubbing) it.setPosition(position)
        }
        progressUpdateListener?.onProgressUpdate(position, buffered)
        val state = p?.playbackState ?: Player.STATE_IDLE
        if (p != null && state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            val preferred = timeBar?.getPreferredUpdateDelay() ?: 1000L
            val delay = if (p.isPlaying) preferred.coerceIn(200L, 1000L) else 1000L
            postDelayed(updateProgressAction, delay)
        }
    }

    private fun formatTime(timeMs: Long): String {
        val t = if (timeMs == C.TIME_UNSET) 0 else timeMs
        val totalSeconds = (t + 500) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * media3 PlayerView: inflates exo_player_view, puts the video surface first in exo_content_frame and
 * replaces exo_controller_placeholder with a PlayerControlView built from this view's attributes.
 */
open class PlayerView : FrameLayout {
    fun interface ControllerVisibilityListener {
        fun onVisibilityChanged(visibility: Int)
    }

    fun interface FullscreenButtonClickListener {
        fun onFullscreenButtonClick(isFullScreen: Boolean)
    }

    companion object {
        const val SHOW_BUFFERING_NEVER = 0
        const val SHOW_BUFFERING_WHEN_PLAYING = 1
        const val SHOW_BUFFERING_ALWAYS = 2
        const val ARTWORK_DISPLAY_MODE_OFF = 0
        const val ARTWORK_DISPLAY_MODE_FIT = 1
        const val ARTWORK_DISPLAY_MODE_FILL = 2
    }

    val surfaceView: MpvSurfaceView

    /** desktop: mouse behaviour of the player UI hosting this view */
    var desktopInput: com.lagradost.desktop.runtime.ui.DesktopPlayerInput? = null
    private var contentFrame: AspectRatioFrameLayout? = null
    private var shutterView: View? = null
    private var subtitleView: SubtitleView? = null
    private var bufferingView: View? = null
    private var errorMessageView: TextView? = null
    private var overlayFrameLayout: FrameLayout? = null
    private var adOverlayFrameLayout: FrameLayout? = null
    private var controller: PlayerControlView? = null
    private var showBuffering = SHOW_BUFFERING_NEVER
    private var controllerVisibilityListener: ControllerVisibilityListener? = null

    var useController: Boolean = true
        set(value) {
            field = value
            if (!value) controller?.hide() else controller?.player = player
        }

    var controllerAutoShow: Boolean = true
    var controllerHideOnTouch: Boolean = true
    var controllerShowTimeoutMs: Int
        get() = controller?.showTimeoutMs ?: 0
        set(value) {
            controller?.showTimeoutMs = value
        }

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        var layoutId = resId("layout", "exo_player_view")
        var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        var shutterColor: Int? = null
        if (attrs != null) {
            val r = ViewAttributes.reader(this, attrs)
            r.resourceId("player_layout_id").takeIf { it != 0 }?.let { layoutId = it }
            r.bool("use_controller")?.let { useController = it }
            r.int("resize_mode")?.let { resizeMode = it }
            r.int("show_buffering")?.let { showBuffering = it }
            r.bool("hide_on_touch")?.let { controllerHideOnTouch = it }
            r.bool("auto_show")?.let { controllerAutoShow = it }
            r.colorStateList("shutter_background_color")?.let { shutterColor = it.getDefaultColor() }
        }
        LayoutInflater.from(getContext()).inflate(layoutId, this, true)
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS)

        contentFrame = findViewById<View>(resId("id", "exo_content_frame")) as? AspectRatioFrameLayout
        contentFrame?.resizeMode = resizeMode
        shutterView = findViewById(resId("id", "exo_shutter"))
        shutterColor?.let { shutterView?.setBackgroundColor(it) }
        surfaceView = MpvSurfaceView(getContext())
        (contentFrame ?: this).addView(surfaceView, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        findViewById<View>(resId("id", "exo_artwork"))?.setVisibility(GONE)
        findViewById<View>(resId("id", "exo_image"))?.setVisibility(GONE)
        subtitleView = findViewById<View>(resId("id", "exo_subtitles")) as? SubtitleView
        bufferingView = findViewById<View>(resId("id", "exo_buffering"))?.also { it.setVisibility(GONE) }
        errorMessageView = findViewById<View>(resId("id", "exo_error_message")) as? TextView
        errorMessageView?.setVisibility(GONE)
        overlayFrameLayout = findViewById<View>(resId("id", "exo_overlay")) as? FrameLayout
        adOverlayFrameLayout = findViewById<View>(resId("id", "exo_ad_overlay")) as? FrameLayout

        val controllerId = resId("id", "exo_controller")
        val custom = findViewById<View>(controllerId) as? PlayerControlView
        val placeholder = findViewById<View>(resId("id", "exo_controller_placeholder"))
        controller = when {
            custom != null -> custom
            placeholder != null -> PlayerControlView(getContext(), null, 0, attrs).also { c ->
                c.setId(controllerId)
                c.setLayoutParams(placeholder.getLayoutParams())
                val parent = placeholder.getParent() as ViewGroup
                val index = parent.indexOfChild(placeholder)
                parent.removeView(placeholder)
                parent.addView(c, index)
            }
            else -> null
        }
        controller?.addVisibilityListener { visibility -> controllerVisibilityListener?.onVisibilityChanged(visibility) }
        if (attrs != null) ViewAttributes.reader(this, attrs).int("show_timeout")?.let { controller?.showTimeoutMs = it }
        controller?.hideImmediately()
    }

    private val componentListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = updateAspectRatio()
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateBuffering()
            maybeShowController()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = updateBuffering()
    }

    var player: Player? = null
        set(value) {
            if (field === value) return
            field?.removeListener(componentListener)
            (field as? DesktopVideoOutput)?.setVideoSurface(null)
            field = value
            if (useController) controller?.player = value
            subtitleView?.setCues(null)
            (value as? DesktopVideoOutput)?.setVideoSurface(surfaceView)
            value?.addListener(componentListener)
            updateAspectRatio()
            updateBuffering()
            maybeShowController()
        }

    private fun updateAspectRatio() {
        val size = player?.videoSize ?: return
        val ratio = if (size.height == 0 || size.width == 0) 0f else size.width * size.pixelWidthHeightRatio / size.height
        contentFrame?.aspectRatio = ratio
        shutterView?.setVisibility(if (ratio > 0f) GONE else VISIBLE)
    }

    private fun updateBuffering() {
        val p = player
        val show = p != null && p.playbackState == Player.STATE_BUFFERING &&
            (showBuffering == SHOW_BUFFERING_ALWAYS || (showBuffering == SHOW_BUFFERING_WHEN_PLAYING && p.playWhenReady))
        bufferingView?.setVisibility(if (show) VISIBLE else GONE)
    }

    private fun maybeShowController() {
        if (!useController || player == null || !controllerAutoShow) return
        controller?.show()
    }

    var resizeMode: Int
        get() = contentFrame?.resizeMode ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
        set(value) {
            contentFrame?.resizeMode = value
        }

    val videoSurfaceView: View? get() = surfaceView

    fun getSubtitleView(): SubtitleView? = subtitleView
    fun getOverlayFrameLayout(): FrameLayout? = overlayFrameLayout
    fun getAdOverlayFrameLayout(): FrameLayout? = adOverlayFrameLayout
    fun showController() = controller?.show()
    fun hideController() = controller?.hide()
    fun isControllerFullyVisible(): Boolean = controller?.isFullyVisible() == true
    fun setShowBuffering(showBuffering: Int) {
        this.showBuffering = showBuffering
        updateBuffering()
    }

    fun setControllerVisibilityListener(listener: ControllerVisibilityListener?) {
        controllerVisibilityListener = listener
    }

    fun setControllerVisibilityListener(listener: PlayerControlView.VisibilityListener?) {
        controllerVisibilityListener = listener?.let { l -> ControllerVisibilityListener { l.onVisibilityChange(it) } }
    }

    fun setFullscreenButtonClickListener(listener: FullscreenButtonClickListener?) {}
    fun setShowMultiWindowTimeBar(show: Boolean) = controller?.setShowMultiWindowTimeBar(show)
    fun setShutterBackgroundColor(color: Int) = shutterView?.setBackgroundColor(color)
    fun setErrorMessageProvider(provider: Any?) {}
    fun setKeepContentOnPlayerReset(keep: Boolean) {}
    fun setShowNextButton(show: Boolean) {}
    fun setShowPreviousButton(show: Boolean) {}
    fun setShowFastForwardButton(show: Boolean) {}
    fun setShowRewindButton(show: Boolean) {}
    fun setShowSubtitleButton(show: Boolean) {}
    fun setShowVrButton(show: Boolean) {}
    fun setArtworkDisplayMode(mode: Int) {}
    fun setDefaultArtwork(artwork: android.graphics.drawable.Drawable?) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!useController || player == null || !controllerHideOnTouch) return false
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (controller?.isVisible() == true) controller?.hide() else controller?.show()
        }
        return true
    }
}

open class SubtitleView : FrameLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun setCues(cues: List<androidx.media3.common.text.Cue>?) {}
    open fun setStyle(style: CaptionStyleCompat) {}
    open fun setFixedTextSize(unit: Int, size: Float) {}
    open fun setBottomPaddingFraction(fraction: Float) {}
    open fun setApplyEmbeddedStyles(applyEmbeddedStyles: Boolean) {}
    open fun setApplyEmbeddedFontSizes(applyEmbeddedFontSizes: Boolean) {}
    open fun setUserDefaultStyle() {}
    open fun setUserDefaultTextSize() {}
}
