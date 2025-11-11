package com.example.attendeasecampuscompanion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class AnimatedBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 100
    }

    private val particles = mutableListOf<Particle>()
    private val particleCount = 30

    private var viewWidth = 0f
    private var viewHeight = 0f

    data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speed: Float,
        var alpha: Int
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()

        gradientPaint.shader = LinearGradient(
            0f, 0f,
            0f, viewHeight,
            intArrayOf(
                Color.parseColor("#42A5F5"),
                Color.parseColor("#1976D2"),
                Color.parseColor("#0D47A1")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        initParticles()
    }

    private fun initParticles() {
        particles.clear()
        repeat(particleCount) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * viewWidth,
                    y = Random.nextFloat() * viewHeight,
                    radius = Random.nextFloat() * 4f + 2f,
                    speed = Random.nextFloat() * 2f + 0.5f,
                    alpha = Random.nextInt(50, 150)
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, viewWidth, viewHeight, gradientPaint)

        particles.forEach { particle ->
            particlePaint.alpha = particle.alpha
            canvas.drawCircle(particle.x, particle.y, particle.radius, particlePaint)

            particle.y -= particle.speed

            if (particle.y + particle.radius < 0) {
                particle.y = viewHeight + particle.radius
                particle.x = Random.nextFloat() * viewWidth
            }
        }

        invalidate()
    }
}