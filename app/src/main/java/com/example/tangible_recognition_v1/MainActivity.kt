package com.example.tangible_recognition_v1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val touchView = TouchView(this)
        setContentView(touchView)
    }

}