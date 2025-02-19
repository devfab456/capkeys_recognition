package com.example.tangible_recognition_v1

interface PatternValidator {
    fun isPatternTimingInvalidSelf(
        groups: List<TouchGroup>
    ): Boolean

    fun isPatternTimingInvalidCompare(
        savedPattern: PatternData,
        detectedTimestamps: List<Long>,
        savedTimestamps: List<Long>
    ): Boolean

    fun normalizeCoordinates(): List<TouchGroup>
}