package com.ojas.assistant.ui.galaxy

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * The interactive surface the galaxy lives on.
 *
 * Drag spins it like a turntable, pinch pulls the camera in and out, and a fling keeps
 * it turning with inertia that decays to the ambient drift. Motion is driven from
 * [Choreographer] rather than a timer so it stays locked to the display refresh.
 */
class GalaxyView(context: Context) : GLSurfaceView(context) {

    private val renderer = GalaxyRenderer(DEFAULT_STARS)

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var pointerDownAt = 0L
    private var movedBeyondSlop = false

    private var yawVelocity = 0f
    private var pitchVelocity = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** When false the view is a static backdrop: no touch handling, no per-frame work. */
    var interactive: Boolean = true

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.distance = (renderer.distance / detector.scaleFactor)
                    .coerceIn(GalaxyRenderer.MIN_DISTANCE, GalaxyRenderer.MAX_DISTANCE)
                return true
            }
        }
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!attached) return
            applyInertia()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private var attached = false

    init {
        setEGLContextClientVersion(2)
        // No alpha, no depth, no stencil: the galaxy is an opaque backdrop and the
        // renderer sorts nothing, so the cheapest config is also the correct one.
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
        isClickable = true
    }

    // --------------------------------------------------------------- public API

    fun setStarCount(count: Int) = renderer.setStarCount(count)

    /**
     * Reduced motion parks the galaxy: the ambient spin stops and the surface only
     * redraws when something actually changes, which takes the GPU cost to zero.
     */
    fun setReducedMotion(reduced: Boolean) {
        renderer.spinEnabled = !reduced
        renderMode = if (reduced) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
        if (reduced) requestRender()
    }

    fun setTargetFps(fps: Int) {
        renderer.targetFrameMillis = if (fps <= 0) 0L else (1000L / fps).coerceAtLeast(8L)
    }

    fun resetOrientation() {
        renderer.yawDegrees = 0f
        renderer.pitchDegrees = 0f
        renderer.distance = GalaxyRenderer.DEFAULT_DISTANCE
        yawVelocity = 0f
        pitchVelocity = 0f
        requestRender()
    }

    // ------------------------------------------------------------------ touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false

        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            dragging = false
            requestRender()
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                movedBeyondSlop = false
                pointerDownAt = System.currentTimeMillis()
                yawVelocity = 0f
                pitchVelocity = 0f
                // Let the drag win over any scrolling parent once it is clearly a drag.
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (!movedBeyondSlop && abs(dx) + abs(dy) < touchSlop) return true
                movedBeyondSlop = true
                lastX = event.x
                lastY = event.y

                applyDrag(dx, dy)
                // Velocity for the fling, smoothed so a jittery finger does not spin it.
                yawVelocity = yawVelocity * 0.55f + dx * DRAG_TO_DEGREES * 0.45f
                pitchVelocity = pitchVelocity * 0.55f + dy * DRAG_TO_DEGREES * 0.45f
                requestRender()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!movedBeyondSlop) {
                    yawVelocity = 0f
                    pitchVelocity = 0f
                    // A tap with no drag settles the galaxy back to face on.
                    if (System.currentTimeMillis() - pointerDownAt < TAP_TIMEOUT_MS) {
                        performClick()
                    }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyDrag(dx: Float, dy: Float) {
        renderer.yawDegrees = wrap(renderer.yawDegrees + dx * DRAG_TO_DEGREES)
        renderer.pitchDegrees =
            (renderer.pitchDegrees + dy * DRAG_TO_DEGREES).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    private fun applyInertia() {
        if (dragging || !interactive) return
        if (abs(yawVelocity) < MIN_VELOCITY && abs(pitchVelocity) < MIN_VELOCITY) {
            yawVelocity = 0f
            pitchVelocity = 0f
            return
        }
        applyDrag(yawVelocity / DRAG_TO_DEGREES, pitchVelocity / DRAG_TO_DEGREES)
        yawVelocity *= FRICTION
        pitchVelocity *= FRICTION
        requestRender()
    }

    // -------------------------------------------------------------- lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    private fun wrap(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    companion object {
        const val DEFAULT_STARS = 6000

        private const val DRAG_TO_DEGREES = 0.22f
        private const val MAX_PITCH = 88f
        private const val FRICTION = 0.94f
        private const val MIN_VELOCITY = 0.015f
        private const val TAP_TIMEOUT_MS = 220L
    }
}
