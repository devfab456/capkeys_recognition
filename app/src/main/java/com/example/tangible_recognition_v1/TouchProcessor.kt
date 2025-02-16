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
    private val context: Context
) {

    ////////////////// Initialization ////////////////////////////////////////////////////

    private lateinit var patternCheck: PatternCheck
    private lateinit var patternStorage: PatternStorage

    fun initialize(patternCheck: PatternCheck, patternStorage: PatternStorage) {
        this.patternCheck = patternCheck
        this.patternStorage = patternStorage
    }

    ////////////////// Private properties ///////////////////////////////////////////////////

    // Touch points and logging
    private val touchPoints = mutableMapOf<Int, PointF>() // Stores current touch points
    private val lostTouches =
        mutableMapOf<Int, Pair<PointF, Long>>() // Stores lost touches with timestamps
    private val lastLogTimes = mutableMapOf<Int, Long>() // Stores last log times per touch ID
    private var lastLoggedMessage: String? = null // Stores the last logged message

    // Display metrics
    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val ppi: Float = displayMetrics.xdpi // Pixels per inch

    // Constants for touch processing todo adjust the values
    private val maxTouchPoints: Int = 5 // Maximum number of touch points
    private val minSpacing: Float =
        mmToPixels(5.1f) // Minimum spacing between touch points in millimeters
    private val threshold: Float =
        mmToPixels(4.9f) // Minimum movement in millimeters to update touch point
    private val restoreThreshold: Float =
        mmToPixels(1f) // Maximum movement in millimeters to restore lost touches
    private val logIntervalMs: Int = 200 // Minimum time between logs per touch ID
    private val restoreGracePeriodMs: Int = 100 // Grace period for restoring lost touches

    // for pattern recognition
    val touchSequence = mutableListOf<PointF>()
    val sequenceTimestamps = mutableListOf<Long>()
    var isRecording = false
    var isChecking = false

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
        val currentTime = SystemClock.elapsedRealtime()

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

                    // Enforce the maximum touch points constraint todo remove the constraint?
                    if (touchPoints.size < maxTouchPoints || touchPoints.containsKey(pointerId)) {
                        val lastPoint = touchPoints[pointerId]

                        if (lastPoint == null || isSignificantChange(
                                lastPoint,
                                newPoint,
                                threshold
                            ) && isWellSpaced(newPoint)
                        ) {
                            if (isRecording || isChecking) {
                                addTouchPoint(context, newPoint, currentTime)
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

    fun saveNewPattern() {
        isRecording = true
    }

    fun checkPattern() {
        isChecking = true
    }

    fun resetSequence() {
        touchSequence.clear()
        sequenceTimestamps.clear()
    }

    fun mmToPixels(mm: Float): Float {
        val inches = mm / 25.4f
        return inches * ppi
    }

    ////////////////// Private methods /////////////////////////////////////////////////

    /**
     * Adds a touch point to the touch sequence and checks the pattern if the sequence is complete.
     *
     * @param context The application context.
     * @param point The touch point to be added.
     * @param timestamp The timestamp of the touch point.
     */
    private fun addTouchPoint(context: Context, point: PointF, timestamp: Long) {
        Log.d("PatternRecognizer", "Touch point added to sequence: $point")
        touchSequence.add(point)
        sequenceTimestamps.add(timestamp)
        Log.d(
            "PatternRecognizer",
            "Touch sequence: $touchSequence, Timestamps: $sequenceTimestamps"
        )

        // todo max size limit
        if (touchSequence.size >= maxTouchPoints) {
            Log.d("PatternRecognizer", "Touch sequence ending after 5 points.")
            if (isChecking) {
                val knownPatterns = patternStorage.getKnownPatterns()
                patternCheck.checkPattern(knownPatterns)
            } else if (isRecording) {
                patternStorage.saveNewPattern(context)
            }
        }
    }

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
}