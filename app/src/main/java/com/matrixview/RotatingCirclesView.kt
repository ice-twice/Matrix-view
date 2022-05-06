package com.matrixview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.withStyledAttributes
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RotatingCirclesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {
    private var bigCircleColor = 0
    private var smallCircleColor = 0
    private var activeSmallCircleColor = 0
    private var centerX = 0f
    private var centerY = 0f
    private var bigCircleRadius = 0f
    private var smallCircleRadius = 0f
    private var outerCircleRadiusOffset = 0f
    private var innerCircleRadiusOffset = 0f
    private var activePosition = -1
    private val bigCirclePaint: Paint
    private val smallCirclePaint: Paint
    private var smallCircleAnimator: ValueAnimator? = null
    private var smallCircleAngleOffset = 0f

    init {
        context.withStyledAttributes(attrs, R.styleable.RotatingCircleView) {
            bigCircleColor = getColor(R.styleable.RotatingCircleView_bigCircleColor, 0)
            smallCircleColor = getColor(R.styleable.RotatingCircleView_smallCircleColor, 0)
            activeSmallCircleColor =
                getColor(R.styleable.RotatingCircleView_activeSmallCircleColor, 0)
        }
        bigCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bigCircleColor
        }
        smallCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = smallCircleColor
        }
        isClickable = true
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.drawCircle(centerX, centerY, bigCircleRadius, bigCirclePaint)

        for (position in 0 until SMALL_CIRCLE_COUNT) {
            smallCirclePaint.color = if (activePosition == position) {
                activeSmallCircleColor
            } else {
                smallCircleColor
            }

            val angle = START_ANGLE + position * ANGLE_BETWEEN_SMALL_CIRCLES
            val innerCircleAngle = angle + smallCircleAngleOffset
            val outerCircleAngle = angle - smallCircleAngleOffset
            drawCircle(canvas, outerCircleRadiusOffset, outerCircleAngle)
            drawCircle(canvas, innerCircleRadiusOffset, innerCircleAngle)
        }
    }

    private fun drawCircle(
        canvas: Canvas?,
        radiusOffset: Float,
        angle: Double
    ) {
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val x = (radiusOffset * cosAngle).toFloat() + centerX
        val y = (radiusOffset * sinAngle).toFloat() + centerY
        canvas?.drawCircle(x, y, smallCircleRadius, smallCirclePaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val halfOfSmallestSize = min(w, h).toFloat() / 2
        bigCircleRadius = halfOfSmallestSize * 0.8f
        smallCircleRadius = (halfOfSmallestSize - bigCircleRadius) / 2
        outerCircleRadiusOffset = bigCircleRadius + smallCircleRadius
        innerCircleRadiusOffset = bigCircleRadius - smallCircleRadius
        setupSmallCirclesAnimation()
    }

    private fun setupSmallCirclesAnimation() {
        smallCircleAnimator?.cancel()
        smallCircleAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 6000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                smallCircleAngleOffset = animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun performClick(): Boolean {
        if (super.performClick()) return true
        activePosition++
        if (activePosition > SMALL_CIRCLE_COUNT - 1) {
            activePosition = 0
        }
        invalidate()
        return true
    }

    private companion object {
        private const val SMALL_CIRCLE_COUNT = 12
        private const val START_ANGLE = Math.PI
        private const val ANGLE_BETWEEN_SMALL_CIRCLES = Math.PI * 2 / SMALL_CIRCLE_COUNT
    }
}