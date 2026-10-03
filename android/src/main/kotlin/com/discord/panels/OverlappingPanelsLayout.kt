package com.discord.panels

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Discord's OverlappingPanels: children 0/1/2 are the start, center and end panels. The side panels
 * sit under the center panel at their edges and are revealed by sliding the center panel aside.
 * Desktop: panels open from code (buttons/back) rather than swipes, the side panel width is capped
 * for wide windows, and clicking the uncovered part of the center panel closes the open panel.
 */
open class OverlappingPanelsLayout : FrameLayout {
    enum class LockState {
        OPEN,
        CLOSE,
        UNLOCKED
    }

    enum class Panel {
        START,
        CENTER,
        END
    }

    interface PanelStateListener {
        fun onPanelStateChange(panelState: PanelState)
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var startPanel: View? = null
    private var centerPanel: View? = null
    private var endPanel: View? = null
    private var scrim: View? = null

    private var selectedPanel = Panel.CENTER
    private var startPanelState: PanelState = PanelState.Closed
    private var endPanelState: PanelState = PanelState.Closed
    private var startLock = LockState.UNLOCKED
    private var endLock = LockState.UNLOCKED
    private var animator: ValueAnimator? = null
    private var sidePanelWidth = 0

    private val startListeners = ArrayList<PanelStateListener>()
    private val endListeners = ArrayList<PanelStateListener>()

    private fun dp(v: Float): Int = (v * getResources().getDisplayMetrics().density + 0.5f).toInt()
    private val marginBetweenPanels get() = dp(8f)

    private fun initPanels() {
        if (centerPanel != null || getChildCount() < 3) return
        startPanel = getChildAt(0)
        centerPanel = getChildAt(1)
        endPanel = getChildAt(2)
        startPanel?.setVisibility(View.INVISIBLE)
        endPanel?.setVisibility(View.INVISIBLE)
        val s = View(getContext()).apply {
            setVisibility(View.GONE)
            setBackgroundColor(0x40000000)
            setClickable(true)
            setOnClickListener { closePanels() }
        }
        scrim = s
        addView(s, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        initPanels()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        initPanels()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width > 0) {
            // phone: the portrait width minus the visible sliver of the center panel; desktop: capped
            val closedCenterVisible = dp(60f)
            sidePanelWidth = minOf(width - closedCenterVisible - marginBetweenPanels, maxOf(dp(360f), width / 3))
                .coerceAtLeast(dp(200f).coerceAtMost(width))
            for (panel in listOfNotNull(startPanel, endPanel)) {
                val lp = panel.getLayoutParams()
                if (lp.width != sidePanelWidth) {
                    lp.width = sidePanelWidth
                    panel.setLayoutParams(lp)
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // keep an opened panel's offset right when the window is resized
        if (animator?.isRunning() != true) setCenterX(targetX(selectedPanel))
    }

    private fun isRtl() = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL

    private fun targetX(panel: Panel): Float {
        val shift = (sidePanelWidth + marginBetweenPanels).toFloat()
        return when (panel) {
            Panel.CENTER -> 0f
            Panel.START -> if (isRtl()) -shift else shift
            Panel.END -> if (isRtl()) shift else -shift
        }
    }

    private fun setCenterX(x: Float) {
        centerPanel?.setTranslationX(x)
        scrim?.setTranslationX(x)
    }

    fun getSelectedPanel(): Panel = selectedPanel

    fun openStartPanel() {
        if (startPanel == null) initPanels()
        animateTo(Panel.START)
    }

    fun openEndPanel() {
        if (endPanel == null) initPanels()
        animateTo(Panel.END)
    }

    fun closePanels() {
        animateTo(Panel.CENTER)
    }

    fun handleStartPanelWidthUpdate() = requestLayout()
    fun handleEndPanelWidthUpdate() = requestLayout()

    private fun dispatch(panel: Panel, state: PanelState) {
        when (panel) {
            Panel.START -> if (startPanelState != state) {
                startPanelState = state
                startListeners.toList().forEach { it.onPanelStateChange(state) }
            }
            Panel.END -> if (endPanelState != state) {
                endPanelState = state
                endListeners.toList().forEach { it.onPanelStateChange(state) }
            }
            Panel.CENTER -> {}
        }
    }

    private fun animateTo(panel: Panel) {
        val previous = selectedPanel
        if (previous == panel && animator?.isRunning() != true) return
        animator?.cancel()
        selectedPanel = panel
        if (previous != Panel.CENTER && previous != panel) dispatch(previous, PanelState.Closing)
        when (panel) {
            Panel.START -> {
                startPanel?.setVisibility(View.VISIBLE)
                endPanel?.setVisibility(View.INVISIBLE)
                dispatch(Panel.START, PanelState.Opening)
            }
            Panel.END -> {
                endPanel?.setVisibility(View.VISIBLE)
                startPanel?.setVisibility(View.INVISIBLE)
                dispatch(Panel.END, PanelState.Opening)
            }
            Panel.CENTER -> {}
        }
        scrim?.setVisibility(if (panel == Panel.CENTER) View.GONE else View.VISIBLE)
        val from = centerPanel?.getTranslationX() ?: 0f
        val to = targetX(panel)
        val finish = {
            setCenterX(to)
            if (panel == Panel.CENTER) {
                startPanel?.setVisibility(View.INVISIBLE)
                endPanel?.setVisibility(View.INVISIBLE)
            }
            if (previous != Panel.CENTER && previous != panel) dispatch(previous, PanelState.Closed)
            if (panel != Panel.CENTER) dispatch(panel, PanelState.Opened)
        }
        animator = ValueAnimator.ofFloat(from, to).apply {
            setDuration(200)
            addUpdateListener { setCenterX(it.getAnimatedValue() as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) finish()
                }
            })
            start()
        }
    }

    fun setStartPanelLockState(lockState: LockState) {
        startLock = lockState
        when (lockState) {
            LockState.OPEN -> openStartPanel()
            LockState.CLOSE -> if (selectedPanel == Panel.START) closePanels()
            LockState.UNLOCKED -> {}
        }
    }

    fun setEndPanelLockState(lockState: LockState) {
        endLock = lockState
        when (lockState) {
            LockState.OPEN -> openEndPanel()
            LockState.CLOSE -> if (selectedPanel == Panel.END) closePanels()
            LockState.UNLOCKED -> {}
        }
    }

    fun registerStartPanelStateListeners(vararg listeners: PanelStateListener) {
        startListeners.addAll(listeners)
    }

    fun registerEndPanelStateListeners(vararg listeners: PanelStateListener) {
        endListeners.addAll(listeners)
    }

    fun registerPanelStateListener(listener: PanelStateListener) {
        startListeners.add(listener)
        endListeners.add(listener)
    }

    fun unregisterPanelStateListener(listener: PanelStateListener) {
        startListeners.remove(listener)
        endListeners.remove(listener)
    }

    /** Swipe gestures are not used on desktop, so the child gesture regions need no handling */
    fun setChildGestureRegions(gestureRegions: List<Rect>) {}
}
