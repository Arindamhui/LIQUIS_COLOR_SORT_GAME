package com.liquidcolorsort.ui.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.liquidcolorsort.core.model.LiquidColor
import com.liquidcolorsort.core.model.Tube
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom Canvas view that renders a single liquid-sort tube.
 *
 * ## Coordinate system
 * The tube is drawn centred horizontally and filling most of the view's
 * height. Segments are stacked bottom-up. The tube outline, cork, and
 * shine overlay are drawn on top of the liquid fill.
 *
 * ## Animation
 * Pour animation is initiated by [animatePour]. It transitions between
 * two segment configurations using a [ValueAnimator]. The [onTapped]
 * callback notifies the Fragment when the user taps this view.
 *
 * ## Rendering pipeline (per-frame)
 * 1. Draw tube body background (glass-like rounded rect).
 * 2. Draw each liquid segment scaled by [animationFraction].
 * 3. Draw tube outline / cork.
 * 4. Draw gloss/shine overlay for depth.
 * 5. Draw selection highlight if [isSelected].
 *
 * @param context Android context.
 * @param attrs   XML attribute set (unused — created programmatically).
 */
class TubeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    // ── State ──────────────────────────────────────────────────────────────

    /** Current tube data to render. */
    var tube: Tube = Tube()
        set(value) {
            field = value
            invalidate()
        }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }

    /**
     * Whether this tube is the hint target (highlighted in a different color).
     */
    var isHintTarget: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Called when the user taps this view. */
    var onTapped: (() -> Unit)? = null

    // ── Animation state ────────────────────────────────────────────────────

    /**
     * Progress of the pour animation (0f = start, 1f = end).
     * When positive, [animatingFromSegments] / [animatingToSegments] define
     * the transition.
     */
    private var animationFraction: Float = 1f
    private var animatingFromSegments: List<LiquidColor> = emptyList()
    private var animatingToSegments:   List<LiquidColor> = emptyList()
    private var currentAnimator: ValueAnimator? = null

    // ── Paint objects (created once, reused) ──────────────────────────────

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tubePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#AAFFFFFF")
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1AFFFFFF")  // semi-transparent glass
    }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0.3f, 1f,
            Color.parseColor("#33FFFFFF"), Color.TRANSPARENT,
            Shader.TileMode.CLAMP)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#FFFFD700")  // gold highlight
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#FF00FFAA")  // teal hint highlight
    }
    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DFFFFFF") // semi-transparent white
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val patternFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DFFFFFF")
        style = Paint.Style.FILL
    }

    // ── Color palette (IDs 1–12) ──────────────────────────────────────────

    private val colorPalette = intArrayOf(
        0xFF000000.toInt(), // 0 = empty (unused)
        0xFFE53935.toInt(), // 1 = Red
        0xFF1E88E5.toInt(), // 2 = Blue
        0xFF43A047.toInt(), // 3 = Green
        0xFFFDD835.toInt(), // 4 = Yellow
        0xFFFF6D00.toInt(), // 5 = Orange
        0xFF8E24AA.toInt(), // 6 = Purple
        0xFF00ACC1.toInt(), // 7 = Cyan
        0xFFEC407A.toInt(), // 8 = Pink
        0xFF6D4C41.toInt(), // 9 = Brown
        0xFF78909C.toInt(), // 10 = Grey
        0xFF76FF03.toInt(), // 11 = Lime
        0xFFFF80AB.toInt(), // 12 = Rose
    )

    // ── Dimensions (computed in onSizeChanged) ─────────────────────────────

    private var tubeRect     = RectF()
    private var cornerRadius  = 0f
    private var segmentHeight = 0f
    private var tubeCapacity  = Tube.DEFAULT_CAPACITY

    // ── Overrides ──────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padH = w * 0.10f
        val padV = h * 0.04f
        val tubeWidth = w - padH * 2
        cornerRadius  = tubeWidth / 2f
        tubeRect = RectF(padH, padV, w - padH, h.toFloat() - padV)

        val totalHeight = tubeRect.height()
        segmentHeight   = totalHeight / tube.capacity
        tubeCapacity    = tube.capacity

        // Rebuild shine shader with correct dimensions
        shinePaint.shader = LinearGradient(
            tubeRect.left, tubeRect.top,
            tubeRect.left + tubeWidth * 0.35f, tubeRect.bottom,
            Color.parseColor("#44FFFFFF"), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        segmentHeight = tubeRect.height() / tubeCapacity.toFloat()

        // 1. Glass body background
        canvas.drawRoundRect(tubeRect, cornerRadius, cornerRadius, bodyPaint)

        // 2. Liquid segments
        val segments = interpolatedSegments()
        drawSegments(canvas, segments)

        // 3. Tube outline
        canvas.drawRoundRect(tubeRect, cornerRadius, cornerRadius, tubePaint)

        // 4. Shine overlay
        canvas.drawRoundRect(tubeRect, cornerRadius, cornerRadius, shinePaint)

        // 5. Selection / hint ring
        when {
            isSelected   -> canvas.drawRoundRect(tubeRect, cornerRadius, cornerRadius, selectedPaint)
            isHintTarget -> canvas.drawRoundRect(tubeRect, cornerRadius, cornerRadius, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onTapped?.invoke()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ── Animation ──────────────────────────────────────────────────────────

    /**
     * Animates a pour from [fromSegments] to [toSegments].
     * [onEnd] is called when the animation completes (main thread).
     *
     * The animation shows the top-most liquid rising then settling at the
     * new fill level.
     */
    fun animatePour(
        fromSegments: List<LiquidColor>,
        toSegments:   List<LiquidColor>,
        durationMs:   Long = 400L,
        onEnd:        () -> Unit,
    ) {
        currentAnimator?.cancel()
        animatingFromSegments = fromSegments
        animatingToSegments   = toSegments

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration     = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                animationFraction = anim.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                animationFraction = 1f
                animatingFromSegments = emptyList()
                animatingToSegments   = emptyList()
                onEnd()
            }
            start()
        }
    }

    // ── Private drawing helpers ────────────────────────────────────────────

    private fun drawSegments(canvas: Canvas, segments: List<LiquidColor>) {
        segments.forEachIndexed { index, color ->
            if (color.isEmpty) return@forEachIndexed

            val bottom = tubeRect.bottom - index * segmentHeight
            val top    = bottom - segmentHeight

            // Clip to tube bounds
            val clipTop    = maxOf(top,    tubeRect.top)
            val clipBottom = minOf(bottom, tubeRect.bottom)
            if (clipTop >= clipBottom) return@forEachIndexed

            val rect = RectF(tubeRect.left + 2f, clipTop, tubeRect.right - 2f, clipBottom)
            liquidPaint.color = colorPalette[color.id]
            // Only round bottom corners for the bottom segment
            if (index == 0) {
                canvas.drawRoundRect(rect, cornerRadius - 2f, cornerRadius - 2f, liquidPaint)
            } else {
                canvas.drawRect(rect, liquidPaint)
            }

            // Draw color-blind accessible patterns/symbols on top of the liquid segments
            drawPatternForColor(canvas, color.id, rect)
        }
    }

    private fun drawPatternForColor(canvas: Canvas, colorId: Int, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.15f

        when (colorId) {
            1 -> { // Dot
                canvas.drawCircle(cx, cy, r * 0.8f, patternFillPaint)
            }
            2 -> { // Two horizontal dots
                canvas.drawCircle(cx - r, cy, r * 0.6f, patternFillPaint)
                canvas.drawCircle(cx + r, cy, r * 0.6f, patternFillPaint)
            }
            3 -> { // Slash
                canvas.drawLine(cx - r, cy + r, cx + r, cy - r, patternPaint)
            }
            4 -> { // Cross
                canvas.drawLine(cx - r, cy, cx + r, cy, patternPaint)
                canvas.drawLine(cx, cy - r, cx, cy + r, patternPaint)
            }
            5 -> { // Three dots
                canvas.drawCircle(cx, cy - r * 0.7f, r * 0.5f, patternFillPaint)
                canvas.drawCircle(cx - r, cy + r * 0.5f, r * 0.5f, patternFillPaint)
                canvas.drawCircle(cx + r, cy + r * 0.5f, r * 0.5f, patternFillPaint)
            }
            6 -> { // Horizontal line
                canvas.drawLine(cx - r * 1.2f, cy, cx + r * 1.2f, cy, patternPaint)
            }
            7 -> { // Vertical line
                canvas.drawLine(cx, cy - r * 1.2f, cx, cy + r * 1.2f, patternPaint)
            }
            8 -> { // Square outline
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, patternPaint)
            }
            9 -> { // X
                canvas.drawLine(cx - r, cy - r, cx + r, cy + r, patternPaint)
                canvas.drawLine(cx - r, cy + r, cx + r, cy - r, patternPaint)
            }
            10 -> { // Diamond
                val path = Path().apply {
                    moveTo(cx, cy - r * 1.2f)
                    lineTo(cx + r * 1.2f, cy)
                    lineTo(cx, cy + r * 1.2f)
                    lineTo(cx - r * 1.2f, cy)
                    close()
                }
                canvas.drawPath(path, patternPaint)
            }
            11 -> { // Circle outline
                canvas.drawCircle(cx, cy, r, patternPaint)
            }
            12 -> { // Wavy style line
                canvas.drawLine(cx - r, cy - r * 0.5f, cx - r * 0.5f, cy + r * 0.5f, patternPaint)
                canvas.drawLine(cx - r * 0.5f, cy + r * 0.5f, cx + r * 0.5f, cy - r * 0.5f, patternPaint)
                canvas.drawLine(cx + r * 0.5f, cy - r * 0.5f, cx + r, cy + r * 0.5f, patternPaint)
            }
        }
    }

    /**
     * Returns the segment list to render, linearly interpolated between
     * [animatingFromSegments] and [animatingToSegments] during animation.
     *
     * Simple approach: lerp the fill level of each color block.
     */
    private fun interpolatedSegments(): List<LiquidColor> {
        if (animatingFromSegments.isEmpty()) return tube.segments
        val t = animationFraction

        // Determine the resulting segment count for each step
        val fromSize = animatingFromSegments.size
        val toSize   = animatingToSegments.size
        val currentSize = (fromSize + (toSize - fromSize) * t).roundToInt()
            .coerceIn(0, tubeCapacity)

        // Build the interpolated list: use 'to' colors but with lerped count
        return if (currentSize <= animatingToSegments.size) {
            animatingToSegments.takeLast(currentSize)
        } else {
            animatingFromSegments.takeLast(currentSize)
        }
    }
}

// ── Kotlin extension on ValueAnimator ─────────────────────────────────────────

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationEnd(animation: android.animation.Animator)   = action()
        override fun onAnimationStart(animation: android.animation.Animator) = Unit
        override fun onAnimationCancel(animation: android.animation.Animator) = Unit
        override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
    })
}
