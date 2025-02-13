package com.example.tangible_recognition_v1

import android.content.Context
import android.graphics.PointF
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.math.hypot

class PatternRecognizer(
    private val touchProcessor: TouchProcessor,
    private val positionTolerance: Float // Tolerance for matching pattern points
) {

    ////////////////// Private properties /////////////////////////////////////////////////

    private val touchSequence = mutableListOf<PointF>()
    private val sequenceTimestamps = mutableListOf<Long>()

    // Define known patterns using relative coordinates
    private val knownPatterns = mutableListOf<PatternData>()
    private val currentPatterns = mutableListOf<PatternData>()
    private var patternIdCounter = 1 // Start with ID 1, gets overwritten on load

    //    private val maxPatternTimeMs: Int = 5000
    //    private val maxTimeBetweenTouchesMs: Long = 700L


    ///////////////// Public methods ///////////////////////////////////////////////////////

    fun addTouchPoint(context: Context, point: PointF, timestamp: Long) {
        Log.d("PatternRecognizer", "Touch point added to sequence: $point")
        touchSequence.add(point)
        sequenceTimestamps.add(timestamp)

        // todo max size limit
        if (touchSequence.size >= 5) {
            Log.d("PatternRecognizer", "Touch sequence ending after 5 points.")
            if (touchProcessor.isChecking) {
                checkPattern()
            } else if (touchProcessor.isRecording) {
                saveNewPattern(context)
            }
        }
    }

    fun getCurrentPatterns(): MutableList<PatternData> {
        return currentPatterns
    }

    fun savePatternsToFile(context: Context) {
        val file = File(context.filesDir, "patterns.json")

        // Load existing patterns if the file exists
        val existingPatterns: MutableList<PatternData> = if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<PatternData>>() {}.type
                Gson().fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.e("PatternRecognizer", "Error reading existing patterns: ${e.message}")
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        // Convert currentPatterns (Pair<Int, List<PointF>>) to PatternData
        val newPatterns = currentPatterns.map { (id, points) -> PatternData(id, points) }

        // Add new patterns to the existing list
        existingPatterns.addAll(newPatterns)

        // Save the updated list back to file
        val json = Gson().toJson(existingPatterns)
        file.writeText(json)

        Log.d("PatternRecognizer", "Patterns saved: $json")
    }

    fun loadPatternsFromFile(context: Context) {
        val file = File(context.filesDir, "patterns.json")
        if (file.exists()) {
            val json = file.readText()
            try {
                val gson = Gson()
                val type = object : TypeToken<List<PatternData>>() {}.type
                val parsedData: List<PatternData> = gson.fromJson(json, type) ?: emptyList()

                knownPatterns.clear()
                knownPatterns.addAll(parsedData)

                if (knownPatterns.isEmpty()) {
                    Log.d("PatternRecognizer", "No patterns loaded. File is empty.")
                } else {
                    Log.d(
                        "PatternRecognizer",
                        "Patterns loaded successfully: ${knownPatterns.map { it.id }}"
                    )
//                    Log.d("PatternRecognizer", "Patterns loaded: $knownPatterns")
                }
            } catch (e: JsonSyntaxException) {
                Log.e("PatternRecognizer", "Invalid JSON format: ${e.message}")
            }
        }
    }

    fun loadPatternIdCounter(context: Context) {
        val sharedPreferences = context.getSharedPreferences("PatternPrefs", Context.MODE_PRIVATE)
        patternIdCounter =
            sharedPreferences.getInt("patternIdCounter", 1) // Default to 1 if not found
    }

    fun deletePatternFromFile(context: Context, patternId: Int) {
        val file = File(context.filesDir, "patterns.json")

        if (!file.exists()) {
            Log.d("PatternRecognizer", "No file found. Nothing to delete.")
            return
        }

        try {
            val json = file.readText()
            val gson = Gson()
            val type = object : TypeToken<List<PatternData>>() {}.type
            val patterns: MutableList<PatternData> = gson.fromJson(json, type) ?: mutableListOf()

            Log.d("PatternRecognizer", "Existing IDs before deletion: ${patterns.map { it.id }}")

            val originalSize = patterns.size
            patterns.removeAll { it.id == patternId }

            if (patterns.size == originalSize) {
                Log.d(
                    "PatternRecognizer",
                    "Pattern ID $patternId not found. Available IDs: ${patterns.map { it.id }}"
                )
                return
            }

            // Save the updated list back to the file using PatternData directly
            val updatedJson = gson.toJson(patterns)
            file.writeText(updatedJson)

            Log.d(
                "PatternRecognizer",
                "Deleted pattern with ID $patternId. File updated. Remaining IDs: ${patterns.map { it.id }}"
            )
        } catch (e: Exception) {
            Log.e("PatternRecognizer", "Error deleting pattern: ${e.message}")
        }
    }

    ///////////////// Private methods /////////////////////////////////////////////////

    private fun resetSequence() {
        touchSequence.clear()
        sequenceTimestamps.clear()
    }

    // todo maybe distance between single points?
    private fun normalizeSequence(): List<PointF> {
        if (touchSequence.isEmpty()) return emptyList()

        val firstPoint = touchSequence.first()
        return touchSequence.map { PointF(it.x - firstPoint.x, it.y - firstPoint.y) }
    }

    private fun saveNewPattern(context: Context) {
        val normalized = normalizeSequence()
        currentPatterns.add(PatternData(patternIdCounter++, normalized))
        savePatternIdCounter(context)
        Log.d(
            "PatternRecognizer",
            "New pattern saved with ID ${patternIdCounter - 1} and Points: $normalized"
        )
        resetSequence()
        touchProcessor.isRecording = false
        Log.d("PatternRecognizer", "Pattern recording stopped.")
        Log.d("PatternRecognizer", "Current patterns: $currentPatterns")
    }

    private fun savePatternIdCounter(context: Context) {
        val sharedPreferences = context.getSharedPreferences("PatternPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("patternIdCounter", patternIdCounter).apply()
    }

    private fun checkPattern() {
        val normalized = normalizeSequence()

        Log.d("PatternRecognizer", "Detected Pattern: $normalized")
        resetSequence()
        touchProcessor.isChecking = false

        for (pattern in knownPatterns) {
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

    private fun arePatternsEqual(
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

        // todo more sophisticated comparison - distinguish between different patterns/gestures/rotations
        for (i in savedPattern.points.indices) {
            val p1 = savedPattern.points[i]
            val p2 = patternToBeChecked[i]

            val distance = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()

            if (distance > positionTolerance) {
                Log.d(
                    "PatternRecognizer",
                    "Pattern mismatch in Pattern ${savedPattern.id} at Index $i: Distance $distance exceeds tolerance $positionTolerance"
                )
                return false
            }

            if (i != savedPattern.points.indices.first) {
                Log.d(
                    "PatternRecognizer",
                    "Pattern match in Pattern ${savedPattern.id} under the given Tolerance $distance"
                )
            }
        }

        return true
    }

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