package com.example.tangible_recognition_v1

import android.graphics.PointF
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PatternCheck {
    private lateinit var touchProcessor: TouchProcessor
    private lateinit var patternStorage: PatternStorage

    fun setDependencies(touchProcessor: TouchProcessor, patternStorage: PatternStorage) {
        this.touchProcessor = touchProcessor
        this.patternStorage = patternStorage
    }

    // todo adjust the values
    private val positionTolerance: Float =
        touchProcessor.mmToPixels(3f) // Tolerance for position matching
    private val maxTimeBetweenTouchesMs: Long = 2000 // Maximum time between touches
    private val maxPatternTimeMs: Long = 3000 // Maximum time for a pattern
    private val maxTimeGap: Long = 1000 // Maximum time gap between touches

    ////////////////// Public methods ///////////////////////////////////////////////////////

    fun normalizeSequence(): List<PointF> {
        if (touchProcessor.touchSequence.isEmpty()) return emptyList()

        // Apply position normalization
        val firstPoint = touchProcessor.touchSequence.first()
        val positionNormalized =
            touchProcessor.touchSequence.map { PointF(it.x - firstPoint.x, it.y - firstPoint.y) }

        // Apply rotation normalization
        return normalizeRotation(positionNormalized)
    }

    fun checkPattern() {
        val normalized = normalizeSequence()

        Log.d("PatternRecognizer", "Detected Pattern: $normalized")
        touchProcessor.resetSequence()
        touchProcessor.isChecking = false

        for (pattern in patternStorage.knownPatterns) {
            if (arePatternsEqual(pattern, normalized)) {
                Log.d(
                    "PatternRecognizer",
                    "Recognized Pattern ID: ${pattern.id} - Points: ${pattern.points}}"
                )
                return
            }
        }

        Log.d("PatternRecognizer", "Pattern NOT recognized!")
    }

    // todo also time checks when saving the patterns?
    fun isPatternTimingInvalid(
        savedPattern: PatternData?,
        recordNewPattern: Boolean = false
    ): Boolean {
        if (touchProcessor.sequenceTimestamps.size < 2) return false

        // Check for long pauses between touches
        for (i in 1 until touchProcessor.sequenceTimestamps.size) {
            val timeGap =
                touchProcessor.sequenceTimestamps[i] - touchProcessor.sequenceTimestamps[i - 1]
            if (timeGap > maxTimeBetweenTouchesMs) {
                Log.d(
                    "SecurityCheck",
                    "Touch delay too long at index $i: Delay of $timeGap ms exceeds allowed $maxTimeBetweenTouchesMs ms."
                )
                return true
            }
        }

        // Check total duration of pattern
        val totalDuration =
            touchProcessor.sequenceTimestamps.last() - touchProcessor.sequenceTimestamps.first()
        if (totalDuration > maxPatternTimeMs) {
            Log.d("SecurityCheck", "Pattern rejected: Took too long ($totalDuration ms).")
            return true
        }

        // add a check for the recordNewPattern case
        if (!recordNewPattern) {
            if (savedPattern?.timestamps?.size != touchProcessor.sequenceTimestamps.size) return false

            for (i in 1 until savedPattern.timestamps.size) {
                val expectedTimeGap = savedPattern.timestamps[i] - savedPattern.timestamps[i - 1]
                val observedTimeGap =
                    touchProcessor.sequenceTimestamps[i] - touchProcessor.sequenceTimestamps[i - 1]

                if (abs(observedTimeGap - expectedTimeGap) > maxTimeGap) {
                    Log.d(
                        "SecurityCheck",
                        "Timing mismatch at index $i: Expected $expectedTimeGap ms, but got $observedTimeGap ms."
                    )
                    return true
                }
            }
        }
        return false
    }

    ////////////////// Private methods //////////////////////////////////////////////////////

    private fun normalizeRotation(points: List<PointF>): List<PointF> {
        if (points.size < 2) return points // Not enough points to determine rotation

        val centroidX = points.sumOf { it.x.toDouble() } / points.size
        val centroidY = points.sumOf { it.y.toDouble() } / points.size
        val centroid = PointF(centroidX.toFloat(), centroidY.toFloat())

        val first = points.first()
        val angle = atan2((first.y - centroid.y).toDouble(), (first.x - centroid.x).toDouble())

        return points.map { rotatePoint(it, -angle, centroid) }
    }

    // Function to rotate a point around a pivot
    private fun rotatePoint(point: PointF, angle: Double, pivot: PointF): PointF {
        val cosTheta = cos(angle)
        val sinTheta = sin(angle)

        val translatedX = point.x - pivot.x
        val translatedY = point.y - pivot.y

        val rotatedX = translatedX * cosTheta - translatedY * sinTheta
        val rotatedY = translatedX * sinTheta + translatedY * cosTheta

        return PointF(rotatedX.toFloat(), rotatedY.toFloat())
    }

    private fun arePatternsEqual(
        savedPattern: PatternData,
        patternToBeChecked: List<PointF>
    ): Boolean {
        if (!isSizeEqual(savedPattern, patternToBeChecked)) return false

        if (isPatternTimingInvalid(savedPattern)) return false

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

    private fun isSizeEqual(
        savedPattern: PatternData,
        patternToBeChecked: List<PointF>
    ): Boolean {
        if (savedPattern.points.size != patternToBeChecked.size) {
            Log.d(
                "PatternRecognizer",
                "Pattern size mismatch: ${savedPattern.points.size} vs ${patternToBeChecked.size}"
            )
            return false
        }
        return true
    }

    private fun isDistanceWithinTolerance(
        savedPattern: PatternData,
        patternToBeChecked: List<PointF>,
        index: Int
    ): Boolean {
        val p1 = savedPattern.points[index]
        val p2 = patternToBeChecked[index]

        val distance = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()

        if (distance > positionTolerance) {
            Log.d(
                "PatternRecognizer",
                "Pattern mismatch in Pattern ${savedPattern.id} at index $index: Expected ${p1.x},${p1.y} but got ${p2.x},${p2.y}. Distance $distance exceeds tolerance $positionTolerance"
            )
            return false
        }

        if (index != savedPattern.points.indices.first) {
            Log.d(
                "PatternRecognizer",
                "Pattern match in Pattern ${savedPattern.id}: Distance $distance is within tolerance $positionTolerance"
            )
        }

        return true
    }

    private fun isPairwiseDistanceWithinTolerance(
        savedPattern: PatternData,
        patternToBeChecked: List<PointF>,
        index: Int
    ): Boolean {
        val p1 = savedPattern.points[index]
        val p2 = patternToBeChecked[index]
        val prevP1 = savedPattern.points[index - 1]
        val prevP2 = patternToBeChecked[index - 1]

        val savedPairDistance =
            hypot((p1.x - prevP1.x).toDouble(), (p1.y - prevP1.y).toDouble()).toFloat()
        val checkedPairDistance =
            hypot((p2.x - prevP2.x).toDouble(), (p2.y - prevP2.y).toDouble()).toFloat()

        val distanceDiff = abs(savedPairDistance - checkedPairDistance)

        if (distanceDiff > positionTolerance) {
            Log.d(
                "PatternRecognizer",
                "Pattern mismatch in Pattern ${savedPattern.id} at Index $index: Pairwise distance $checkedPairDistance differs from expected $savedPairDistance"
            )
            return false
        }
        return true
    }
}