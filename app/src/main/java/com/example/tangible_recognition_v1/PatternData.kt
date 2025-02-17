package com.example.tangible_recognition_v1

import android.graphics.PointF

data class PatternData(val id: Int, val points: List<PointF>, val timestamps: List<Long>)