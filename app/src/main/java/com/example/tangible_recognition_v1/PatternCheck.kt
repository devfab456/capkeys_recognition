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

    ////////////////// Private constants //////////////////////////////////////////////////////////////

    // todo adjust the values
    private val positionTolerance: Float = touchProcessor.mmToPixels(4f)
    private val maxTimeBetweenGroupsMs: Long = 6000 // Maximum time between groups
    private val maxPatternTimeMs: Long = 20000 // Maximum time for entire pattern
    private val maxTimeGap: Long =
        1000 // Maximum allowed time difference between corresponding groups

    ////////////////// Override methods ///////////////////////////////////////////////////////

    override fun normalizeCoordinates(): List<TouchGroup> {
        if (touchProcessor.patternGroups.isEmpty()) return emptyList()

        // Calculate centroid of ALL points across ALL groups
        val allPoints = touchProcessor.patternGroups.flatMap { it.points }
        val centroidX = allPoints.sumOf { it.x.toDouble() } / allPoints.size
        val centroidY = allPoints.sumOf { it.y.toDouble() } / allPoints.size
        val centroid = PointF(centroidX.toFloat(), centroidY.toFloat())

        // Normalize position of all points in all groups
        val positionNormalized = touchProcessor.patternGroups.map { group ->
            TouchGroup(
                points = group.points.map { point ->
                    PointF(point.x - centroid.x, point.y - centroid.y)
                },
                timestamp = group.timestamp
            )
        }

        // Apply PCA-based rotation normalization
        return normalizeRotation(positionNormalized, centroid)
    }

    override fun isPatternTimingInvalidSelf(groups: List<TouchGroup>): Boolean {
        if (groups.size < 2) return false

        // Check for long pauses between groups
        for (i in 1 until groups.size) {
            val timeGap = groups[i].timestamp - groups[i - 1].timestamp
            if (timeGap > maxTimeBetweenGroupsMs) {
                Log.w(
                    "PatternRecognizer - SecurityCheck",
                    "Group delay too long at index $i: Delay of $timeGap ms exceeds allowed $maxTimeBetweenGroupsMs ms."
                )
                return true
            }
        }

        // Check total duration of pattern
        val totalDuration = groups.last().timestamp - groups.first().timestamp
        if (totalDuration > maxPatternTimeMs) {
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Pattern rejected: Took too long ($totalDuration ms)."
            )
            return true
        }

        return false
    }

    override fun isPatternTimingInvalidCompare(
        savedPattern: PatternData,
        detectedTimestamps: List<Long>,
        savedTimestamps: List<Long>
    ): Boolean {
        if (detectedTimestamps.size != savedTimestamps.size) return true

        for (i in 1 until savedTimestamps.size) {
            val expectedTimeGap = savedTimestamps[i] - savedTimestamps[i - 1]
            val observedTimeGap = detectedTimestamps[i] - detectedTimestamps[i - 1]

            if (abs(observedTimeGap - expectedTimeGap) > maxTimeGap) {
                Log.w(
                    "PatternRecognizer - SecurityCheck",
                    "Timing mismatch in Pattern ${savedPattern.id} at index $i: " +
                            "Expected $expectedTimeGap ms, but got $observedTimeGap ms."
                )
                return true
            }
        }

        return false
    }

    ////////////////// Public methods ////////////////////////////////////////////////////////

    fun checkPattern(knownPatterns: List<PatternData>): Boolean {
        val normalized = normalizeCoordinates()

        // Check self timing (gaps between groups in current pattern)
        if (isPatternTimingInvalidSelf(normalized)) {
            return false
        }

        val detectedPattern = PatternData(-1, normalized)

        for (pattern in knownPatterns) {
            // Extract timestamps from both patterns
            val detectedTimestamps = detectedPattern.groups.map { it.timestamp }
            val savedTimestamps = pattern.groups.map { it.timestamp }

            // Check timing comparison between saved and current pattern
            if (isPatternTimingInvalidCompare(pattern, detectedTimestamps, savedTimestamps)) {
                continue // Skip this pattern if timing doesn't match
            }

            // Check pattern points and structure
            if (arePatternsEqual(pattern, detectedPattern)) {
                Log.d(
                    "PatternRecognizer - SecurityCheck",
                    "Recognized Pattern ID: ${pattern.id}"
                )
                return true
            }
        }

        Log.w("PatternRecognizer - SecurityCheck", "Pattern NOT recognized!")
        return false
    }

    ////////////////// Private methods //////////////////////////////////////////////////////

    private fun arePatternsEqual(
        savedPattern: PatternData,
        patternToBeChecked: PatternData
    ): Boolean {
        if (savedPattern.groups.size != patternToBeChecked.groups.size) {
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Group count mismatch: ${savedPattern.groups.size} vs ${patternToBeChecked.groups.size}"
            )
            return false
        }

        // Compare groups
        for (i in savedPattern.groups.indices) {
            val savedGroup = savedPattern.groups[i]
            val checkedGroup = patternToBeChecked.groups[i]

            // Check group size equality
            if (!isSizeEqual(savedGroup, checkedGroup)) {
                return false
            }

            // Compare timing between groups
            if (i > 0) {
                val savedTimeGap = savedGroup.timestamp - savedPattern.groups[i - 1].timestamp
                val checkedTimeGap =
                    checkedGroup.timestamp - patternToBeChecked.groups[i - 1].timestamp
                if (abs(savedTimeGap - checkedTimeGap) > maxTimeGap) {
                    Log.w(
                        "PatternRecognizer - SecurityCheck",
                        "Timing mismatch between groups for group ${savedPattern.id} at index $i. " +
                                "Expected $savedTimeGap ms, but got $checkedTimeGap ms."
                    )
                    return false
                }
            }

            // Compare points within group
            for (j in savedGroup.points.indices) {
                val savedPointsSorted = savedGroup.points.sortedWith(compareBy({ it.x }, { it.y }))
                val checkedPointsSorted =
                    checkedGroup.points.sortedWith(compareBy({ it.x }, { it.y }))

                // Check individual point distances
                if (!isDistanceWithinTolerance(savedPointsSorted[j], checkedPointsSorted[j], j)) {
                    return false
                }

                // Check pairwise distances for all points except the first one
                if (j > 0 && !isPairwiseDistanceWithinTolerance(
                        savedPointsSorted,
                        checkedPointsSorted,
                        j
                    )
                ) {
                    return false
                }
            }
        }

        return true
    }

    ////////////////// Helper methods ////////////////////////////////////////////////////////

    /**
     * Checks if the size of the two groups is equal.
     *
     * @param savedGroup The saved group to be checked against.
     * @param checkedGroup The group to be checked.
     * @return True if the sizes are equal, false otherwise.
     */
    private fun isSizeEqual(
        savedGroup: TouchGroup,
        checkedGroup: TouchGroup
    ): Boolean {
        if (savedGroup.points.size != checkedGroup.points.size) {
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Group size mismatch: ${savedGroup.points.size} vs ${checkedGroup.points.size}"
            )
            return false
        }
        return true
    }

    /**
     * Checks if the distance between two points is within a given tolerance.
     *
     * @param savedPoint The saved point to be checked against.
     * @param checkedPoint The point to be checked.
     * @param index The index of the points being compared (for logging).
     * @return True if the distance is within the tolerance, false otherwise.
     */
    private fun isDistanceWithinTolerance(
        savedPoint: PointF,
        checkedPoint: PointF,
        index: Int
    ): Boolean {
        val distance = hypot(
            (savedPoint.x - checkedPoint.x).toDouble(),
            (savedPoint.y - checkedPoint.y).toDouble()
        ).toFloat()

        if (distance > positionTolerance) {
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Point mismatch at index $index: Expected ${savedPoint.x},${savedPoint.y} but got ${checkedPoint.x},${checkedPoint.y}. " +
                        "Distance $distance exceeds tolerance $positionTolerance"
            )
            return false
        }

        Log.d(
            "PatternRecognizer - SecurityCheck",
            "Point match at index $index: Distance $distance is within tolerance $positionTolerance"
        )
        return true
    }

    /**
     * Checks if the pairwise distance between two points is within a given tolerance.
     *
     * @param savedPoints The list of saved points to be checked against.
     * @param checkedPoints The list of points to be checked.
     * @param index The current index being compared.
     * @return True if the pairwise distance is within the tolerance, false otherwise.
     */
    private fun isPairwiseDistanceWithinTolerance(
        savedPoints: List<PointF>,
        checkedPoints: List<PointF>,
        index: Int
    ): Boolean {
        val p1 = savedPoints[index]
        val p2 = checkedPoints[index]
        val prevP1 = savedPoints[index - 1]
        val prevP2 = checkedPoints[index - 1]

        val savedPairDistance =
            hypot((p1.x - prevP1.x).toDouble(), (p1.y - prevP1.y).toDouble()).toFloat()
        val checkedPairDistance =
            hypot((p2.x - prevP2.x).toDouble(), (p2.y - prevP2.y).toDouble()).toFloat()

        val distanceDiff = abs(savedPairDistance - checkedPairDistance)

        if (distanceDiff > positionTolerance) {
            Log.w(
                "PatternRecognizer - SecurityCheck",
                "Pairwise distance mismatch at Index $index: Distance $checkedPairDistance differs from expected $savedPairDistance"
            )
            return false
        }
        return true
    }

    /**
     * Normalizes the rotation of all points in all groups.
     *
     * @param groups The list of touch groups to be normalized.
     * @return The list of normalized touch groups.
     */
    private fun normalizeRotation(groups: List<TouchGroup>, centroid: PointF): List<TouchGroup> {
        if (groups.isEmpty() || groups.first().points.isEmpty()) return groups

        // Flatten all points to analyze as one set
        val allPoints = groups.flatMap { it.points }

        // Determine rotation logic based on the number of points
        val angle = when (allPoints.size) {
            1 -> 0.0 // No rotation for a single point
            2 -> calculateAngleBetweenPoints(
                allPoints[0],
                allPoints[1]
            ) // Simple angle between two points
            else -> calculatePrincipalAxisAngle(allPoints, centroid) // PCA for 3 or more points
        }

        // Rotate all points in all groups
        return groups.map { group ->
            TouchGroup(
                points = group.points.map { point -> rotatePoint(point, -angle, centroid) },
                timestamp = group.timestamp
            )
        }
    }

    private fun calculatePrincipalAxisAngle(points: List<PointF>, centroid: PointF): Double {
        var sumXX = 0.0
        var sumXY = 0.0
        var sumYY = 0.0

        for (point in points) {
            val dx = point.x - centroid.x
            val dy = point.y - centroid.y
            sumXX += dx * dx
            sumXY += dx * dy
            sumYY += dy * dy
        }

        // Calculate principal axis angle
        return 0.5 * atan2(2 * sumXY, sumXX - sumYY)
    }

    private fun calculateAngleBetweenPoints(p1: PointF, p2: PointF): Double {
        return atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
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
}