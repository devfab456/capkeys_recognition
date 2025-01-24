package com.example.tangible_recognition_v1

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class TouchView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL_AND_STROKE
    }
    private val touchPoints = mutableMapOf<Int, PointF>()
    private val threshold = 30 // Define a threshold for significant movement (in pixels)
    private val maxTouchPoints = 5 // Maximum number of touch points allowed

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)

                    // Enforce the maximum touch points constraint
                    if (touchPoints.size < maxTouchPoints || touchPoints.containsKey(pointerId)) {
                        val x = event.getX(i)
                        val y = event.getY(i)

                        // Check for significant movement
                        val lastPoint = touchPoints[pointerId]
                        if (lastPoint == null || isSignificantChange(lastPoint, x, y)) {
                            touchPoints[pointerId] = PointF(x, y)

                            // Log the touch information for significant changes only
                            Log.d(
                                "TouchView",
                                "Significant movement - Pointer ID: $pointerId, X: $x, Y: $y"
                            )
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                touchPoints.remove(pointerId)
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        touchPoints.values.forEach { point ->
            canvas.drawCircle(point.x, point.y, 100f, paint)
        }
    }

    private fun isSignificantChange(lastPoint: PointF, newX: Float, newY: Float): Boolean {
        val dx = abs(newX - lastPoint.x)
        val dy = abs(newY - lastPoint.y)
        return dx > threshold || dy > threshold
    }
}