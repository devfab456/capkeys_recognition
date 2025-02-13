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

        // Create the TouchView
        val touchView = TouchView(this)

        // Load saved patterns
        touchView.loadPatterns(this)
        // Load saved ID counter on startup todo check if id is the same before saving to file
        touchView.loadPatternIdCounter(this)

//        touchView.deletePatternFromFile(this, 2)


        /////////////////////////// Add buttons to the layout ///////////////////////////

        val saveNewPatternButton = Button(this).apply {
            text = "Save New Pattern"
            setOnClickListener {
                Log.d("MainActivity", "Saving new pattern...")
                touchView.saveNewPattern()
            }
        }

        val saveAllPatternsToFileButton = Button(this).apply {
            text = "Save All Patterns Permanently"
            setOnClickListener {
                val currentPatterns = touchView.getCurrentPatterns()

                if (currentPatterns.isEmpty()) {
                    Log.d("MainActivity", "No patterns to save!")
                    return@setOnClickListener
                }

                // Log current patterns
                Log.d("MainActivity", "Review the following patterns before saving:")
                currentPatterns.forEach { (id, pattern) ->
                    Log.d("MainActivity", "Pattern ID: $id - Points: $pattern")
                }

                // Show confirmation dialog
                AlertDialog.Builder(context)
                    .setTitle("Confirm Save")
                    .setMessage("Do you want to permanently save these patterns?\nCheck the log for details.")
                    .setPositiveButton("Yes") { _, _ ->
                        // Save patterns permanently
                        touchView.saveAllPatternsToFile(this@MainActivity)
                        Log.d("MainActivity", "Patterns saved permanently!")
                    }
                    .setNegativeButton("No") { _, _ ->
                        Log.d("MainActivity", "Pattern save canceled by user.")
                    }
                    .show()
            }
        }

        val checkPatternButton = Button(this).apply {
            text = "Check Pattern"
            setOnClickListener {
                Log.d("MainActivity", "Checking pattern...")
                //todo load the patterns again as they may have changed
                touchView.checkPattern()
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
