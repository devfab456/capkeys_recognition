package com.example.tangible_recognition_v1

import android.graphics.PointF
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PatternCheck(
    private val touchProcessor: TouchProcessor
) : PatternValidator {

    // todo adjust the values
    private val positionTolerance: Float =
        touchProcessor.mmToPixels(3f) // Tolerance for position matching
    private val maxTimeBetweenTouchesMs: Long = 3000 // Maximum time between touches
    private val maxPatternTimeMs: Long = 12000 // Maximum time for a pattern
    private val maxTimeGap: Long = 400 // Maximum time gap between touches

    private var failedAttempts = 0
    private val maxFailedAttempts = 3  // Max failed attempts before lockout
    private var lockoutTimeMs: Long = 10000  // Lock for 10 seconds after max attempts
    private var lockoutEndTime: Long = 0  // Time when lockout ends

    ////////////////// Public methods ///////////////////////////////////////////////////////

    /**
     * Normalizes the sequence of touch points by translating the first point to the origin and
     * rotating the sequence to align the first point with the x-axis.
     *
     * @return The normalized sequence of touch points.
     */
    override fun normalizeSequence(): List<PointF> {
        if (touchProcessor.touchSequence.isEmpty()) return emptyList()

        // Apply position normalization
        val firstPoint = touchProcessor.touchSequence.first()
        val positionNormalized =
            touchProcessor.touchSequence.map { PointF(it.x - firstPoint.x, it.y - firstPoint.y) }

        // Apply rotation normalization
        return normalizeRotation(positionNormalized)
    }

    /**
     * Checks if the timing of the touch events
     *
     * @param timestamps The list of timestamps for the touch events.
     * @return True if the timing is invalid, false otherwise.
     */
    override fun isPatternTimingInvalidSelf(
        timestamps: List<Long>
    ): Boolean {

        if (timestamps.size < 2) return false

        // Check for long pauses between touches
        for (i in 1 until timestamps.size) {
            val timeGap =
                timestamps[i] - timestamps[i - 1]
            if (timeGap > maxTimeBetweenTouchesMs) {
                Log.d(
                    "PatternRecognizer - SecurityCheck",
                    "Touch delay too long at index $i: Delay of $timeGap ms exceeds allowed $maxTimeBetweenTouchesMs ms."
                )
                return true
            }
        }

        // Check total duration of pattern
        val totalDuration =
            timestamps.last() - timestamps.first()
        Log.d("PatternRecognizer - SecurityCheck", "Total duration of pattern: $totalDuration ms")
        if (totalDuration > maxPatternTimeMs) {
            Log.d(
                "PatternRecognizer - SecurityCheck",
                "Pattern rejected: Took too long ($totalDuration ms)."
            )
            return true
        }

        return false
    }

    /**
     * Checks if the timing of the saved pattern is invalid compared to the current touch sequence.
     *
     * @param savedPattern The saved pattern to be checked against.
     * @param timestamps The list of timestamps for the touch events.
     * @return True if the timing is invalid, false otherwise.
     */
    override fun isPatternTimingInvalidCompare(
        savedPattern: PatternData,
        timestamps: List<Long>
    ): Boolean {

        if (savedPattern.timestamps.size != touchProcessor.sequenceTimestamps.size) return false

        for (i in 1 until savedPattern.timestamps.size) {
            val expectedTimeGap = savedPattern.timestamps[i] - savedPattern.timestamps[i - 1]
            val observedTimeGap =
                touchProcessor.sequenceTimestamps[i] - touchProcessor.sequenceTimestamps[i - 1]

            if (abs(observedTimeGap - expectedTimeGap) > maxTimeGap) {
                Log.d(
                    "PatternRecognizer - SecurityCheck",
                    "Timing mismatch in Pattern ${savedPattern.id} at index $i: Expected $expectedTimeGap ms, but got $observedTimeGap ms."
                )
                return true
            }
        }

        return false
    }

    /**
     * Checks if the detected pattern matches any stored patterns while enforcing brute-force protection.
     */
    fun checkPattern(knownPatterns: List<PatternData>): Boolean {
        // Check if lockout is active
        if (isLocked()) {
            val remainingLockTime = getRemainingLockTime()
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Pattern check blocked! Locked for another ${remainingLockTime}ms."
            )
            return false
        }

        val normalized = normalizeSequence()
        val timestamps = touchProcessor.sequenceTimestamps.toList()
        val detectedPattern = PatternData(-1, normalized, timestamps)

        Log.d("PatternRecognizer - SecurityCheck", "Pattern To Check: $detectedPattern")

        if (isPatternTimingInvalidSelf(timestamps)) {
            handleFailedAttempt()
            touchProcessor.resetSequence()
            return false
        }

        for (pattern in knownPatterns) {
            if (arePatternsEqual(pattern, detectedPattern)) {
                Log.d(
                    "PatternRecognizer - SecurityCheck",
                    "Recognized Pattern ID: ${pattern.id}"
                )
                resetFailedAttempts()
                touchProcessor.resetSequence()
                return true  // Pattern recognized
            }
        }

        handleFailedAttempt()
        touchProcessor.resetSequence()

        Log.w("PatternRecognizer - SecurityCheck", "Pattern NOT recognized!")
        return false  // Pattern not recognized
    }


    ////////////////// Brute-Force Protection ///////////////////////////////////////////////////

    /**
     * Handles a failed authentication attempt by incrementing the counter
     * and applying a lockout if necessary.
     */
    private fun handleFailedAttempt() {
        failedAttempts++
        Log.w(
            "PatternRecognizer - SecurityCheck",
            "Failed attempt $failedAttempts/$maxFailedAttempts"
        )

        if (failedAttempts >= maxFailedAttempts) {
            lockoutEndTime = System.currentTimeMillis() + lockoutTimeMs
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Too many failed attempts! Locking for ${lockoutTimeMs / 1000} seconds."
            )
        }
    }

    /**
     * Resets the failed attempt counter after a successful authentication.
     */
    private fun resetFailedAttempts() {
        failedAttempts = 0
        lockoutEndTime = 0
        Log.d("PatternRecognizer - SecurityCheck", "Failed attempts reset after success.")
    }

    /**
     * Checks if the system is currently locked due to excessive failed attempts.
     */
    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockoutEndTime
    }

    /**
     * Returns the remaining lockout time in milliseconds.
     */
    fun getRemainingLockTime(): Long {
        return maxOf(0, lockoutEndTime - System.currentTimeMillis())
    }

    ////////////////// Private methods //////////////////////////////////////////////////////

    /**
     * Normalizes the position of a sequence of points by translating the first point to the centroid.
     *
     * @param points The sequence of points to be normalized.
     * @return The normalized sequence of points.
     */
    private fun normalizeRotation(points: List<PointF>): List<PointF> {
        if (points.size < 2) return points // Not enough points to determine rotation

        val centroidX = points.sumOf { it.x.toDouble() } / points.size
        val centroidY = points.sumOf { it.y.toDouble() } / points.size
        val centroid = PointF(centroidX.toFloat(), centroidY.toFloat())

        val first = points.first()
        val angle = atan2((first.y - centroid.y).toDouble(), (first.x - centroid.x).toDouble())

        return points.map { rotatePoint(it, -angle, centroid) }
    }

    /**
     * Rotates a point around a pivot by a given angle.
     *
     * @param point The point to be rotated.
     * @param angle The angle of rotation in radians.
     * @param pivot The pivot point for the rotation.
     * @return The rotated point.
     */
    private fun rotatePoint(point: PointF, angle: Double, pivot: PointF): PointF {
        val cosTheta = cos(angle)
        val sinTheta = sin(angle)

        val translatedX = point.x - pivot.x
        val translatedY = point.y - pivot.y

        val rotatedX = translatedX * cosTheta - translatedY * sinTheta
        val rotatedY = translatedX * sinTheta + translatedY * cosTheta

        return PointF(rotatedX.toFloat(), rotatedY.toFloat())
    }

    /**
     * Compares two patterns to determine if they are equal.
     *
     * @param savedPattern The saved pattern to be checked against.
     * @param patternToBeChecked The pattern to be checked.
     * @return True if the patterns are equal, false otherwise.
     */
    private fun arePatternsEqual(
        savedPattern: PatternData,
        patternToBeChecked: PatternData
    ): Boolean {
        if (!isSizeEqual(savedPattern, patternToBeChecked)) return false

        if (isPatternTimingInvalidCompare(savedPattern, patternToBeChecked.timestamps)) return false

        for (i in savedPattern.points.indices) {
            if (!isDistanceWithinTolerance(savedPattern, patternToBeChecked, i)) return false
            if (i > 0 && !isPairwiseDistanceWithinTolerance(
                    savedPattern,
                    patternToBeChecked,
                    i
                )
            ) return false
        }

        return true
    }

    ////////////////// Helper methods ////////////////////////////////////////////////////////

    /**
     * Checks if the size of the two patterns is equal.
     *
     * @param savedPattern The saved pattern to be checked against.
     * @param patternToBeChecked The pattern to be checked.
     * @return True if the sizes are equal, false otherwise.
     */
    private fun isSizeEqual(
        savedPattern: PatternData,
        patternToBeChecked: PatternData
    ): Boolean {
        if (savedPattern.points.size != patternToBeChecked.points.size) {
            Log.d(
                "PatternRecognizer - SecurityCheck",
                "Pattern size mismatch: ${savedPattern.points.size} vs ${patternToBeChecked.points.size}"
            )
            return false
        }
        return true
    }

    /**
     * Checks if the distance between two points is within a given tolerance.
     *
     * @param savedPattern The saved pattern to be checked against.
     * @param patternToBeChecked The pattern to be checked.
     * @param index The index of the points to be compared.
     * @return True if the distance is within the tolerance, false otherwise.
     */
    private fun isDistanceWithinTolerance(
        savedPattern: PatternData,
        patternToBeChecked: PatternData,
        index: Int
    ): Boolean {
        val p1 = savedPattern.points[index]
        val p2 = patternToBeChecked.points[index]

        val distance = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()

        if (distance > positionTolerance) {
            Log.d(
                "PatternRecognizer - SecurityCheck",
                "Pattern mismatch in Pattern ${savedPattern.id} at index $index: Expected ${p1.x},${p1.y} but got ${p2.x},${p2.y}. Distance $distance exceeds tolerance $positionTolerance"
            )
            return false
        }

        Log.d(
            "PatternRecognizer - SecurityCheck",
            "Pattern match in Pattern ${savedPattern.id} at index $index: Distance $distance is within tolerance $positionTolerance"
        )
        return true
    }

    /**
     * Checks if the pairwise distance between two points is within a given tolerance.
     * The first point is the saved point and the second point is the point to be checked.
     *
     * @param savedPattern The saved pattern to be checked against.
     * @param patternToBeChecked The pattern to be checked.
     * @param index The index of the points to be compared.
     * @return True if the pairwise distance is within the tolerance, false otherwise.
     */
    private fun isPairwiseDistanceWithinTolerance(
        savedPattern: PatternData,
        patternToBeChecked: PatternData,
        index: Int
    ): Boolean {
        val p1 = savedPattern.points[index]
        val p2 = patternToBeChecked.points[index]
        val prevP1 = savedPattern.points[index - 1]
        val prevP2 = patternToBeChecked.points[index - 1]

        val savedPairDistance =
            hypot((p1.x - prevP1.x).toDouble(), (p1.y - prevP1.y).toDouble()).toFloat()
        val checkedPairDistance =
            hypot((p2.x - prevP2.x).toDouble(), (p2.y - prevP2.y).toDouble()).toFloat()

        val distanceDiff = abs(savedPairDistance - checkedPairDistance)

        if (distanceDiff > positionTolerance) {
            Log.d(
                "PatternRecognizer - SecurityCheck",
                "Pattern mismatch in Pattern ${savedPattern.id} at Index $index: Pairwise distance $checkedPairDistance differs from expected $savedPairDistance"
            )
            return false
        }
        return true
    }
}