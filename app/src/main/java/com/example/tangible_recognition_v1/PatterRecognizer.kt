package com.example.tangible_recognition_v1

import android.content.Context
import android.graphics.PointF
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

class PatternRecognizer(
    private val touchProcessor: TouchProcessor,
//    private val maxPatternTimeMs: Int = 5000, private val maxDistanceBetweenTouchPoints: Int =
//        50, private val maxTimeBetweenTouchesMs: Long = 700L
) {

    ////////////////// Private properties /////////////////////////////////////////////////

    private val touchSequence = mutableListOf<PointF>()
    private val sequenceTimestamps = mutableListOf<Long>()

    // Define known patterns using relative coordinates
    private val knownPatterns = mutableListOf<PatternData>()
    private val currentPatterns = mutableListOf<Pair<Int, List<PointF>>>()
    private var patternIdCounter = 1 // Start with ID 1


    ///////////////// Public methods ///////////////////////////////////////////////////////

    fun addTouchPoint(point: PointF, timestamp: Long) {
        Log.d("PatternRecognizer", "Touch point added to sequence: $point")
        touchSequence.add(point)
        sequenceTimestamps.add(timestamp)

        if (touchSequence.size == 5) { // Example max size limit
            Log.d("PatternRecognizer", "Touch sequence starting to save after 5 points.")
            saveNewPattern()
        }
    }

    fun getCurrentPatterns(): MutableList<Pair<Int, List<PointF>>> {
        return currentPatterns
    }

    fun savePatternsToFile(context: Context) {
        // todo add patterns instead of overwriting
        val file = File(context.filesDir, "patterns.json")
        val json = Gson().toJson(currentPatterns)
        Log.d("PatternRecognizer", "Saving patterns: $json")
        file.writeText(json)
        Log.d("PatternRecognizer", "Patterns saved to file.")
    }

    fun loadPatternsFromFile(context: Context) {
        val file = File(context.filesDir, "patterns.json")
        if (file.exists()) {
            val json = file.readText()
            try {
                val gson = Gson()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val parsedData = gson.fromJson<List<Map<String, Any>>>(json, type)

                knownPatterns.clear() // Clear existing patterns before loading
                for (item in parsedData) {
                    val id = (item["first"] as? Double)?.toInt() ?: continue
                    val pointsJson = item["second"]
                    val pointsType = object : TypeToken<List<PointF>>() {}.type
                    val points: List<PointF> = gson.fromJson(gson.toJson(pointsJson), pointsType)

                    knownPatterns.add(PatternData(id, points)) // Store ID + Points
                }

                Log.d("PatternRecognizer", "Patterns loaded successfully.")
            } catch (e: JsonSyntaxException) {
                Log.e("PatternRecognizer", "Invalid JSON format: ${e.message}")
            }
            Log.d("PatternRecognizer", "Loaded patterns: $knownPatterns")
        }
    }

//    fun checkCurrentPattern(): Boolean {
//        val currentPattern = normalizeSequence() // Normalize the current detected pattern
//
//        Log.d("PatternRecognizer", "Detected Pattern: $currentPattern")
//
//        for ((id, savedPattern) in knownPatterns) {
//            if (arePatternsSimilar(savedPattern, currentPattern)) {
//                Log.d("PatternRecognizer", "Recognized Pattern ID: $id - Points: $savedPattern")
//                return true
//            }
//        }
//
//        Log.d("PatternRecognizer", "Pattern NOT recognized!")
//        return false
//    }

    ///////////////// Private methods /////////////////////////////////////////////////

    private fun resetSequence() {
        touchSequence.clear()
        sequenceTimestamps.clear()
    }

    private fun saveNewPattern() {
        val normalized = normalizeSequence()
        currentPatterns.add(Pair(patternIdCounter++, normalized))
        Log.d(
            "PatternRecognizer",
            "New pattern saved with ID ${patternIdCounter - 1} and Points: $normalized"
        )
        resetSequence()
        touchProcessor.isRecording = false
        Log.d("PatternRecognizer", "Pattern recording stopped.")
        Log.d("PatternRecognizer", "Current patterns: $currentPatterns")
    }

    private fun normalizeSequence(): List<PointF> {
        if (touchSequence.isEmpty()) return emptyList()

        val firstPoint = touchSequence.first()
        return touchSequence.map { PointF(it.x - firstPoint.x, it.y - firstPoint.y) }
    }

//    private fun arePatternsSimilar(pattern1: List<PointF>, pattern2: List<PointF>): Boolean {
//        if (pattern1.size != pattern2.size) return false
//
//        return pattern1.zip(pattern2).all { (p1, p2) ->
//            abs(p1.x - p2.x) < 10 && abs(p1.y - p2.y) < maxDistanceBetweenTouchPoints // Adjust sensitivity
//        }
//    }

//    private fun isPatternTooSlow(): Boolean {
//        if (sequenceTimestamps.size < 2) return false
//
//        for (i in 1 until sequenceTimestamps.size) {
//            val timeGap = sequenceTimestamps[i] - sequenceTimestamps[i - 1]
//            val maxTimeBetweenTouchesMs = 700L
//            if (timeGap > maxTimeBetweenTouchesMs) {
//                Log.d(
//                    "TouchProcessor - PatternRecognizer",
//                    "Touch delay too long ($timeGap ms). Pattern reset."
//                )
//                return true
//            }
//        }
//
//        val totalDuration = sequenceTimestamps.last() - sequenceTimestamps.first()
//        return totalDuration > maxPatternTimeMs
//    }
}