package com.devsquid.convertercamera.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.devsquid.convertercamera.ui.theme.ConverterCameraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConverterCameraTheme { AppNavHost() }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}

