package com.example.tangible_recognition_v1

import android.graphics.PointF
import com.google.gson.annotations.SerializedName

data class PatternDataJson(
    @SerializedName("id") val id: Int = -1,  // Ensure Gson maps the correct ID
    @SerializedName("points") val points: List<PointF> = emptyList()
)

