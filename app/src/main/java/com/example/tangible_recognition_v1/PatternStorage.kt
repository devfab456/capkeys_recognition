package com.example.tangible_recognition_v1

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

class PatternStorage(
    private val patternValidator: PatternValidator,
    private val touchProcessor: TouchProcessor
) {

    private val knownPatterns = mutableListOf<PatternData>()
    private val currentPatterns = mutableListOf<PatternData>()

    private var patternIdCounter = 1 // Start with ID 1, gets overwritten on load

    ////////////////// Public methods ///////////////////////////////////////////////////////

    fun saveNewPattern(context: Context) {
        val normalized = patternValidator.normalizeCoordinates()

        if (patternValidator.isPatternTimingInvalidSelf(normalized)) {
            Log.d("PatternRecognizer", "New Pattern rejected due to timing issues.")
            touchProcessor.resetGroup()
            return
        }

        currentPatterns.add(
            PatternData(
                patternIdCounter++,
                normalized
            )
        )

        savePatternIdCounter(context)
        Log.d(
            "PatternRecognizer",
            "New pattern saved with ID ${patternIdCounter - 1}"
        )

        Log.i("PatternRecognizer", "Current patterns: $currentPatterns")
    }

    /**
     * Saves the current patterns to a file in JSON format.
     *
     * This method first loads any existing patterns from the file, then adds the current patterns
     * to the list, and finally saves the updated list back to the file.
     *
     * @param context The context used to access the file system.
     */
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

        // Convert currentPatterns to PatternData
        val newPatterns = currentPatterns.map { pattern ->
            PatternData(pattern.id, pattern.groups)
        }

        // Check if any new pattern ID already exists in the existing patterns
        val existingIds = existingPatterns.map { it.id }.toSet()
        val newIds = newPatterns.map { it.id }

        if (newIds.any { it in existingIds }) {
            Log.d("PatternRecognizer", "New pattern ID already exists. Save operation aborted.")
            return
        }

        // Add new patterns to the existing list
        existingPatterns.addAll(newPatterns)

        // Save the updated list back to file
        val json = Gson().toJson(existingPatterns)
        file.writeText(json)

        resetCurrentPatterns()

        Log.d("PatternRecognizer", "Patterns saved: $json")
    }

    /**
     * Loads patterns from a file in JSON format.
     *
     * This method reads the JSON data from the file and parses it into a list of PatternData objects.
     * If the file does not exist or is empty, it logs a message and returns an empty list.
     *
     * @param context The context used to access the file system.
     */
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
                }
            } catch (e: JsonSyntaxException) {
                Log.e("PatternRecognizer", "Invalid JSON format: ${e.message}")
            }
        }
    }

    /**
     * Deletes a pattern from the file based on its ID.
     *
     * This method reads the existing patterns from the file, removes the pattern with the specified
     * ID, and saves the updated list back to the file. If the pattern ID is not found, it logs a
     * message and does nothing.
     *
     * @param context The context used to access the file system.
     * @param patternId The ID of the pattern to be deleted.
     */
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

    /**
     * Loads the pattern ID counter from shared preferences.
     *
     * This method reads the pattern ID counter from shared preferences and assigns it to the
     * patternIdCounter property. If the counter is not found, it defaults to 1.
     *
     * @param context The context used to access shared preferences.
     */
    fun loadPatternIdCounter(context: Context) {
        val sharedPreferences = context.getSharedPreferences("PatternPrefs", Context.MODE_PRIVATE)
        patternIdCounter =
            sharedPreferences.getInt("patternIdCounter", 1) // Default to 1 if not found
    }

    fun getKnownPatterns(): MutableList<PatternData> {
        return knownPatterns
    }

    fun getCurrentPatterns(): MutableList<PatternData> {
        return currentPatterns
    }

    fun resetCurrentPatterns() {
        currentPatterns.clear()
    }

    ////////////////// Private methods //////////////////////////////////////////////////////

    /**
     * Saves the pattern ID counter to shared preferences.
     *
     * This method writes the patternIdCounter property to shared preferences using the key
     * "patternIdCounter".
     *
     * @param context The context used to access shared preferences.
     */
    private fun savePatternIdCounter(context: Context) {
        val sharedPreferences = context.getSharedPreferences("PatternPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("patternIdCounter", patternIdCounter).apply()
    }
}