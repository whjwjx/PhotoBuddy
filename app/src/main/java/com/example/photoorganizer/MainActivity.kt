package com.example.photoorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.photoorganizer.ui.HomeScreen
import com.example.photoorganizer.ui.theme.PhotoOrganizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoOrganizerTheme {
                HomeScreen()
            }
        }
    }
}
