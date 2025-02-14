package com.example.tangible_recognition_v1

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

class PatternStorage(
    private val patternCheck: PatternCheck
) {
    private lateinit var touchProcessor: TouchProcessor

    fun setTouchProcessor(processor: TouchProcessor) {
        this.touchProcessor = processor
    }

    val knownPatterns = mutableListOf<PatternData>()
    private val currentPatterns = mutableListOf<PatternData>()

    private var patternIdCounter = 1 // Start with ID 1, gets overwritten on load

    fun getCurrentPatterns(): MutableList<PatternData> {
        return currentPatterns
    }

    fun resetCurrentPatterns() {
        currentPatterns.clear()
    }

    fun saveNewPattern(context: Context) {
        if (patternCheck.isPatternTimingInvalid(null, recordNewPattern = true)) {
            Log.d("PatternRecognizer", "New Pattern rejected due to timing issues.")
            touchProcessor.resetSequence()
            touchProcessor.isRecording = false
            return
        }
        val normalized = patternCheck.normalizeSequence()
        currentPatterns.add(
            PatternData(
                patternIdCounter++,
                normalized,
                touchProcessor.sequenceTimestamps
            )
        )
        savePatternIdCounter(context)
        Log.d(
            "PatternRecognizer",
            "New pattern saved with ID ${patternIdCounter - 1} and Points: $normalized"
        )
        touchProcessor.resetSequence()
        touchProcessor.isRecording = false
        Log.d("PatternRecognizer", "Pattern recording stopped.")
        Log.d("PatternRecognizer", "Current patterns: $currentPatterns")
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
        val newPatterns =
            currentPatterns.map { (id, points, timestamps) -> PatternData(id, points, timestamps) }

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

    private fun savePatternIdCounter(context: Context) {
        val sharedPreferences = context.getSharedPreferences("PatternPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("patternIdCounter", patternIdCounter).apply()
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
}