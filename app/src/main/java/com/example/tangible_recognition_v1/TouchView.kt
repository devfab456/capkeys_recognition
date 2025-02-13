package com.example.tangible_recognition_v1

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class TouchView(context: Context) : View(context) {

    ////////////////// Private properties ///////////////////////////////////////////////////

    /** Paint object for drawing touch points */
    private val paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        textSize = 50f
    }
    private val touchColors = mutableMapOf<Int, Int>()

    /** Touch processor for handling touch events */
    private val touchProcessor = TouchProcessor(
        context, maxTouchPoints = 5, minSpacingMm = 5.1f,
        thresholdMm = 4.9f, restoreThresholdMm = 1f, 200, 100
    )

    ////////////////// Overwritten methods ///////////////////////////////////////////////////////

    /** Processes touch events and assigns a random color to each touch point */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchProcessor.processTouch(event, context) // Process touch events

        // Assign a random color to each new touch point
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (!touchColors.containsKey(pointerId)) {
                touchColors[pointerId] = getRandomColor()
            }
        }

        invalidate() // Redraw the view
        return true
    }

    /** Draws touch points on the canvas */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        touchProcessor.getTouchPoints().forEach { (pointerId, point) ->
            paint.color = touchColors[pointerId] ?: Color.RED
            canvas.drawCircle(point.x, point.y, 100f, paint)
            paint.color = Color.BLACK
            canvas.drawText("ID: $pointerId", point.x - 270, point.y - 270, paint)
        }
    }

    ////////////////// Public methods ///////////////////////////////////////////////////////

    fun getCurrentPatterns(): MutableList<PatternData> {
        return touchProcessor.getCurrentPatterns()
    }

    fun saveNewPattern() {
        touchProcessor.saveNewPattern()
    }

    fun saveAllPatternsToFile(context: Context) {
        touchProcessor.saveAllPatternsToFile(context)
    }

    fun loadPatterns(context: Context) {
        touchProcessor.loadPatterns(context)
    }

    fun loadPatternIdCounter(context: Context) {
        touchProcessor.loadPatternIdCounter(context)
    }

    fun deletePatternFromFile(context: Context, patternId: Int) {
        touchProcessor.deletePatternFromFile(context, patternId)
    }

    fun checkPattern() {
        touchProcessor.checkPattern()
    }

    ////////////////// Private methods //////////////////////////////////////////////////////

    /** Generates a random color */
    private fun getRandomColor(): Int {
        val random = Random.Default
        return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }
}