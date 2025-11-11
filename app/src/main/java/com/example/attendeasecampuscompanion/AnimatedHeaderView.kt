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

class AnimatedHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 60
    }

    private val particles = mutableListOf<Particle>()
    private val particleCount = 15

    private var viewWidth = 0f
    private var viewHeight = 0f

    data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speed: Float,
        var alpha: Int,
        var drift: Float
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
                Color.parseColor("#1976D2")
            ),
            floatArrayOf(0f, 1f),
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
                    radius = Random.nextFloat() * 3f + 1.5f,
                    speed = Random.nextFloat() * 0.8f + 0.3f,
                    alpha = Random.nextInt(30, 80),
                    drift = Random.nextFloat() * 0.5f - 0.25f
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
            particle.x += particle.drift

            if (particle.y + particle.radius < 0) {
                particle.y = viewHeight + particle.radius
                particle.x = Random.nextFloat() * viewWidth
            }

            if (particle.x < -particle.radius) {
                particle.x = viewWidth + particle.radius
            } else if (particle.x > viewWidth + particle.radius) {
                particle.x = -particle.radius
            }
        }

        invalidate()
    }
}