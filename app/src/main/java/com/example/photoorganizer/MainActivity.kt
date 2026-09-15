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
    private var targetQueueType by mutableStateOf<String?>(null)
    private var targetQueueRequestId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntentTarget()
        setContent {
            PhotoOrganizerTheme {
                AppRoot(
                    initialTab = targetTab,
                    initialQueueTypeName = targetQueueType,
                    initialQueueRequestId = targetQueueRequestId,
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentTarget()
    }

    private fun applyIntentTarget() {
        targetTab = tabFromIntent()
        targetQueueType = queueTypeFromIntent()
        targetQueueRequestId += 1
    }

    private fun tabFromIntent(): AppTab =
        if (intent?.getBooleanExtra(EXTRA_OPEN_ORGANIZE, false) == true) {
            AppTab.ORGANIZE
        } else {
            AppTab.HOME
        }

    private fun queueTypeFromIntent(): String? =
        intent?.getStringExtra(EXTRA_QUEUE_TYPE)?.takeIf { it.isNotBlank() }

    companion object {
        const val EXTRA_OPEN_ORGANIZE = "open_organize"
        const val EXTRA_QUEUE_TYPE = "queue_type"
    }
}
