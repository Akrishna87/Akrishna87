package io.github.akrishna87.myvideos

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps the player view and adds the usual video-player gestures on top of its own taps:
 * double-tap the left or right third to skip back or ahead, and swipe up/down on the left half for
 * brightness or the right half for volume. Single taps and the controls still reach the player.
 */
@SuppressLint("ViewConstructor")
class GestureLayout(context: Context, private val callbacks: Callbacks) : FrameLayout(context) {

    interface Callbacks {
        fun onDoubleTapSide(forward: Boolean)
        fun onVerticalDragStart(leftSide: Boolean)

        /** [fraction] is how far the finger has moved up since the drag started, as a share of the height. */
        fun onVerticalDrag(leftSide: Boolean, fraction: Float)
        fun onDragEnd()
    }

    /** Off in picture-in-picture, where the window is too small for gestures. */
    var gesturesEnabled = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    // Leave the top edge to the system (pulling down the status bar) and the bottom to the seek bar.
    private val topDeadZone = 48 * density
    private val bottomDeadZone = 110 * density

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragLeft = false
    /** This gesture belongs to us, not the player underneath. */
    private var takeOver = false

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val third = width / 3f
            val forward = when {
                e.x < third -> false
                e.x > width - third -> true
                else -> return false
            }
            if (e.y < topDeadZone || e.y > height - bottomDeadZone) return false
            takeOver = true
            callbacks.onDoubleTapSide(forward)
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (gesturesEnabled) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                takeOver = false
                dragging = false
                downX = ev.x
                downY = ev.y
            }
            detector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        if (takeOver) return true
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && !dragging) {
            val dx = ev.x - downX
            val dy = ev.y - downY
            val inZone = downY > topDeadZone && downY < height - bottomDeadZone
            if (inZone && abs(dy) > slop * 2 && abs(dy) > abs(dx) * 2) {
                dragging = true
                takeOver = true
                dragLeft = downX < width / 2f
                callbacks.onVerticalDragStart(dragLeft)
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility") // the player view underneath handles clicks and accessibility
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (dragging) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (height > 0) callbacks.onVerticalDrag(dragLeft, (downY - ev.y) / height)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    callbacks.onDragEnd()
                }
            }
        }
        return takeOver
    }
}
