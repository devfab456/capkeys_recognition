package com.example.tangible_recognition_v1

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

class TouchProcessor(
    context: Context,
    private val maxTouchPoints: Int, // Maximum number of touch points
    minSpacingMm: Float, // Minimum spacing between touch points in millimeters
    thresholdMm: Float, // Minimum movement in millimeters to update touch point
    restoreThresholdMm: Float, // Maximum movement in millimeters to restore lost touches
    private val logIntervalMs: Int, // Minimum time between logs per touch ID
    private val restoreGracePeriodMs: Int // Grace period for restoring touches
) {

    ////////////////// Private properties ///////////////////////////////////////////////////

    private val touchPoints = mutableMapOf<Int, PointF>() // Stores current touch points
    private val lostTouches =
        mutableMapOf<Int, Pair<PointF, Long>>() // Stores lost touches with timestamps
    private val lastLogTimes = mutableMapOf<Int, Long>() // Stores last log times per touch ID

    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val ppi: Int = displayMetrics.densityDpi // Pixels per inch

    private val minSpacing: Float = mmToPixels(minSpacingMm)
    private val threshold: Float = mmToPixels(thresholdMm)
    private val restoreThreshold: Float = mmToPixels(restoreThresholdMm)

    private var lastLoggedMessage: String? = null

    private val patternRecognizer = PatternRecognizer(this)
    var isRecording = false


    ////////////////// Public methods ///////////////////////////////////////////////////////

    /**
     * Processes touch events and returns the updated touch points.
     *
     * This method handles different types of touch events such as ACTION_DOWN, ACTION_POINTER_DOWN,
     * ACTION_MOVE, ACTION_UP, and ACTION_POINTER_UP. It updates the touch points based on the event
     * and logs the touch points if necessary.
     *
     * @param event The MotionEvent containing touch event data.
     */
    fun processTouch(event: MotionEvent) {
        val currentTime = SystemClock.uptimeMillis()

        // Process touch events
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val newPoint = PointF(x, y)

                    // Restore lost touch if within grace period and distance threshold
                    val lostTouch = lostTouches[pointerId]
                    if (lostTouch != null && currentTime - lostTouch.second <= restoreGracePeriodMs
                        && !isSignificantChange(lostTouch.first, newPoint, restoreThreshold)
                    ) {
                        touchPoints[pointerId] = lostTouch.first // Restore only if close
                        lostTouches.remove(pointerId) // Remove from lostTouches
                        continue // Skip further processing
                    }

                    // Enforce the maximum touch points constraint
                    if (touchPoints.size < maxTouchPoints || touchPoints.containsKey(pointerId)) {
                        val lastPoint = touchPoints[pointerId]

                        if (lastPoint == null || isSignificantChange(
                                lastPoint,
                                newPoint,
                                threshold
                            ) && isWellSpaced(newPoint)
                        ) {
                            if (isRecording) {
                                patternRecognizer.addTouchPoint(newPoint, currentTime)
                            }
                            touchPoints[pointerId] = newPoint
                            logTouchPoint(pointerId, newPoint)
                        }
                    }
                }
                logConcurrentTouchPoints()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                touchPoints[pointerId]?.let { lastTouch ->
                    lostTouches[pointerId] = Pair(lastTouch, currentTime)
                    touchPoints.remove(pointerId)
                    logTouchPoint(pointerId, lastTouch, removed = true)
                }
                logConcurrentTouchPoints()
            }
        }

        // Remove lost touches that exceed the grace period
        lostTouches.entries.removeIf { currentTime - it.value.second > restoreGracePeriodMs }
    }

    /** Returns the current touch points map */
    fun getTouchPoints(): Map<Int, PointF> = touchPoints


    ////////////////// Public pattern recognition methods /////////////////////////////////

    fun saveNewPattern() {
        // todo ok button
        isRecording = true
    }

    fun getCurrentPatterns(): MutableList<Pair<Int, List<PointF>>> {
        return patternRecognizer.getCurrentPatterns()
    }

    fun saveAllPatternsToFile(context: Context) {
        patternRecognizer.savePatternsToFile(context)
    }

    fun loadPatterns(context: Context) {
        patternRecognizer.loadPatternsFromFile(context)
    }

//    fun checkCurrentPattern(): Boolean {
//        return patternRecognizer.checkCurrentPattern()
//    }

    ////////////////// Private methods /////////////////////////////////////////////////

    /**
     * Logs the touch point, but only if at least the specified log interval has passed since the last log.
     *
     * @param pointerId The ID of the touch pointer.
     * @param point The coordinates of the touch point.
     * @param removed Indicates whether the touch point was removed. Defaults to false.
     */
    private fun logTouchPoint(
        pointerId: Int,
        point: PointF,
        removed: Boolean = false
    ) {
        if (!removed) {
            val currentTime = SystemClock.uptimeMillis()
            val lastLogTime = lastLogTimes[pointerId] ?: 0

            if (currentTime - lastLogTime >= logIntervalMs) {
                Log.d(
                    "TouchProcessor - ADDED", "New Touch! --- ID: $pointerId " +
                            "at X: ${point.x}, Y: ${point.y}"
                )
                lastLogTimes[pointerId] = currentTime // Update log time
            }
        } else {
            Log.d(
                "TouchProcessor - REMOVED",
                "Touch removed! --- ID: $pointerId at X: ${point.x}, Y: ${point.y}"
            )
        }
    }

    /** Logs the number of concurrent touch points */
    private fun logConcurrentTouchPoints() {
        val currentMessage = "Concurrent touch points: ${touchPoints.size}"
        if (currentMessage != lastLoggedMessage) {
            Log.d("TouchProcessor - CURRENT", currentMessage)
            lastLoggedMessage = currentMessage
        }
    }

    /**
     * Ensures movement is significant before updating a touch point
     *
     * @param lastPoint The last touch point
     * @param newPoint The new touch point
     * @param threshold The movement threshold in pixels
     * @return True if the movement is significant, false otherwise
     */
    private fun isSignificantChange(
        lastPoint: PointF,
        newPoint: PointF,
        threshold: Float
    ): Boolean {
        val dx = abs(newPoint.x - lastPoint.x)
        val dy = abs(newPoint.y - lastPoint.y)
        return dx > threshold || dy > threshold
    }

    /**
     * Checks if the new touch point is well spaced from existing touch points.
     *
     * @param newPoint The new touch point to be checked.
     * @return True if the new point is well spaced from existing points, false otherwise.
     */
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

    /** Converts millimeters to pixels */
    private fun mmToPixels(mm: Float): Float {
        val inches = mm / 25.4f
        return inches * ppi
    }
}