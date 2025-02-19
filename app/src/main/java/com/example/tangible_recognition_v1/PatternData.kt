package com.example.tangible_recognition_v1

import android.graphics.PointF

// Represents a group of touch points that occurred within the time threshold
data class TouchGroup(
    val points: List<PointF>,
    val timestamp: Long  // Single timestamp for the group (e.g., average or first touch time)
)

// Updated PatternData to use TouchGroups
data class PatternData(
    val id: Int,
    val groups: List<TouchGroup>
)
