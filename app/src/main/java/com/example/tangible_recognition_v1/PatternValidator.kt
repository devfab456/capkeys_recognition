package com.example.tangible_recognition_v1

import android.graphics.PointF

interface PatternValidator {
    fun isPatternTimingInvalidSelf(
        timestamps: List<Long>
    ): Boolean

    fun isPatternTimingInvalidCompare(
        savedPattern: PatternData,
        timestamps: List<Long>
    ): Boolean

    fun normalizeSequence(): List<PointF>
}