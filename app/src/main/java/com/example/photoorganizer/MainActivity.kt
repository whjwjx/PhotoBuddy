package com.example.photoorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.photoorganizer.ui.AppTab
import com.example.photoorganizer.ui.AppRoot
import com.example.photoorganizer.ui.theme.PhotoOrganizerTheme

class MainActivity : ComponentActivity() {
    private var targetTab by mutableStateOf(AppTab.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        targetTab = tabFromIntent()
        setContent {
            PhotoOrganizerTheme {
                AppRoot(initialTab = targetTab)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetTab = tabFromIntent()
    }

    private fun tabFromIntent(): AppTab =
        if (intent?.getBooleanExtra(EXTRA_OPEN_ORGANIZE, false) == true) {
            AppTab.ORGANIZE
        } else {
            AppTab.HOME
        }

    companion object {
        const val EXTRA_OPEN_ORGANIZE = "open_organize"
    }
}
