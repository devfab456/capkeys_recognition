package com.example.tangible_recognition_v1

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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


        // Load patterns from file on startup
        patternStorage.loadPatternsFromFile(this)
        // Load saved ID counter on startup todo check if id is the same before saving to file
        patternStorage.loadPatternIdCounter(this)

        // Delete all patterns from file
//        for (patternId in 78..88) {
//            patternStorage.deletePatternFromFile(this, patternId)
//        }

        /////////////////////////// Add buttons to the layout ///////////////////////////

        // add a button for saving the new pattern
        val saveNewPatternButton = Button(this).apply {
            text = "Save New Pattern"
            setOnClickListener {
                Log.d("MainActivity", "Saving new pattern...")
                touchProcessor.saveNewPattern()
            }
        }

        // add a button for saving all patterns to a file
        val saveAllPatternsToFileButton = Button(this).apply {
            text = "Save All Patterns Permanently"
            setOnClickListener {
                val currentPatterns = patternStorage.getCurrentPatterns()

                if (currentPatterns.isEmpty()) {
                    Log.d("MainActivity", "No patterns to save!")
                    return@setOnClickListener
                }

                // Log current patterns
                Log.d("MainActivity", "Review the following patterns before saving:")
                currentPatterns.forEach { (id, pattern, timestamps) ->
                    Log.d(
                        "MainActivity",
                        "Pattern ID: $id - Points: $pattern - Timestamps: $timestamps"
                    )
                }

                // Show confirmation dialog
                AlertDialog.Builder(context)
                    .setTitle("Confirm Save")
                    .setMessage("Do you want to permanently save these patterns?\nCheck the log for details.")
                    .setPositiveButton("Yes") { _, _ ->
                        // Save patterns permanently
                        patternStorage.savePatternsToFile(this@MainActivity)
                        Log.d("MainActivity", "Patterns saved permanently!")
                    }
                    .setNegativeButton("No") { _, _ ->
                        Log.d("MainActivity", "Pattern save canceled by user.")
                    }
                    .show()
            }
        }

        // add a button for resetting the current patterns
        val resetCurrentPatternsButton = Button(this).apply {
            text = "Reset Current Patterns"
            setOnClickListener {
                patternStorage.resetCurrentPatterns()
                Log.d("MainActivity", "Reset current patterns.")
            }
        }

        // add a button for checking the pattern
        val checkPatternButton = Button(this).apply {
            text = "Check Pattern"
            setOnClickListener {
                patternStorage.loadPatternsFromFile(this@MainActivity)
                Log.d("MainActivity", "Checking pattern...")
                touchProcessor.checkPattern()
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

            // Add Save Button to the layout
            addView(
                saveNewPatternButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                saveAllPatternsToFileButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                resetCurrentPatternsButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                checkPatternButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Set the LinearLayout as the content view
        setContentView(layout)
    }
}
