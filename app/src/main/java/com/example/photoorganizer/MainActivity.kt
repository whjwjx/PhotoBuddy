package com.example.photoorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.photoorganizer.ui.AppTab
import com.example.photoorganizer.ui.AppRoot
import com.example.photoorganizer.ui.theme.PhotoOrganizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTab =
            if (intent?.getBooleanExtra(EXTRA_OPEN_ORGANIZE, false) == true) {
                AppTab.ORGANIZE
            } else {
                AppTab.HOME
            }
        setContent {
            PhotoOrganizerTheme {
                AppRoot(initialTab = initialTab)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ORGANIZE = "open_organize"
    }
}
