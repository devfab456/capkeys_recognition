package com.example.tangible_recognition_v1

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /////////////////////////// Set everything up ///////////////////////////

        // Step 1: Create TouchProcessor first (dependencies injected later)
        val touchProcessor = TouchProcessor(context = this)

        // Step 2: Create PatternCheck and PatternStorage, injecting dependencies correctly
        val patternCheck = PatternCheck(touchProcessor)
        val patternStorage = PatternStorage(patternCheck, touchProcessor)

        // Step 3: Inject dependencies into TouchProcessor
        touchProcessor.initialize(patternCheck, patternStorage)

        // Step 4: Create TouchView
        val touchView = TouchView(context = this, touchProcessor = touchProcessor)

        // Load patterns from file an ID counter on startup
        patternStorage.loadPatternsFromFile(this)
        patternStorage.loadPatternIdCounter(this)

        // Delete all patterns from file
//        for (patternId in 121..121) {
//            patternStorage.deletePatternFromFile(this, patternId)
//        }

        /////////////////////////// Add buttons to the layout ///////////////////////////

        lateinit var stopSavingPatternButton: Button
        lateinit var stopCheckingButton: Button

        // add a button for saving the new pattern
        val saveNewPatternButton = Button(this).apply {
            text = "Save New Pattern"
            setOnClickListener {
                touchProcessor.saveNewPattern()
                visibility = View.GONE // Hide start button
                stopSavingPatternButton.visibility = View.VISIBLE // Show stop button
            }
        }

        // add a button for stopping the recording
        stopSavingPatternButton = Button(this).apply {
            text = "Stop Saving Pattern"
            visibility = View.GONE // initially hidden
            setOnClickListener {
                touchProcessor.stopSavingPattern()
                visibility = View.GONE // Hide stop button
                saveNewPatternButton.visibility = View.VISIBLE // Show start button
            }
        }

        // Add a button for saving all patterns to a file
        val saveAllPatternsToFileButton = Button(this).apply {
            text = "Save All Patterns Permanently"
            setOnClickListener {
                val currentPatterns = patternStorage.getCurrentPatterns()

                if (currentPatterns.isEmpty()) {
                    Log.d("PatternRecognizer - MainActivity", "No patterns to save!")
                    return@setOnClickListener
                }

                // Log current patterns
                Log.d(
                    "PatternRecognizer - MainActivity",
                    "Review the following patterns before saving:"
                )
                currentPatterns.forEach { pattern ->
                    Log.d("PatternRecognizer - MainActivity", "Pattern ID: ${pattern.id}")

                    pattern.groups.forEachIndexed { index, group ->
                        Log.d(
                            "PatternRecognizer - MainActivity",
                            "Group $index - Points: ${group.points} - Timestamp: ${group.timestamp}"
                        )
                    }
                }

                // Show confirmation dialog
                AlertDialog.Builder(context)
                    .setTitle("Confirm Save")
                    .setMessage("Do you want to permanently save these patterns?\nCheck the log for details.")
                    .setPositiveButton("Yes") { _, _ ->
                        // Save patterns permanently
                        patternStorage.savePatternsToFile(this@MainActivity)
                        Log.d("PatternRecognizer - MainActivity", "Patterns saved permanently!")
                    }
                    .setNegativeButton("No") { _, _ ->
                        Log.d("PatternRecognizer - MainActivity", "Pattern save canceled by user.")
                    }
                    .show()
            }
        }

        // add a button for resetting the current patterns
        val resetCurrentPatternsButton = Button(this).apply {
            text = "Reset Current Patterns"
            setOnClickListener {
                patternStorage.resetCurrentPatterns()
                Log.d("PatternRecognizer - MainActivity", "Reset current patterns.")
            }
        }

        // add a button for checking the pattern
        val checkPatternButton = Button(this).apply {
            text = "Check Pattern"
            setOnClickListener {
                patternStorage.loadPatternsFromFile(this@MainActivity)
                touchProcessor.checkPattern()
                visibility = View.GONE // Hide start button
                stopCheckingButton.visibility = View.VISIBLE // Show stop button
            }
        }

        stopCheckingButton = Button(this).apply {
            text = "Stop Checking"
            visibility = View.GONE // Initially hidden
            setOnClickListener {
                touchProcessor.stopCheckingPattern()
                visibility = View.GONE // Hide stop button
                checkPatternButton.visibility = View.VISIBLE // Show start button again
            }
        }

        /////////////////////////// Create the layout ///////////////////////////

        // Create a LinearLayout to hold the views
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            // Add TouchView to the layout
            addView(
                touchView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f // Weight to make it fill the remaining space
                )
            )

            addView(saveNewPatternButton)
            addView(stopSavingPatternButton)
            addView(saveAllPatternsToFileButton)
            addView(resetCurrentPatternsButton)
            addView(checkPatternButton)
            addView(stopCheckingButton)
        }

        // Set the LinearLayout as the content view
        setContentView(layout)
    }
}
