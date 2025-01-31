package com.example.tangible_recognition_v1

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class TouchView(context: Context) : View(context) {

    ////////////////// Private properties ///////////////////////////////////////////////////

    private val paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        textSize = 50f
    }
    private val touchColors = mutableMapOf<Int, Int>()

    private val touchProcessor = TouchProcessor(context, maxTouchPoints = 5, minSpacing = 5f)

    ////////////////// Public methods ///////////////////////////////////////////////////////

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchProcessor.processTouch(event) // Process touch events

        // Assign a random color to each new touch point
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (!touchColors.containsKey(pointerId)) {
                touchColors[pointerId] = getRandomColor()
            }
        }

        invalidate() // Redraw the view
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        touchProcessor.getTouchPoints().forEach { (pointerId, point) ->
            paint.color = touchColors[pointerId] ?: Color.RED
            canvas.drawCircle(point.x, point.y, 100f, paint)
            paint.color = Color.BLACK
            canvas.drawText("ID: $pointerId", point.x - 50, point.y - 120, paint)
        }
    }

    ////////////////// Private methods //////////////////////////////////////////////////////

    private fun getRandomColor(): Int {
        val random = Random.Default
        return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }
}