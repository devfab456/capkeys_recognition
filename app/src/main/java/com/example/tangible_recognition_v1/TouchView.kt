package com.example.tangible_recognition_v1

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class TouchView : View {

    // Reference to the touch processor
    private lateinit var touchProcessor: TouchProcessor

    // Constructor used when creating the view programmatically
    constructor(context: Context, touchProcessor: TouchProcessor) : super(context) {
        this.touchProcessor = touchProcessor
    }

    // Constructor required for XML instantiation
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    // Constructor required for XML instantiation with a style
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    ////////////////// Private properties ///////////////////////////////////////////////////

    /** Paint object for drawing touch points */
    private val paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        textSize = 50f
    }
    private val touchColors = mutableMapOf<Int, Int>()

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
            canvas.drawText("ID: $pointerId", point.x, point.y - 350, paint)
        }
    }

    ////////////////// Public methods ///////////////////////////////////////////////////////

    fun saveNewPattern() {
        touchProcessor.saveNewPattern()
    }
    ////////////////// Private methods //////////////////////////////////////////////////////

    /** Generates a random color */
    private fun getRandomColor(): Int {
        val random = Random.Default
        return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }
}