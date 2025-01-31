package com.example.tangible_recognition_v1

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

class TouchProcessor(
    context: Context,
    private val maxTouchPoints: Int,
    private val minSpacing: Float
) {

    ////////////////// Private properties ///////////////////////////////////////////////////

    private val touchPoints = mutableMapOf<Int, PointF>() // Stores current touch points
    private val lastPositions =
        mutableMapOf<Int, MutableList<PointF>>() // Stores last positions for smoothing
    private val lostTouches = mutableMapOf<Int, PointF>() // Stores temporarily lost touches

    private val threshold = 5 // Minimum movement in pixels (adjust to match ~5mm)
    private val smoothingWindow = 3 // Number of positions to average

    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val ppi: Float = displayMetrics.xdpi // Pixels per inch

    private val handler = Handler(Looper.getMainLooper())
    private val touchTimeoutMs = 200L // Grace period in milliseconds

    ////////////////// Public methods ///////////////////////////////////////////////////////

    /** Processes touch events and returns the updated touch points */
    fun processTouch(event: MotionEvent) {
        // Process touch events
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val newPoint = PointF(x, y)
                    val size = event.getSize(i)
                    val sizeInMm = sizeToMm(size)

                    // Enforce the maximum touch points constraint
                    if (touchPoints.size < maxTouchPoints || touchPoints.containsKey(pointerId)) {
                        // Recover lost touch
                        val lastPoint = touchPoints[pointerId] ?: lostTouches.remove(pointerId)

                        if (lastPoint == null || isSignificantChange(lastPoint, newPoint)) {
                            val smoothedPoint = smoothPosition(pointerId, newPoint)

                            // Apply minimum spacing filter to separate touches
                            if (isWellSpaced(smoothedPoint)) {
                                touchPoints[pointerId] = smoothedPoint

                                Log.d(
                                    "TouchView",
                                    "Valid Touch - Pointer ID: $pointerId," +
                                            " X: ${smoothedPoint.x}, Y: ${smoothedPoint.y},"
                                            + "Size: $size, Size in mm: $sizeInMm"
                                )
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                val lastTouch = touchPoints[pointerId]

                if (lastTouch != null) {
                    // Save the touch temporarily instead of removing it immediately
                    lostTouches[pointerId] = lastTouch

                    // Schedule touch removal after grace period
                    handler.postDelayed({
                        if (lostTouches.containsKey(pointerId)) {
                            lostTouches.remove(pointerId) // Finally remove if it hasn't returned
                            touchPoints.remove(pointerId)
                            lastPositions.remove(pointerId) // Clear history
                        }
                    }, touchTimeoutMs)
                }
            }
        }
    }

    /** Returns the current touch points map */
    fun getTouchPoints(): Map<Int, PointF> = touchPoints

    ////////////////// Private methods /////////////////////////////////////////////////

    /** Ensures movement is significant before updating a touch point */
    private fun isSignificantChange(lastPoint: PointF, newPoint: PointF): Boolean {
        val dx = abs(newPoint.x - lastPoint.x)
        val dy = abs(newPoint.y - lastPoint.y)
        return dx > threshold || dy > threshold
    }

    /** Smooths movement by averaging last few positions */
    private fun smoothPosition(pointerId: Int, newPoint: PointF): PointF {
        val history = lastPositions.getOrPut(pointerId) { mutableListOf() }

        history.add(newPoint)
        if (history.size > smoothingWindow) {
            history.removeAt(0)
        }

        val avgX = history.sumOf { it.x.toDouble() } / history.size
        val avgY = history.sumOf { it.y.toDouble() } / history.size
        return PointF(avgX.toFloat(), avgY.toFloat())
    }

    /** Ensures touch points are spaced at least minSpacing pixels apart */
    private fun isWellSpaced(newPoint: PointF): Boolean {
        for (existingPoint in touchPoints.values) {
            val distance = hypot(
                (existingPoint.x - newPoint.x).toDouble(),
                (existingPoint.y - newPoint.y).toDouble()
            )
            if (distance < minSpacing) {
                return false // Reject if too close to another touch
            }
        }
        return true
    }

    /** Converts touch size to millimeters */
    private fun sizeToMm(size: Float): Float {
        val sizeInInches = size * displayMetrics.widthPixels / ppi
        return sizeInInches * 25.4f // Convert inches to millimeters
    }
}