package com.example.tangible_recognition_v1

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
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

    private var lastLoggedMessage: String? = null // Stores the last logged message

    // Display metrics
    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val ppi: Float = displayMetrics.xdpi // Pixels per inch

    // Constants for touch processing todo adjust the values
    private val maxConcurrentTouchPoints: Int =
        5 // Maximum number of concurrent touch points on iPhone
    private val minSpacing: Float =
        mmToPixels(3f) // Minimum spacing between touch points in millimeters
    private val threshold: Float =
        mmToPixels(3f) // Minimum movement in millimeters to update touch point
    private val restoreThreshold: Float =
        mmToPixels(1f) // Maximum movement in millimeters to restore lost touches
    private val restoreGracePeriodMs: Int = 100 // Grace period for restoring lost touches
    private val outlierThreshold = mmToPixels(20f) // Example: 10mm tolerance

    // for pattern recognition
    private var isRecording = false
    private var isChecking = false

    private var currentGroup = mutableListOf<Pair<PointF, Long>>() // Current group being built
    val patternGroups = mutableListOf<TouchGroup>() // All groups in the current pattern
    private val groupTimeThresholdMs: Long = 200 // Time threshold for grouping touches

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
                        Log.d("TouchProcessor", "Restoring lost touch: $newPoint")
                        touchPoints[pointerId] = lostTouch.first // Restore only if close
                        lostTouches.remove(pointerId) // Remove from lostTouches
                        continue // Skip further processing
                    }

                    // Enforce the maximum concurrent touch points constraint when not yet tracked and update touch points
                    if (touchPoints.size < maxConcurrentTouchPoints || touchPoints.containsKey(
                            pointerId
                        )
                    ) {
                        val lastPoint = touchPoints[pointerId]

                        if (lastPoint == null || isSignificantChange(
                                lastPoint,
                                newPoint,
                                threshold
                            ) && isWellSpaced(newPoint)
                        ) {
                            if (isRecording || isChecking) {
                                addTouchPoint(newPoint, currentTime)
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
        resetGroup()
        isRecording = true
        Log.d("PatternRecognizer - Storage", "Recording new pattern...")
    }

    fun stopSavingPattern() {
        if (isRecording) {
            finalizeCurrentGroup()
            processPattern()
            isRecording = false
            Log.d("PatternRecognizer - Storage", "Pattern recording stopped.")
        }
    }

    fun checkPattern() {
        resetGroup()
        isChecking = true
        Log.d("PatternRecognizer - Storage", "Checking pattern...")
    }

    fun stopCheckingPattern() {
        if (isChecking) {
            finalizeCurrentGroup()
            processPattern()
            isChecking = false
            Log.d("PatternRecognizer - Storage", "Pattern checking stopped.")
        }
    }

    fun resetGroup() {
        patternGroups.clear()
        currentGroup.clear()
        isRecording = false
        isChecking = false
    }

    fun mmToPixels(mm: Float): Float {
        val inches = mm / 25.4f
        return inches * ppi
    }

    ////////////////// Private methods /////////////////////////////////////////////////

    private fun addTouchPoint(point: PointF, timestamp: Long) {
        Log.d("PatternRecognizer", "Touch point added to sequence: $point")

        if (currentGroup.isEmpty()) {
            // Start a new group
            currentGroup.add(point to timestamp)
        } else {
            val firstTouchInGroup = currentGroup.first().second

            if (timestamp - firstTouchInGroup <= groupTimeThresholdMs) {
                // Add to current group if within time threshold
                currentGroup.add(point to timestamp)
            } else {
                // Finalize current group and start a new one
                finalizeCurrentGroup()
                currentGroup.add(point to timestamp)
            }
        }
    }

    private fun finalizeCurrentGroup() {
        if (currentGroup.isNotEmpty()) {
            val groupTimestamp =
                currentGroup.first().second // Use first touch time as group timestamp
            val groupPoints = currentGroup.map { it.first }

            // Apply outlier filtering
            val filteredPoints = filterOutliers(groupPoints)

            // Only add the group if it still has points
            if (filteredPoints.isNotEmpty()) {
                patternGroups.add(TouchGroup(filteredPoints, groupTimestamp))
                Log.d("TouchProcessor", "Group finalized with points: $filteredPoints")
            } else {
                Log.w("TouchProcessor", "Group discarded due to all points being outliers.")
            }

            currentGroup.clear()
        }
    }

    private fun processPattern() {
        if (isChecking) {
            val knownPatterns = patternStorage.getKnownPatterns()
            patternCheck.checkPattern(knownPatterns)
            resetGroup()
        } else if (isRecording) {
            patternStorage.saveNewPattern(context)
            resetGroup()
        }
    }

    /**
     * Filters out touch points that are too far from the centroid.
     *
     * @param points The list of touch points.
     * @return The list of points within the threshold distance.
     */
    private fun filterOutliers(points: List<PointF>): List<PointF> {
        if (points.size <= 2) return points // No need to filter with very few points

        // Calculate centroid
        val centroidX = points.sumOf { it.x.toDouble() } / points.size
        val centroidY = points.sumOf { it.y.toDouble() } / points.size
        val centroid = PointF(centroidX.toFloat(), centroidY.toFloat())

        // Filter points within a threshold distance
        return points.filter { point ->
            val distance =
                hypot((point.x - centroid.x).toDouble(), (point.y - centroid.y).toDouble())
            distance <= outlierThreshold
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
            Log.d(
                "TouchProcessor - ADDED", "New Touch! --- ID: $pointerId " +
                        "at X: ${point.x}, Y: ${point.y}"
            )
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
        val distance = hypot(
            (newPoint.x - lastPoint.x).toDouble(),
            (newPoint.y - lastPoint.y).toDouble()
        )
        return distance > threshold
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
                Log.w("TouchProcessor", "Touch point too close to another touch!")
                return false // Reject if too close to another touch
            }
        }
        return true
    }
}